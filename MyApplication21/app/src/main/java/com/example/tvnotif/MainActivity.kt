package com.example.tvnotif

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

// ---- Main Activity ----
class MainActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var tvLogs: TextView
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etIp = findViewById(R.id.etTVIp)
        tvLogs = findViewById(R.id.tvLogs)

        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnSelectApps = findViewById<Button>(R.id.btnSelectApps)
        val btnTestBasic = findViewById<Button>(R.id.btnTest)
        val btnTestImage = findViewById<Button>(R.id.btnTestImage)
        val btnTestGif = findViewById<Button>(R.id.btnTestGif)

        val prefs = getSharedPreferences("TV_PREFS", MODE_PRIVATE)
        etIp.setText(prefs.getString("ip", ""))

        // Notification permission check
        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            toast("Enable notification access for TV Notifier")
        }

        // ---- SAVE IP ----
        btnSave.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isEmpty()) {
                toast("IP cannot be empty")
                return@setOnClickListener
            }
            prefs.edit().putString("ip", ip).apply()
            toast("IP saved")
        }

        // ---- APP SELECTION ----
        btnSelectApps.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        // ---- TEST BUTTONS ----
        btnTestBasic.setOnClickListener {
            sendManualNotification("Manual Test", "This is plain text", null)
        }

        btnTestImage.setOnClickListener {
            sendManualNotification(
                "Image Test",
                "This is a test image",
                "https://picsum.photos/600/400"
            )
        }

        btnTestGif.setOnClickListener {
            sendManualNotification(
                "GIF Test",
                "This is a test animated GIF",
                "https://media.giphy.com/media/ICOgUNjpvO0PC/giphy.gif"
            )
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun logUi(msg: String) {
        runOnUiThread {
            if (tvLogs.lineCount > 100) tvLogs.text = ""
            tvLogs.append("\n$msg")
        }
    }

    private fun sendManualNotification(title: String, message: String, mediaUrl: String?) {
        val ip = etIp.text.toString().trim()
        if (ip.isEmpty()) {
            toast("Enter TV IP first")
            return
        }

        thread {
            try {
                val json = JsonObject().apply {
                    addProperty("title", title)
                    addProperty("message", message)
                    addProperty("app", "Phone Tester")
                    addProperty("is_group", false)
                    addProperty("duration", 10)
                    mediaUrl?.let { addProperty("media_url", it) }
                }

                val body = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("http://$ip:7979/notify")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    logUi("[${response.code}] $title sent")
                }
            } catch (e: Exception) {
                Log.e("PHONE_APP", "Send failed", e)
                logUi("Error: ${e.localizedMessage}")
            }
        }
    }
}

// ---- Notification Listener ----
class NotifierListenerService : android.service.notification.NotificationListenerService() {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
        val packageName = sbn.packageName
        val prefs = getSharedPreferences("SELECTED_APPS", Context.MODE_PRIVATE)
        val selectedApps = prefs.getStringSet("packages", emptySet()) ?: emptySet()

        if (!selectedApps.contains(packageName)) return

        val extras = sbn.notification.extras ?: return

        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        val title = extras.getString(Notification.EXTRA_TITLE) ?: "New Message"
        val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val sender = if (isGroup) extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: title else title

        // Extract image safely
        val bitmap = extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
            ?: extras.getParcelable<Bitmap>(Notification.EXTRA_LARGE_ICON)
        val mediaData = bitmap?.let { bitmapToBase64(it) }

        val json = JsonObject().apply {
            addProperty("title", title)
            addProperty("message", message)
            addProperty("app", packageName)
            addProperty("is_group", isGroup)
            addProperty("sender", if (isGroup) sender else null)
            mediaData?.let { addProperty("media_url", "data:image/jpeg;base64,$it") }
            addProperty("duration", 10)
        }

        val tvIp = getSharedPreferences("TV_REFS", Context.MODE_PRIVATE).getString("ip", null)
        if (!tvIp.isNullOrEmpty()) {
            sendToTV(tvIp, json.toString())
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String? {
        return try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("NotifierListener", "Bitmap encode failed", e)
            null
        }
    }

    private fun sendToTV(ip: String, jsonBody: String) {
        thread {
            try {
                val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("http://$ip:7979/notify")
                    .post(body)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    Log.d("NotifierListener", "Notification sent, code=${response.code}")
                }
            } catch (e: IOException) {
                Log.e("NotifierListener", "Failed to send notification", e)
            }
        }
    }
}
