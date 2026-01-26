package com.example.tvnotif
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private val prefsName = "TVNotifierPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etTVIp = findViewById<EditText>(R.id.etTVIp)
        val btnSelectApps = findViewById<Button>(R.id.btnSelectApps)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnTest = findViewById<Button>(R.id.btnTest)

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        etTVIp.setText(prefs.getString("tvIp", ""))

        // Select Apps button
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
                    Toast.makeText(this, "Apps saved", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Save TV IP
        btnSave.setOnClickListener {
            val tvIp = etTVIp.text.toString()
            if (tvIp.isEmpty()) {
                Toast.makeText(this, "Enter TV IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("tvIp", tvIp).apply()
            Toast.makeText(this, "TV IP saved", Toast.LENGTH_SHORT).show()
        }

        // Test button
        btnTest.setOnClickListener {
            val tvIp = etTVIp.text.toString()
            if (tvIp.isEmpty()) {
                Toast.makeText(this, "Enter TV IP first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendToTV(tvIp, "Test Notification", "This is a test!", 5)
        }
    }

    private fun sendToTV(tvIp: String, title: String, message: String, duration: Long) {
        val json = JSONObject().apply {
            put("title", title)
            put("message", message)
            put("duration", duration)
            put("position", 0)
        }

        val body = json.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("http://$tvIp:7979/notify")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Sent!", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}