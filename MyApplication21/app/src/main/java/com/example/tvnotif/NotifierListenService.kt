package com.example.tvnotif

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class NotifierListenerService : NotificationListenerService() {

    private val client = OkHttpClient()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val prefs = getSharedPreferences("SELECTED_APPS", MODE_PRIVATE)
        val selectedApps = prefs.getStringSet("packages", emptySet()) ?: emptySet()

        // Only send if the user selected this app
        if (selectedApps.contains(packageName)) {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: "New Notification"
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            
            val tvIp = getSharedPreferences("TV_REFS", MODE_PRIVATE).getString("ip", "")
            if (!tvIp.isNullOrEmpty()) {
                sendToTV(tvIp, title, text, packageName)
            }
        }
    }

    private fun sendToTV(ip: String, title: String, message: String, appName: String) {
        val json = """
            {
                "title": "$title",
                "message": "$message",
                "app": "$appName"
            }
        """.trimIndent()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("http://$ip:7979/notify").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NotifierService", "Failed to send: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
