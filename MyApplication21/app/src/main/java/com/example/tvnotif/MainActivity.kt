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

        // Initialize Views
        etIp = findViewById(R.id.etTVIp)
        tvLogs = findViewById(R.id.tvLogs)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val btnSelectApps = findViewById<Button>(R.id.btnSelectApps)
        val btnClear = findViewById<Button>(R.id.btnClearLogs)

        // 1. Check for Notification Permission on Startup
        if (!isNotificationServiceEnabled()) {
            logMessage("SYSTEM: Notification Access NOT enabled.")
            showPermissionDialog()
        }

        // Load saved IP
        val prefs = getSharedPreferences("TV_REFS", MODE_PRIVATE)
        etIp.setText(prefs.getString("ip", ""))

        btnSave.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                prefs.edit().putString("ip", ip).apply()
                logMessage("SUCCESS: IP Saved ($ip)")
            } else {
                logMessage("ERROR: IP cannot be empty")
            }
        }

        btnSelectApps.setOnClickListener {
            logMessage("NAV: Opening App Selection...")
            // Ensure you have created AppListActivity
            val intent = Intent(this, AppListActivity::class.java)
            startActivity(intent)
        }

        btnTest.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isEmpty()) {
                logMessage("ERROR: Please set TV IP first")
            } else {
                sendTestNotification(ip)
            }
        }

        btnClear.setOnClickListener {
            tvLogs.text = "--- Logs Cleared ---\n"
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val names = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return names?.contains(packageName) == true
    }

    private fun showPermissionDialog() {
        Toast.makeText(this, "Please enable Notification Access", Toast.LENGTH_LONG).show()
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }

    private fun logMessage(msg: String) {
        // Crucial: This allows background network threads to update the UI
        runOnUiThread {
            tvLogs.append("\n> $msg")
        }
    }

    private fun sendTestNotification(ip: String) {
        val client = OkHttpClient()

        // 1. Prepare JSON (This matches what your TV app expects)
        val json = """
            {
                "title": "Phone Test",
                "message": "Notification sync is working!",
                "app": "TV Notifier",
                "duration": 10,
                "position": 0
            }
        """.trimIndent()

        // 2. Setup RequestBody as JSON
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toRequestBody(mediaType)

        // 3. Create the Request
        val request = Request.Builder()
            .url("http://$ip:7979/notify")
            .post(body)
            .build()

        logMessage("SENDING: POST to $ip:7979...")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logMessage("NETWORK ERROR: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val code = response.code
                if (code == 200) {
                    logMessage("SUCCESS: TV received notification (200)")
                } else {
                    logMessage("TV REJECTED: Code $code (Check TV logs)")
                }
                response.close()
            }
        })
    }
}
