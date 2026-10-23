package com.callagent.host.push

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The Kyubey push relay: exchanges this phone's FCM token for an endpoint URL that nodes post to. */
internal class KyubeyClient(private val relayUrl: String) {
    fun register(token: String): String {
        val connection = (URL(relayUrl.trimEnd('/') + "/v1/register").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(JSONObject().put("token", token).toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200) throw IOException("relay HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val endpoint = JSONObject(body).optString("endpoint")
            if (!endpoint.startsWith("https://")) throw IOException("relay returned no https endpoint")
            return endpoint
        } finally {
            connection.disconnect()
        }
    }
}
