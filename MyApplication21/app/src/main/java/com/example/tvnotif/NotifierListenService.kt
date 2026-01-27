package com.example.tvnotif

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException

class NotifierListenerService : NotificationListenerService() {

    private val client = OkHttpClient()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val prefs = getSharedPreferences("SELECTED_APPS", MODE_PRIVATE)
        val selectedApps = prefs.getStringSet("packages", emptySet()) ?: emptySet()

        if (selectedApps.contains(packageName)) {
            val extras = sbn.notification.extras
            
            // 1. Extract Group Chat Info
            val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
            val title = extras.getString(Notification.EXTRA_TITLE) ?: "New Message"
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            
            // For groups, EXTRA_TITLE is the Group Name. 
            // EXTRA_MESSAGING_PERSON contains the individual sender.
            val sender = if (isGroup) {
                extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: title
            } else title

            // 2. Extract Image (Check for Picture or Large Icon)
            val bitmap = extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE) 
                      ?: extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)
            
            val mediaData = if (bitmap != null) bitmapToBase64(bitmap) else null

            // 3. Build JSON
            val json = JsonObject().apply {
                addProperty("title", title)
                addProperty("message", text)
                addProperty("app", packageName)
                addProperty("is_group", isGroup)
                addProperty("sender", if (isGroup) extras.getCharSequence(Notification.EXTRA_TITLE).toString() else null)
                if (mediaData != null) addProperty("media_url", "data:image/jpeg;base64,$mediaData")
                addProperty("duration", 10)
            }

            val tvIp = getSharedPreferences("TV_REFS", MODE_PRIVATE).getString("ip", "")
            if (!tvIp.isNullOrEmpty()) {
                sendToTV(tvIp, json.toString())
            }
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream) // Compressed for speed
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    private fun sendToTV(ip: String, jsonBody: String) {
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("http://$ip:7979/notify").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PhoneNotifier", "Failed: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }
}
