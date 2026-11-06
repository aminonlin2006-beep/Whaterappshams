package com.amin.weather.core

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(
    val code: Int,
    val body: ByteArray?,
    val error: String?
) {
    val isSuccess: Boolean get() = code in 200..299 && body != null
    val bodyString: String? get() = body?.let { String(it, Charsets.UTF_8) }
}

object Http {
    fun get(urlStr: String, timeoutMs: Int = Tuning.HTTP_TIMEOUT_MS): HttpResponse {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "AminWeather/5.0 (Android)")
            
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val inputStream = conn.inputStream
                val buffer = ByteArray(8192)
                val bos = ByteArrayOutputStream()
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    bos.write(buffer, 0, bytesRead)
                }
                HttpResponse(responseCode, bos.toByteArray(), null)
            } else if (responseCode == 429) {
                HttpResponse(429, null, "Rate limit exceeded (HTTP 429)")
            } else {
                val errStream = conn.errorStream
                val errText = errStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                HttpResponse(responseCode, null, errText)
            }
        } catch (e: Exception) {
            HttpResponse(-1, null, e.message ?: e.toString())
        } finally {
            conn?.disconnect()
        }
    }
}
