package com.example.tvnotif

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
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
        val btnClear = findViewById<Button>(R.id.btnClearLogs)

        // 1. Check for Notification Permission on Startup
        if (!isNotificationServiceEnabled()) {
            logMessage("WARNING: Notification Access NOT enabled.")
            showPermissionDialog()
        }

        val prefs = getSharedPreferences("TV_REFS", MODE_PRIVATE)
        etIp.setText(prefs.getString("ip", ""))

        btnSave.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                prefs.edit().putString("ip", ip).apply()
                logMessage("IP Saved: $ip")
            } else {
                logMessage("ERROR: Enter a valid IP")
            }
        }

        btnSelectApps.setOnClickListener {
            logMessage("App selection clicked (Coming soon)")
        }

        btnTest.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isEmpty()) {
                logMessage("ERROR: Set IP first")
            } else {
                sendTestNotification(ip)
            }
        }

        btnClear.setOnClickListener { tvLogs.text = "--- Logs Cleared ---\n" }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val names = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        // This check is critical to prevent background crashes
        return names?.contains(packageName) == true
    }

    private fun showPermissionDialog() {
        Toast.makeText(this, "Enable Notification Access for TV Notifier", Toast.LENGTH_LONG).show()
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        startActivity(intent)
    }

    private fun logMessage(msg: String) {
        runOnUiThread {
            tvLogs.append("\n> $msg")
        }
    }

    private fun sendTestNotification(ip: String) {
        val client = OkHttpClient()
        val formBody = FormBody.Builder()
            .add("title", "Test")
            .add("message", "Hello from your Phone!")
            .build()

        val request = Request.Builder()
            .url("http://$ip:7979/notify")
            .post(formBody)
            .build()

        logMessage("Sending test to $ip...")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logMessage("NETWORK FAIL: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                logMessage("SERVER SYNC: ${response.code}")
                response.close()
            }
        })
    }
}
