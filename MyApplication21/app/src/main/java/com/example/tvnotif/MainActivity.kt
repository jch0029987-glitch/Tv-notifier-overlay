package com.example.tvnotif

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val prefsName = "TVNotifierPrefs"
    private lateinit var tvLogs: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etTVIp = findViewById<EditText>(R.id.etTVIp)
        val btnSelectApps = findViewById<Button>(R.id.btnSelectApps)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)
        val btnClearLogs = findViewById<Button>(R.id.btnClearLogs) // Add this button to your XML
        tvLogs = findViewById(R.id.tvLogs)

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        etTVIp.setText(prefs.getString("tvIp", ""))

        logMessage("App Started. Check Notification Access!")

        btnSelectApps.setOnClickListener {
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { it.packageName to pm.getApplicationLabel(it).toString() }
                .sortedBy { it.second }

            val labels = installedApps.map { it.second }.toTypedArray()
            val packageNames = installedApps.map { it.first }
            val checkedItems = BooleanArray(labels.size) { i ->
                prefs.getBoolean(packageNames[i], false)
            }

            android.app.AlertDialog.Builder(this)
                .setTitle("Select apps to send to TV")
                .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton("Save") { _, _ ->
                    val editor = prefs.edit()
                    for (i in labels.indices) {
                        editor.putBoolean(packageNames[i], checkedItems[i])
                    }
                    editor.apply()
                    logMessage("App filters updated.")
                }
                .show()
        }

        btnSave.setOnClickListener {
            val tvIp = etTVIp.text.toString()
            prefs.edit().putString("tvIp", tvIp).apply()
            logMessage("TV IP saved: $tvIp")
        }

        btnTest.setOnClickListener {
            val tvIp = etTVIp.text.toString()
            if (tvIp.isEmpty()) {
                logMessage("ERROR: Enter TV IP first")
                return@setOnClickListener
            }
            sendToTV(tvIp, "Test", "Hello from Phone!", "com.example.tvnotif")
        }
    }

    fun logMessage(msg: String) {
        runOnUiThread {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            tvLogs.append("[$time] $msg\n")
        }
    }

    private fun sendToTV(tvIp: String, title: String, message: String, appName: String) {
        val json = JSONObject().apply {
            put("title", title)
            put("message", message)
            put("app", appName)
            put("duration", 10)
        }

        // IMPORTANT: TV expects "postData" key
        val formBody = FormBody.Builder()
            .add("postData", json.toString())
            .build()

        val request = Request.Builder()
            .url("http://$tvIp:7979/notify")
            .post(formBody)
            .build()

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
