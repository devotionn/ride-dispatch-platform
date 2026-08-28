package com.funccrypto.ridedispatch.driver.network

import com.funccrypto.ridedispatch.driver.BuildConfig
import com.funccrypto.ridedispatch.driver.domain.DriverOrder
import com.funccrypto.ridedispatch.driver.domain.DriverSession
import com.funccrypto.ridedispatch.driver.domain.DriverState
import com.funccrypto.ridedispatch.driver.domain.DriverAccount
import com.funccrypto.ridedispatch.driver.domain.DriverProfile
import com.funccrypto.ridedispatch.driver.domain.DriverQr
import com.funccrypto.ridedispatch.driver.domain.LedgerItem
import com.funccrypto.ridedispatch.driver.domain.LocationSnapshot
import com.funccrypto.ridedispatch.driver.domain.PendingDispatch
import com.funccrypto.ridedispatch.driver.domain.WorkStatus
import com.funccrypto.ridedispatch.driver.domain.toOrderStatus
import com.funccrypto.ridedispatch.driver.domain.toTripStage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DriverApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) {

    /**
     * Opens the driver-scoped SSE channel. The caller owns the coroutine and
     * should reconnect when this method returns or throws. Polling remains the
     * source-of-truth fallback when the stream is unavailable.
     */
    suspend fun streamEvents(token: String, onEvent: (DriverRealtimeEvent) -> Unit) = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/driver/events").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 0
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
        }
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion {
            connection.disconnect()
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val text = connection.errorStream?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                }.orEmpty()
                throw DriverApiException.from(status, text)
            }
            val parser = DriverSseParser()
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    parser.onLine(line)?.let(onEvent)
                }
            }
            parser.flush()?.let(onEvent)
        } finally {
            cancellationHandle?.dispose()
            connection.disconnect()
        }
    }

    suspend fun login(driverNo: String, password: String): DriverSession = withContext(Dispatchers.IO) {
        val response = request(
            method = "POST",
            path = "/api/v1/auth/driver/login",
            body = JSONObject()
                .put("username", driverNo)
                .put("password", password),
        )
        val root = JSONObject(response)
        DriverSession(
            accessToken = root.getString("accessToken"),
            expiresAt = root.getString("expiresAt"),
            authority = root.getString("authority"),
        )
    }

    suspend fun logout(token: String) = withContext(Dispatchers.IO) {
        runCatching {
            request("POST", "/api/v1/auth/logout", token = token)
        }
    }

    suspend fun state(token: String): DriverState = withContext(Dispatchers.IO) {
        DriverState.from(
            JSONObject(request("GET", "/api/v1/driver/me/state", token = token)),
        )
    }

    suspend fun profile(token: String): DriverProfile = withContext(Dispatchers.IO) {
        val root = JSONObject(request("GET", "/api/v1/driver/me/profile", token = token))
        DriverProfile(root.getString("driverNo"), root.getString("name"), root.getString("mobile"),
            root.optString("plateNo").ifBlank { null }, root.optString("brandModel").ifBlank { null })
    }

    suspend fun qr(token: String): DriverQr = withContext(Dispatchers.IO) {
        val root = JSONObject(request("GET", "/api/v1/driver/me/qr", token = token))
        DriverQr(root.getString("shortCode"), root.getString("path"), root.optString("imageDataUrl").ifBlank { null })
    }

    suspend fun updateWorkStatus(token: String, status: WorkStatus): DriverState = withContext(Dispatchers.IO) {
        DriverState.from(
            JSONObject(
                request(
                    method = "PUT",
                    path = "/api/v1/driver/me/work-status",
                    token = token,
                    body = JSONObject().put("workStatus", status.name),
                ),
            ),
        )
    }

    suspend fun updateAvailablePassengers(token: String, count: Int): DriverState = withContext(Dispatchers.IO) {
        DriverState.from(
            JSONObject(
                request(
                    method = "PUT",
                    path = "/api/v1/driver/me/available-passengers",
                    token = token,
                    body = JSONObject().put("availablePassengers", count),
                ),
            ),
        )
    }

    suspend fun updateLocation(
        token: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        locatedAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    ): LocationSnapshot = withContext(Dispatchers.IO) {
        val root = JSONObject(
            request(
                method = "POST",
                path = "/api/v1/driver/me/location",
                token = token,
                body = JSONObject()
                    .put("latitude", latitude)
                    .put("longitude", longitude)
                    .put("accuracyMeters", accuracyMeters)
                    .put("locatedAt", locatedAt.toString())
                    .put("source", "DRIVER_APP"),
            ),
        )
        LocationSnapshot(
            latitude = root.getDouble("latitude"),
            longitude = root.getDouble("longitude"),
            locatedAt = root.getString("locatedAt"),
        )
    }

    suspend fun pendingConfirmations(token: String): List<PendingDispatch> = withContext(Dispatchers.IO) {
        val array = JSONArray(request("GET", "/api/v1/driver/orders/pending-confirmation", token = token))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    PendingDispatch(
                        attemptId = item.getLong("attemptId"),
                        dispatchedAt = item.getString("dispatchedAt"),
                        order = DriverOrder.from(item.getJSONObject("order")),
                    ),
                )
            }
        }
    }

    suspend fun activeOrders(token: String): List<DriverOrder> = withContext(Dispatchers.IO) {
        val array = JSONArray(request("GET", "/api/v1/driver/orders/active", token = token))
        buildList {
            for (index in 0 until array.length()) add(DriverOrder.from(array.getJSONObject(index)))
        }
    }

    suspend fun historyOrders(token: String): List<DriverOrder> = withContext(Dispatchers.IO) {
        val array = JSONArray(request("GET", "/api/v1/driver/orders/history", token = token))
        buildList { for (index in 0 until array.length()) add(DriverOrder.from(array.getJSONObject(index))) }
    }

    suspend fun accept(token: String, attemptId: Long): String = withContext(Dispatchers.IO) {
        JSONObject(request("POST", "/api/v1/driver/dispatch-attempts/$attemptId/accept", token = token))
            .getString("status")
    }

    suspend fun reject(token: String, attemptId: Long, reasonText: String): String = withContext(Dispatchers.IO) {
        JSONObject(
            request(
                method = "POST",
                path = "/api/v1/driver/dispatch-attempts/$attemptId/reject",
                token = token,
                body = JSONObject().put("reasonText", reasonText),
            ),
        ).getString("status")
    }

    suspend fun progress(token: String, orderNo: String, stage: String) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = "/api/v1/driver/orders/${orderNo.encodePathSegment()}/progress",
            token = token,
            body = JSONObject().put("stage", stage),
        )
    }

    suspend fun submitFinalAmount(token: String, orderNo: String, amount: Long) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = "/api/v1/driver/orders/${orderNo.encodePathSegment()}/final-amount",
            token = token,
            body = JSONObject().put("amount", amount),
        )
    }

    suspend fun confirmOfflinePayment(token: String, orderNo: String) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = "/api/v1/driver/orders/${orderNo.encodePathSegment()}/offline-payment/confirm",
            token = token,
            body = JSONObject().put("confirmation", "CONFIRM"),
        )
    }

    suspend fun account(token: String): DriverAccount = withContext(Dispatchers.IO) {
        val root = JSONObject(request("GET", "/api/v1/driver/me/account", token = token))
        DriverAccount(
            businessIncome = root.getLong("businessIncome"),
            availableBalance = root.getLong("availableBalance"),
            frozenBalance = root.getLong("frozenBalance"),
        )
    }

    suspend fun ledger(token: String): List<LedgerItem> = withContext(Dispatchers.IO) {
        val array = JSONArray(request("GET", "/api/v1/driver/me/ledger", token = token))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(LedgerItem(item.getString("ledgerType"), item.getLong("amount"), item.getString("createdAt")))
            }
        }
    }

    suspend fun requestWithdrawal(token: String, amountFen: Long, channel: String, account: String) = withContext(Dispatchers.IO) {
        request(
            method = "POST",
            path = "/api/v1/driver/me/withdrawals",
            token = token,
            headers = mapOf("Idempotency-Key" to "withdrawal-" + UUID.randomUUID().toString()),
            body = JSONObject().put("amount", amountFen).put("channel", channel).put("account", account),
        )
    }

    private fun request(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val maxAttempts = if (method == "GET" || method == "HEAD") MAX_READ_ATTEMPTS else 1
        var attempt = 1
        while (true) {
            try {
                return requestOnce(method, path, token, body, headers)
            } catch (error: IOException) {
                if (attempt >= maxAttempts) throw error
                Thread.sleep(RETRY_DELAY_MILLIS * attempt)
                attempt++
            }
        }
    }

    private fun requestOnce(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        return try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(body.toString()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText() }.orEmpty()
            if (status !in 200..299) throw DriverApiException.from(status, text)
            text
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_READ_ATTEMPTS = 3
        const val RETRY_DELAY_MILLIS = 400L
    }
}

data class DriverRealtimeEvent(
    val name: String,
    val data: String,
)

/** Small SSE frame parser kept independent from the Android service for unit testing. */
internal class DriverSseParser {
    private var eventName = "message"
    private val data = StringBuilder()

    fun onLine(line: String): DriverRealtimeEvent? {
        if (line.isEmpty()) return flush()
        if (line.startsWith(":")) return null
        when {
            line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").trimStart())
            }
        }
        return null
    }

    fun flush(): DriverRealtimeEvent? {
        if (data.isEmpty() && eventName == "message") return null
        val result = DriverRealtimeEvent(eventName, data.toString())
        eventName = "message"
        data.clear()
        return result
    }
}

class DriverApiException(
    val statusCode: Int,
    val code: String,
    override val message: String,
) : RuntimeException(message) {
    companion object {
        fun from(statusCode: Int, rawBody: String): DriverApiException {
            val root = runCatching { JSONObject(rawBody) }.getOrNull()
            return DriverApiException(
                statusCode = statusCode,
                code = root?.optString("code").orEmpty().ifBlank { "HTTP_$statusCode" },
                message = root?.optString("message").orEmpty().ifBlank { "请求失败（HTTP $statusCode）" },
            )
        }
    }
}

private fun JSONObject.stringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null

private fun JSONObject.longOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) getLong(name) else null

private fun DriverState.Companion.from(root: JSONObject): DriverState = DriverState(
    driverId = root.getLong("driverId"),
    workStatus = WorkStatus.valueOf(root.getString("workStatus")),
    availablePassengers = root.getInt("availablePassengers"),
    maxPassengers = root.getInt("maxPassengers"),
)

private fun DriverOrder.Companion.from(root: JSONObject): DriverOrder = DriverOrder(
    orderNo = root.getString("orderNo"),
    status = root.stringOrNull("status").toOrderStatus(),
    tripStage = root.stringOrNull("tripStage").toTripStage(),
    passengerMobile = root.getString("passengerMobile"),
    pickupAddress = root.getString("pickupAddress"),
    destinationAddress = root.getString("destinationAddress"),
    passengerCount = root.getInt("passengerCount"),
    departureAt = root.getString("departureAt"),
    remark = root.stringOrNull("remark"),
    finalAmount = root.longOrNull("finalAmount"),
)

private fun String.encodePathSegment(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
