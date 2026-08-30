package com.funccrypto.ridedispatch.driver

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.lightColorScheme
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import com.funccrypto.ridedispatch.driver.auth.SessionStore
import com.funccrypto.ridedispatch.driver.domain.DriverOrder
import com.funccrypto.ridedispatch.driver.domain.DriverAccount
import com.funccrypto.ridedispatch.driver.domain.DriverProfile
import com.funccrypto.ridedispatch.driver.domain.DriverQr
import com.funccrypto.ridedispatch.driver.domain.LedgerItem
import com.funccrypto.ridedispatch.driver.domain.DriverState
import com.funccrypto.ridedispatch.driver.domain.PendingDispatch
import com.funccrypto.ridedispatch.driver.domain.TripStage
import com.funccrypto.ridedispatch.driver.domain.WorkStatus
import com.funccrypto.ridedispatch.driver.domain.canSubmitFinalAmount
import com.funccrypto.ridedispatch.driver.domain.formatFenAsYuan
import com.funccrypto.ridedispatch.driver.domain.nextTripStage
import com.funccrypto.ridedispatch.driver.domain.parseYuanToFen
import com.funccrypto.ridedispatch.driver.location.LocationForegroundService
import com.funccrypto.ridedispatch.driver.network.DriverApi
import com.funccrypto.ridedispatch.driver.network.DriverApiException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val DriverBlue = Color(0xFF2F67D8)
private val DriverBlueDark = Color(0xFF173B77)
private val DriverBackground = Color(0xFFF4F7FC)
private val DriverBorder = Color(0xFFE0E8F4)
private val DriverMuted = Color(0xFF667085)
private val DriverGreen = Color(0xFF0F9D72)
private val DriverAmber = Color(0xFFB66A00)
private val DriverRed = Color(0xFFB42318)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DriverApp() }
    }
}

@Composable
private fun DriverApp() {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    val api = remember { DriverApi() }
    var token by remember { mutableStateOf(store.token) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DriverBlue,
            onPrimary = Color.White,
            secondary = DriverBlueDark,
            background = DriverBackground,
            surface = Color.White,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = DriverBackground) {
            if (token.isNullOrBlank()) {
                LoginScreen(
                    api = api,
                    onLoggedIn = { session ->
                        store.save(session)
                        token = session.accessToken
                    },
                )
            } else {
                DriverHomeScreen(
                    api = api,
                    token = token.orEmpty(),
                    onLogout = {
                        store.clear()
                        token = null
                    },
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(
    api: DriverApi,
    onLoggedIn: (com.funccrypto.ridedispatch.driver.domain.DriverSession) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var driverNo by rememberSaveable { mutableStateOf("D101") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DriverBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("DRIVER OPERATIONS", style = MaterialTheme.typography.labelLarge, color = DriverBlue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("司机工作台", style = MaterialTheme.typography.headlineLarge, color = DriverBlueDark, fontWeight = FontWeight.Bold)
        Text("接单、履约与收款状态，一屏掌握", color = DriverMuted)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, DriverBorder),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("登录账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = driverNo,
                    onValueChange = { driverNo = it },
                    label = { Text("司机编号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = DriverRed, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            runCatching { api.login(driverNo.trim(), password) }
                                .onSuccess(onLoggedIn)
                                .onFailure { error = it.userMessage() }
                            busy = false
                        }
                    },
                    enabled = !busy && driverNo.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DriverBlue),
                ) {
                    Text(if (busy) "登录中…" else "进入工作台", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DriverHomeScreen(
    api: DriverApi,
    token: String,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var driverState by remember { mutableStateOf<DriverState?>(null) }
    var pending by remember { mutableStateOf<List<PendingDispatch>>(emptyList()) }
    var active by remember { mutableStateOf<List<DriverOrder>>(emptyList()) }
    var account by remember { mutableStateOf<DriverAccount?>(null) }
    var profile by remember { mutableStateOf<DriverProfile?>(null) }
    var qr by remember { mutableStateOf<DriverQr?>(null) }
    var history by remember { mutableStateOf<List<DriverOrder>>(emptyList()) }
    var ledger by remember { mutableStateOf<List<LedgerItem>>(emptyList()) }
    var availablePassengers by rememberSaveable { mutableStateOf("") }
    var withdrawalAmount by rememberSaveable { mutableStateOf("") }
    var withdrawalAccount by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var requestAvailableAfterPermission by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    suspend fun load() {
        driverState = api.state(token)
        availablePassengers = driverState?.availablePassengers?.toString().orEmpty()
        pending = api.pendingConfirmations(token)
        active = api.activeOrders(token)
        account = api.account(token)
        profile = api.profile(token)
        qr = api.qr(token)
        history = api.historyOrders(token)
        ledger = api.ledger(token)
    }

    fun refresh() {
        scope.launch {
            runCatching { load() }
                .onFailure { error = it.userMessage() }
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            runCatching { action() }
                .onFailure { error = it.userMessage() }
            runCatching { load() }
                .onFailure { error = it.userMessage() }
            busy = false
        }
    }

    fun stopLocationService() {
        context.stopService(Intent(context, LocationForegroundService::class.java))
    }

    fun updateWorkStatus(status: WorkStatus) {
        runAction {
            driverState = api.updateWorkStatus(token, status)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (requestAvailableAfterPermission) {
            requestAvailableAfterPermission = false
            if (granted) updateWorkStatus(WorkStatus.AVAILABLE)
            else error = "未授予定位权限，不能开始接单"
        }
    }

    fun requestAvailable() {
        if (hasLocationPermission(context)) {
            updateWorkStatus(WorkStatus.AVAILABLE)
        } else {
            requestAvailableAfterPermission = true
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(token) {
        while (isActive) {
            runCatching { load() }
                .onFailure { error = it.userMessage() }
            delay(15_000)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(driverState?.workStatus) {
        when (driverState?.workStatus) {
            WorkStatus.AVAILABLE -> if (hasLocationPermission(context)) {
                startForegroundService(context, Intent(context, LocationForegroundService::class.java))
            }
            WorkStatus.PAUSED, WorkStatus.OFFLINE -> stopLocationService()
            null -> Unit
        }
    }

    val serviceOrders = active.filter { it.status != com.funccrypto.ridedispatch.driver.domain.OrderStatus.PENDING_PAYMENT }
    val pendingPaymentOrders = active.filter { it.status == com.funccrypto.ridedispatch.driver.domain.OrderStatus.PENDING_PAYMENT }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = DriverBlueDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 12.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("司机工作台", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("服务端状态实时同步", color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            api.logout(token)
                            onLogout()
                            stopLocationService()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("退出") }
            }
        }

        profile?.let { ProfileCard(it, qr) }

        error?.let {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4F2)),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("同步提醒", color = DriverRed, fontWeight = FontWeight.Bold)
                    Text(it, color = DriverRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, DriverBorder),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("工作状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("可接人数 ${driverState?.availablePassengers ?: "-"} / ${driverState?.maxPassengers ?: "-"}", color = DriverMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    StatusPill(
                        text = driverState?.workStatus?.label() ?: "读取中",
                        color = driverState?.workStatus?.statusColor() ?: DriverMuted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (driverState?.workStatus == WorkStatus.AVAILABLE) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { updateWorkStatus(WorkStatus.PAUSED) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("暂停接单")
                        }
                    } else {
                        Button(
                            enabled = !busy,
                            onClick = ::requestAvailable,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("开始接单", fontWeight = FontWeight.Bold) }
                    }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = { updateWorkStatus(WorkStatus.OFFLINE) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("下线")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = availablePassengers,
                        onValueChange = { availablePassengers = it.filter(Char::isDigit).take(2) },
                        label = { Text("可接人数") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = !busy && availablePassengers.toIntOrNull() != null,
                        onClick = {
                            runAction {
                                driverState = api.updateAvailablePassengers(
                                    token,
                                    availablePassengers.toInt(),
                                )
                            }
                        },
                        modifier = Modifier.alignByBaseline(),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("保存") }
                }
            }
        }

        SectionTitle("待确认订单", pending.size)
        if (pending.isEmpty()) {
            Text("暂无待确认订单", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            pending.forEach { item ->
                PendingCard(
                    item = item,
                    enabled = !busy,
                    onAccept = { runAction { api.accept(token, item.attemptId) } },
                    onReject = {
                        runAction {
                            api.reject(token, item.attemptId, "当前无法接单")
                        }
                    },
                )
            }
        }

        SectionTitle("履约中订单", serviceOrders.size)
        if (serviceOrders.isEmpty()) {
            Text("暂无履约中订单", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            serviceOrders.forEach { order ->
                ActiveOrderCard(
                    order = order,
                    enabled = !busy,
                    onProgress = { stage -> runAction { api.progress(token, order.orderNo, stage.name) } },
                    onFinalAmount = { amount ->
                        runAction { api.submitFinalAmount(token, order.orderNo, amount) }
                    },
                )
            }
        }

        account?.let { snapshot ->
            AccountCard(
                account = snapshot,
                withdrawalAmount = withdrawalAmount,
                onWithdrawalAmountChange = { withdrawalAmount = it },
                withdrawalAccount = withdrawalAccount,
                onWithdrawalAccountChange = { withdrawalAccount = it },
                enabled = !busy,
                onWithdraw = {
                    parseYuanToFen(withdrawalAmount)?.let { amountFen ->
                        runAction {
                            api.requestWithdrawal(token, amountFen, "BANK", withdrawalAccount.trim())
                            withdrawalAmount = ""
                        }
                    }
                },
            )
        }

        LedgerCard(ledger)
        HistoryCard(history)

        SectionTitle("待收款订单", pendingPaymentOrders.size)
        if (pendingPaymentOrders.isEmpty()) {
            Text("暂无待收款订单", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            pendingPaymentOrders.forEach { order ->
                PendingPaymentCard(
                    order = order,
                    enabled = !busy,
                    onOfflineConfirm = { runAction { api.confirmOfflinePayment(token, order.orderNo) } },
                )
            }
        }

        OutlinedButton(enabled = !busy, onClick = ::refresh, modifier = Modifier.fillMaxWidth()) {
            Text("刷新服务端状态")
        }
    }
}

@Composable
private fun ProfileCard(profile: DriverProfile, qr: DriverQr?) {
    OrderCard {
        OrderHeader("司机资料", "账号与车辆", DriverBlue)
        Text("${profile.name} · ${profile.driverNo}", color = DriverBlueDark, fontWeight = FontWeight.Bold)
        Text("${profile.mobile} · ${profile.plateNo ?: "未绑定车牌"} · ${profile.brandModel ?: "车型未填写"}", color = DriverMuted, style = MaterialTheme.typography.bodySmall)
        qr?.let {
            Surface(color = Color(0xFFF1F5FF), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("司机专属二维码短码", color = DriverMuted, style = MaterialTheme.typography.labelSmall)
                    Text(it.shortCode, color = DriverBlue, fontWeight = FontWeight.Bold)
                    it.imageDataUrl?.let { dataUrl ->
                        QrImage(dataUrl)
                    }
                    Text("下单链接：${it.path}", color = DriverMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun QrImage(dataUrl: String) {
    val bitmap = remember(dataUrl) {
        runCatching {
            val encoded = dataUrl.substringAfter(',', missingDelimiterValue = dataUrl)
            BitmapFactory.decodeByteArray(Base64.decode(encoded, Base64.DEFAULT), 0, Base64.decode(encoded, Base64.DEFAULT).size)
        }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "司机专属下单二维码",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(220.dp),
        )
    }
}

@Composable
private fun LedgerCard(items: List<LedgerItem>) {
    OrderCard {
        OrderHeader("最近账本", "收入与提现流水", DriverBlue)
        if (items.isEmpty()) Text("暂无账本记录", color = DriverMuted)
        items.take(5).forEach { item ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.ledgerType, modifier = Modifier.weight(1f), color = DriverMuted, style = MaterialTheme.typography.bodySmall)
                Text("${if (item.amount >= 0) "+" else ""}¥${formatFenAsYuan(kotlin.math.abs(item.amount))}", color = if (item.amount >= 0) DriverGreen else DriverRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HistoryCard(items: List<DriverOrder>) {
    OrderCard {
        OrderHeader("历史订单", "最近完成 / 取消 / 异常", DriverBlue)
        if (items.isEmpty()) Text("暂无历史订单", color = DriverMuted)
        items.take(5).forEach { order ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.orderNo, color = DriverBlueDark, fontWeight = FontWeight.SemiBold)
                    Text("${order.pickupAddress} → ${order.destinationAddress}", color = DriverMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Text(order.status.name, color = if (order.status == com.funccrypto.ridedispatch.driver.domain.OrderStatus.COMPLETED) DriverGreen else DriverMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: DriverAccount,
    withdrawalAmount: String,
    onWithdrawalAmountChange: (String) -> Unit,
    withdrawalAccount: String,
    onWithdrawalAccountChange: (String) -> Unit,
    enabled: Boolean,
    onWithdraw: () -> Unit,
) {
    OrderCard {
        OrderHeader("收入与结算", "服务端账本", DriverBlue)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BalanceChip("业务收入", account.businessIncome, DriverBlueDark)
            BalanceChip("可提现", account.availableBalance, DriverGreen)
            BalanceChip("冻结", account.frozenBalance, DriverAmber)
        }
        OutlinedTextField(
            value = withdrawalAmount,
            onValueChange = { onWithdrawalAmountChange(it.filter { character -> character.isDigit() || character == '.' }) },
            label = { Text("提现金额（元）") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = withdrawalAccount,
            onValueChange = onWithdrawalAccountChange,
            label = { Text("银行卡号") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        val amountFen = parseYuanToFen(withdrawalAmount)
        Button(
            enabled = enabled && amountFen != null && withdrawalAccount.isNotBlank(),
            onClick = onWithdraw,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text("提交提现申请") }
    }
}

@Composable
private fun RowScope.BalanceChip(label: String, fen: Long, color: Color) {
    Surface(modifier = Modifier.weight(1f), color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = DriverMuted, style = MaterialTheme.typography.labelSmall)
            Text("¥" + formatFenAsYuan(fen), color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PendingPaymentCard(order: DriverOrder, enabled: Boolean, onOfflineConfirm: () -> Unit) {
    var confirmVisible by rememberSaveable(order.orderNo) { mutableStateOf(false) }
    OrderCard {
        OrderHeader(order.orderNo, "待收款", DriverGreen)
        Text("${order.pickupAddress} → ${order.destinationAddress}", color = DriverBlueDark, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFEAF3FF),
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("应收金额", color = DriverMuted, style = MaterialTheme.typography.labelMedium)
                    Text("已到目的地，等待付款", color = DriverBlueDark, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    order.finalAmount?.let(::formatFenAsYuan)?.let { "¥$it" } ?: "—",
                    color = DriverBlueDark,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Button(
            enabled = enabled,
            onClick = { confirmVisible = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text("线下已收款，完成订单") }
    }
    if (confirmVisible) {
        AlertDialog(
            onDismissRequest = { confirmVisible = false },
            title = { Text("确认线下收款") },
            text = { Text("请确认你已经收到乘客的 ¥${order.finalAmount?.let(::formatFenAsYuan) ?: "0.00"}，确认后订单将完成。") },
            confirmButton = {
                Button(onClick = { confirmVisible = false; onOfflineConfirm() }) { Text("确认已收款") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmVisible = false }) { Text("暂不确认") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        StatusPill(count.toString(), DriverBlue)
    }
    HorizontalDivider(color = DriverBorder)
}

@Composable
private fun PendingCard(
    item: PendingDispatch,
    enabled: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    OrderCard {
        OrderHeader(item.order.orderNo, "待确认", DriverAmber)
        Text("${item.order.pickupAddress} → ${item.order.destinationAddress}", color = DriverBlueDark, fontWeight = FontWeight.SemiBold)
        Text("乘客 ${item.order.passengerCount} 人 · ${item.order.passengerMobile}", color = DriverMuted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = enabled,
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text("接受", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                enabled = enabled,
                onClick = onReject,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) { Text("拒绝") }
        }
    }
}

@Composable
private fun ActiveOrderCard(
    order: DriverOrder,
    enabled: Boolean,
    onProgress: (TripStage) -> Unit,
    onFinalAmount: (Long) -> Unit,
) {
    var amount by rememberSaveable(order.orderNo) { mutableStateOf("") }
    val nextStage = nextTripStage(order.tripStage)
    val context = LocalContext.current
    val destinationNavigation = order.tripStage == TripStage.PASSENGER_ONBOARD ||
        order.tripStage == TripStage.IN_TRANSIT || order.tripStage == TripStage.ARRIVED_DESTINATION
    val navigationAddress = if (destinationNavigation) order.destinationAddress else order.pickupAddress
    val navigationLatitude = if (destinationNavigation) order.destinationLatitude else order.pickupLatitude
    val navigationLongitude = if (destinationNavigation) order.destinationLongitude else order.pickupLongitude

    OrderCard {
        OrderHeader(order.orderNo, order.status.label(), DriverBlue)
        Text("${order.pickupAddress} → ${order.destinationAddress}", color = DriverBlueDark, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = DriverBackground,
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(DriverBlue, RoundedCornerShape(50)))
                Spacer(Modifier.size(8.dp))
                Column {
                    Text("当前履约阶段", color = DriverMuted, style = MaterialTheme.typography.labelMedium)
                    Text(order.tripStage?.label() ?: "尚未到达出发地", color = DriverBlueDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        OutlinedButton(
            enabled = enabled,
            onClick = { openExternalNavigation(context, navigationAddress, navigationLatitude, navigationLongitude) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text(if (destinationNavigation) "导航到目的地" else "导航到上车点") }
        if (nextStage != null) {
            Button(
                enabled = enabled,
                onClick = { onProgress(nextStage) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("推进：${nextStage.label()}")
            }
        }
        if (order.canSubmitFinalAmount()) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { character -> character.isDigit() || character == '.' } },
                label = { Text("最终金额（元）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val amountFen = parseYuanToFen(amount)
            Button(
                enabled = enabled && amountFen != null,
                onClick = { amountFen?.let(onFinalAmount) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("提交最终金额") }
        }
    }
}

private fun openExternalNavigation(context: Context, address: String, latitude: Double?, longitude: Double?) {
    val query = if (latitude != null && longitude != null) {
        "$latitude,$longitude(${Uri.encode(address)})"
    } else {
        Uri.encode(address)
    }
    val uri = if (latitude != null && longitude != null) "geo:$latitude,$longitude?q=$query" else "geo:0,0?q=$query"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(intent, "选择地图导航应用"))
        return
    }
    context.getSystemService(android.content.ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("导航地址", address))
    Toast.makeText(context, "未检测到可用的地图导航应用，地址已复制，可手动导航。", Toast.LENGTH_LONG).show()
}

@Composable
private fun OrderCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DriverBorder),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun OrderHeader(orderNo: String, status: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            orderNo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        StatusPill(status, color)
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.12f), contentColor = color, shape = RoundedCornerShape(50)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun WorkStatus.label(): String = when (this) {
    WorkStatus.AVAILABLE -> "可接单"
    WorkStatus.PAUSED -> "暂停接单"
    WorkStatus.OFFLINE -> "离线"
}

private fun WorkStatus.statusColor(): Color = when (this) {
    WorkStatus.AVAILABLE -> DriverGreen
    WorkStatus.PAUSED -> DriverAmber
    WorkStatus.OFFLINE -> DriverMuted
}

private fun com.funccrypto.ridedispatch.driver.domain.OrderStatus.label(): String = when (this) {
    com.funccrypto.ridedispatch.driver.domain.OrderStatus.PENDING_DISPATCH -> "待派单"
    com.funccrypto.ridedispatch.driver.domain.OrderStatus.PENDING_DRIVER_CONFIRM -> "待确认"
    com.funccrypto.ridedispatch.driver.domain.OrderStatus.ACCEPTED -> "已接单"
    com.funccrypto.ridedispatch.driver.domain.OrderStatus.IN_SERVICE -> "服务中"
    com.funccrypto.ridedispatch.driver.domain.OrderStatus.PENDING_PAYMENT -> "待收款"
    com.funccrypto.ridedispatch.driver.domain.OrderStatus.COMPLETED -> "已完成"
    com.funccrypto.ridedispatch.driver.domain.OrderStatus.CANCELLED -> "已取消"
    com.funccrypto.ridedispatch.driver.domain.OrderStatus.UNKNOWN -> "未知"
}

private fun TripStage.label(): String = when (this) {
    TripStage.ARRIVED_PICKUP -> "已到出发地"
    TripStage.PASSENGER_ONBOARD -> "已接到乘客"
    TripStage.IN_TRANSIT -> "行程中"
    TripStage.ARRIVED_DESTINATION -> "已到目的地"
}

private fun Throwable.userMessage(): String = when (this) {
    is DriverApiException -> "$message（$code）"
    else -> message ?: "请求失败，请检查网络后重试"
}
