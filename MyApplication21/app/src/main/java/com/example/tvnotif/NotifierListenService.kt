package com.example.tvnotif

import android.app.Notification
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class NotifierListenerService : NotificationListenerService() {

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            val extras = sbn.notification.extras ?: return

            // ---- SAFELY EXTRACT TEXT ----
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
            val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

            // ---- RELIABLE GROUP DETECTION ----
            val isGroup =
                extras.containsKey(Notification.EXTRA_CONVERSATION_TITLE) &&
                extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) != null

            val sender = if (isGroup) title else null

            // ---- SAFE BITMAP EXTRACTION ----
            val bitmap = try {
                extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
                    ?: extras.getParcelable(Notification.EXTRA_LARGE_ICON)
            } catch (e: Exception) {
                null
            }

            // ---- OFFLOAD HEAVY WORK ----
            thread {
                val mediaBase64 = bitmap?.let {
                    try {
                        bitmapToBase64(it)
                    } catch (e: Exception) {
                        null
                    }
                }

                val json = JsonObject().apply {
                    addProperty("title", title)
                    addProperty("message", message)
                    addProperty("app", sbn.packageName)
                    addProperty("is_group", isGroup)
                    if (sender != null) addProperty("sender", sender)
                    if (mediaBase64 != null) {
                        addProperty("media_url", "data:image/jpeg;base64,$mediaBase64")
                    }
                    addProperty("duration", 10)
                }

                val tvIp = getSharedPreferences("TV_REFS", MODE_PRIVATE)
                    .getString("ip", null)

                if (!tvIp.isNullOrEmpty()) {
                    // Small delay avoids OEM crashes
                    mainHandler.postDelayed({
                        sendToTV(tvIp, json.toString())
                    }, 200)
                }
            }

        } catch (e: Exception) {
            Log.e("PhoneNotifier", "Crash prevented in onNotificationPosted()", e)
        }
    }

    // ---- SAFE BASE64 ENCODER ----
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 65, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    // ---- NETWORK SEND ----
    private fun sendToTV(ip: String, jsonBody: String) {
        try {
            val body = jsonBody.toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

            val request = Request.Builder()
                .url("http://$ip:7979/notify")
                .post(body)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e("PhoneNotifier", "Send failed: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e("PhoneNotifier", "Network error", e)
        }
    }
}
