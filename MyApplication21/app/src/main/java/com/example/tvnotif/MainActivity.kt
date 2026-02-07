package com.example.tvnotif

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var tvLogs: TextView

    // Reuse ONE client
    private val httpClient: OkHttpClient by lazy {
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

        // Notification permission check (SAFE)
        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Toast.makeText(
                this,
                "Enable notification access for TV Notifier",
                Toast.LENGTH_LONG
            ).show()
        }

        btnSave.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isEmpty()) {
                toast("IP cannot be empty")
                return@setOnClickListener
            }
            prefs.edit().putString("ip", ip).apply()
            toast("IP saved")
        }

        btnSelectApps.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        btnTestBasic.setOnClickListener {
            sendMediaManual(
                "Manual Test",
                "This is a plain text message.",
                null
            )
        }

        btnTestImage.setOnClickListener {
            sendMediaManual(
                "Image Test",
                "Testing a high-res photo.",
                "https://picsum.photos/600/400"
            )
        }

        btnTestGif.setOnClickListener {
            sendMediaManual(
                "GIF Test",
                "Testing animated reaction.",
                "https://media.giphy.com/media/ICOgUNjpvO0PC/giphy.gif"
            )
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val enabled =
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabled?.contains(packageName) == true
    }

    private fun sendMediaManual(
        title: String,
        message: String,
        mediaUrl: String?
    ) {
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

    private fun logUi(msg: String) {
        runOnUiThread {
            if (tvLogs.lineCount > 100) tvLogs.text = ""
            tvLogs.append("\n$msg")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
