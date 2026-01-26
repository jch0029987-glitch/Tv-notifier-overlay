package com.example.tvnotif
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class NotifierListenerService : NotificationListenerService() {

    private val client = OkHttpClient()
    private val prefsName = "TVNotifierPrefs"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val tvIp = prefs.getString("tvIp", "") ?: return
        if (tvIp.isEmpty()) return

        // Load selected apps dynamically
        val allowedApps = prefs.all.filter { it.value is Boolean && it.value as Boolean }.keys
        if (sbn.packageName !in allowedApps) return

        val title = sbn.notification.extras.getString("android.title") ?: "Notification"
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: ""

        sendToTV(tvIp, title, text)
    }

    private fun sendToTV(tvIp: String, title: String, message: String) {
        val json = JSONObject().apply {
            put("title", title)
            put("message", message)
            put("duration", 5)
            put("position", 0)
        }

        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("http://$tvIp:7979/notify")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}