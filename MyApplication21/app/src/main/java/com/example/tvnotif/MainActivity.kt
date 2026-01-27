package com.example.tvnotif

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.concurrent.thread
import android.util.Log


class MainActivity : AppCompatActivity() {

    private lateinit var etIp: EditText
    private lateinit var tvLogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI
        etIp = findViewById(R.id.etTVIp)
        tvLogs = findViewById(R.id.tvLogs)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnSelectApps = findViewById<Button>(R.id.btnSelectApps)
        val btnTestBasic = findViewById<Button>(R.id.btnTest)
        val btnTestImage = findViewById<Button>(R.id.btnTestImage)
        val btnTestGif = findViewById<Button>(R.id.btnTestGif)

        // 1. Load Saved IP
        val prefs = getSharedPreferences("TV_REFS", MODE_PRIVATE)
        etIp.setText(prefs.getString("ip", ""))

        // 2. Permission Check
        if (!isNotificationServiceEnabled()) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            Toast.makeText(this, "Please enable TV Notifier access", Toast.LENGTH_LONG).show()
        }

        // 3. Button Actions
        btnSave.setOnClickListener {
            val ip = etIp.text.toString().trim()
            prefs.edit().putString("ip", ip).apply()
            Toast.makeText(this, "IP Saved", Toast.LENGTH_SHORT).show()
        }

        btnSelectApps.setOnClickListener {
            // Opens the app selection screen
            startActivity(Intent(this, AppListActivity::class.java))
        }

        btnTestBasic.setOnClickListener {
            sendMediaManual("Manual Test", "This is a plain text message.", null)
        }

        btnTestImage.setOnClickListener {
            sendMediaManual("Image Test", "Testing a high-res photo.", "https://picsum.photos/600/400")
        }

        btnTestGif.setOnClickListener {
            val gifUrl = "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExNHJueGZ3bmZ6ZndueGZ3&ep=v1_gifs_search&rid=giphy.gif&ct=g"
            sendMediaManual("GIF Test", "Testing animated reaction.", gifUrl)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun sendMediaManual(title: String, message: String, mediaUrl: String?) {
        val ip = etIp.text.toString().trim()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Enter TV IP Address first", Toast.LENGTH_SHORT).show()
            return
        }

        // Run in background thread to prevent "NetworkOnMainThread" crash
        thread {
            try {
                val json = JsonObject().apply {
                    addProperty("title", title)
                    addProperty("message", message)
                    addProperty("app", "Phone Tester")
                    addProperty("is_group", false)
                    addProperty("duration", 10)
                    if (mediaUrl != null) addProperty("media_url", mediaUrl)
                }

                val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("http://$ip:7979/notify")
                    .post(body)
                    .build()

                OkHttpClient().newCall(request).execute().use { response ->
                    runOnUiThread {
                        tvLogs.append("\n[${response.code}] $title Sent!")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvLogs.append("\nError: ${e.message}")
                    Log.e("PHONE_APP", "Send failed", e)
                }
            }
        }
    }
}
