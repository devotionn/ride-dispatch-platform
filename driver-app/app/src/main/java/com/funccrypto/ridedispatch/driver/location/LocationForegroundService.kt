package com.funccrypto.ridedispatch.driver.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.funccrypto.ridedispatch.driver.BuildConfig
import com.funccrypto.ridedispatch.driver.MainActivity
import com.funccrypto.ridedispatch.driver.R
import com.funccrypto.ridedispatch.driver.auth.SessionStore
import com.funccrypto.ridedispatch.driver.network.DriverApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocationForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener
    private var pendingPollingJob: Job? = null
    private var realtimeEventsJob: Job? = null
    private val pendingPollMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationListener = LocationListener { location -> upload(location) }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification().build())
    }

    @SuppressLint("MissingPermission") // Guarded immediately below; SecurityException also stops the service safely.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MILLIS,
                LOCATION_MIN_DISTANCE_METERS,
                locationListener,
                mainLooper,
            )
        }.onFailure { stopSelf() }
        if (pendingPollingJob == null) {
            pendingPollingJob = scope.launch {
                while (isActive) {
                    pollPendingOrders()
                    delay(PENDING_POLL_INTERVAL_MILLIS)
                }
            }
        }
        startRealtimeEvents()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(locationListener) }
        pendingPollingJob?.cancel()
        realtimeEventsJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun upload(location: Location) {
        val token = SessionStore(this).token ?: return
        scope.launch {
            runCatching {
                DriverApi(BuildConfig.API_BASE_URL).updateLocation(
                    token = token,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                )
            }
        }
    }

    private suspend fun pollPendingOrders() {
        pendingPollMutex.withLock {
            val token = SessionStore(this).token ?: return
            val pending = runCatching {
                DriverApi(BuildConfig.API_BASE_URL).pendingConfirmations(token)
            }.getOrNull() ?: return
            val currentIds = pending.map { it.attemptId.toString() }.toSet()
            val notified = getSharedPreferences(NOTIFICATION_PREFERENCES, Context.MODE_PRIVATE)
                .getStringSet(KEY_NOTIFIED_ATTEMPTS, emptySet())
                .orEmpty()
                .toMutableSet()
            notified.retainAll(currentIds)
            pending.forEach { item ->
                val attemptKey = item.attemptId.toString()
                if (attemptKey !in notified && postPendingNotification(item)) {
                    notified += attemptKey
                }
            }
            getSharedPreferences(NOTIFICATION_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_NOTIFIED_ATTEMPTS, notified)
                .apply()
        }
    }

    private fun startRealtimeEvents() {
        if (realtimeEventsJob?.isActive == true) return
        realtimeEventsJob = scope.launch {
            var reconnectDelayMillis = INITIAL_RECONNECT_DELAY_MILLIS
            while (isActive) {
                val token = SessionStore(this@LocationForegroundService).token
                if (token.isNullOrBlank()) {
                    delay(PENDING_POLL_INTERVAL_MILLIS)
                    continue
                }
                var connected = false
                runCatching {
                    DriverApi(BuildConfig.API_BASE_URL).streamEvents(token) { event ->
                        if (event.name == "CONNECTED") {
                            connected = true
                        } else if (event.name == "DRIVER_NEW_DISPATCH" || event.name == "ORDER_STATUS_CHANGED") {
                            // The event is only a wake-up signal. Re-read the
                            // server state before presenting a notification.
                            scope.launch { pollPendingOrders() }
                        }
                    }
                }
                if (connected) reconnectDelayMillis = INITIAL_RECONNECT_DELAY_MILLIS
                delay(reconnectDelayMillis)
                reconnectDelayMillis = (reconnectDelayMillis * 2).coerceAtMost(MAX_RECONNECT_DELAY_MILLIS)
            }
        }
    }

    private fun postPendingNotification(item: com.funccrypto.ridedispatch.driver.domain.PendingDispatch): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val order = item.order
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            item.attemptId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlags(),
        )
        val notification = NotificationCompat.Builder(this, ORDER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("有新的待确认派单")
            .setContentText("${order.pickupAddress} → ${order.destinationAddress}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${order.pickupAddress} → ${order.destinationAddress}，请尽快打开司机工作台处理。"))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        return runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_BASE + (item.attemptId % 100_000).toInt(), notification)
            true
        }.getOrDefault(false)
    }

    private fun pendingIntentFlags(): Int = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        PendingIntent.FLAG_IMMUTABLE
    } else {
        0
    }

    private fun notification(): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("司机端定位已开启")
            .setContentText("仅在可接单状态上报当前位置")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "司机定位", NotificationManager.IMPORTANCE_LOW),
            )
            manager.createNotificationChannel(
                NotificationChannel(ORDER_CHANNEL_ID, "新派单提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                },
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "driver_location"
        private const val ORDER_CHANNEL_ID = "driver_orders"
        private const val NOTIFICATION_PREFERENCES = "driver_notifications"
        private const val KEY_NOTIFIED_ATTEMPTS = "notified_attempt_ids"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_BASE = 2000
        private const val LOCATION_INTERVAL_MILLIS = 60_000L
        private const val LOCATION_MIN_DISTANCE_METERS = 25f
        private const val PENDING_POLL_INTERVAL_MILLIS = 15_000L
        private const val INITIAL_RECONNECT_DELAY_MILLIS = 1_000L
        private const val MAX_RECONNECT_DELAY_MILLIS = 30_000L
    }
}
