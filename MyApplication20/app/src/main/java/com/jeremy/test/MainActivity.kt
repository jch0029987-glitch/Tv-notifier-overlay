package com.jeremy.test

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  // ← Make sure this layout exists!

        // Buttons - these IDs MUST match your activity_main.xml exactly
        val startButton = findViewById<Button>(R.id.startServiceButton)
        val stopButton = findViewById<Button>(R.id.stopServiceButton)

        startButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Overlay permission needed - grant via ADB (no UI on TV)",
                    Toast.LENGTH_LONG
                ).show()
                // On TV this intent usually does nothing - use ADB instead!
                // startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } else {
                // Use correct service class name!
                val serviceIntent = Intent(this, NotifierService::class.java)
                startForegroundService(serviceIntent)  // Better for foreground services
                Toast.makeText(this, "Service started on port 7979", Toast.LENGTH_SHORT).show()
            }
        }

        stopButton.setOnClickListener {
            val serviceIntent = Intent(this, NotifierService::class.java)
            stopService(serviceIntent)
            Toast.makeText(this, "Service stopped", Toast.LENGTH_SHORT).show()
        }
    }
}