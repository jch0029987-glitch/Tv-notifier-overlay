package com.example.tvnotif

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class NotifierListenerService : NotificationListenerService() {

    private val client = OkHttpClient()
    private val prefsName = "TVNotifierPrefs"

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val tvIp = prefs.getString("tvIp", "") ?: return
        if (tvIp.isEmpty()) return

        // --- FILTERING ---
        // If you want to test EVERY notification, comment out the next 2 lines
        val isAllowed = prefs.getBoolean(sbn.packageName, false)
        if (!isAllowed) return 

        val title = sbn.notification.extras.getString("android.title") ?: "New Alert"
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: ""

        sendToTV(tvIp, title, text, sbn.packageName)
    }

    private fun sendToTV(tvIp: String, title: String, message: String, appPkg: String) {
        val json = JSONObject().apply {
            put("title", title)
            put("message", message)
            put("app", appPkg)
            put("duration", 8)
        }

        val formBody = FormBody.Builder()
            .add("postData", json.toString())
            .build()

        val request = Request.Builder()
            .url("http://$tvIp:7979/notify")
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }
}
