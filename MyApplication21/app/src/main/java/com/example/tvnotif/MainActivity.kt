package com.example.tvnotif

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var tvLogs: TextView
    private lateinit var etIp: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etIp = findViewById(R.id.etTVIp)
        tvLogs = findViewById(R.id.tvLogs)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val btnSelectApps = findViewById<Button>(R.id.btnSelectApps)

        // Request Permission
        if (!isNotificationServiceEnabled()) {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        val prefs = getSharedPreferences("TV_REFS", MODE_PRIVATE)
        etIp.setText(prefs.getString("ip", ""))

        btnSave.setOnClickListener {
            prefs.edit().putString("ip", etIp.text.toString()).apply()
            Toast.makeText(this, "IP Saved", Toast.LENGTH_SHORT).show()
        }

        btnSelectApps.setOnClickListener {
            startActivity(Intent(this, AppListActivity::class.java))
        }

        btnTest.setOnClickListener {
            sendManualTest(etIp.text.toString(), "Test Notification", "This is a GIF test!")
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        return Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
    }

    private fun sendManualTest(ip: String, title: String, msg: String) {
        val client = OkHttpClient()
        val json = """
            {
                "title": "$title",
                "message": "$msg",
                "app": "TestApp",
                "is_group": true,
                "sender": "Jeremy",
                "media_url": "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExNHJueGZ3bmZ6ZndueGZ3&ep=v1_gifs_search&rid=giphy.gif&ct=g"
            }
        """.trimIndent()

        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url("http://$ip:7979/notify").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { tvLogs.append("\nFail: ${e.message}") }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread { tvLogs.append("\nSuccess: ${response.code}") }
                response.close()
            }
        })
    }
}
