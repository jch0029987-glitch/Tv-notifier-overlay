package com.jeremy.test

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD
import java.io.BufferedInputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class NotifierService : Service() {

    private lateinit var httpServer: MyHttpServer
    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager
    private val executor = Executors.newFixedThreadPool(2)

    private lateinit var soundPool: SoundPool
    private var soundId: Int = 0

    private var lastNotificationTime = 0L
    private val rateLimitMillis = 3000L 
    private val pendingMessages = mutableListOf<JsonObject>()

    companion object {
        const val CHANNEL_ID = "TvNotifierChannel"
        const val PORT = 7979
        private const val TAG = "NotifierService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotifierService starting...")

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannels()
        startForeground(1, createNotification())

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attrs).build()
        
        // Ensure R.raw.notify exists or this will return 0
        soundId = soundPool.load(this, R.raw.notify, 1)

        try {
            httpServer = MyHttpServer(PORT, this)
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "HTTP server started on port $PORT")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start HTTP server", e)
        }
    }

    override fun onDestroy() {
        if (::httpServer.isInitialized) httpServer.stop()
        removeOverlay()
        executor.shutdown()
        soundPool.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /* ---------------- Notification Logic ---------------- */

    fun showNotification(
        title: String,
        message: String,
        app: String = "",
        duration: Long = 10,
        position: Int = -1,
        priority: Int = 0,
        mediaUrl: String? = null,
        isGroup: Boolean = false,
        groupName: String? = null,
        sender: String? = null
    ) {
        Handler(Looper.getMainLooper()).post {
            if (!canDrawOverlays()) {
                Log.w(TAG, "Overlay permission not granted")
                return@post
            }

            val now = System.currentTimeMillis()
            val withinRateLimit = now - lastNotificationTime < rateLimitMillis
            lastNotificationTime = now

            val json = JsonObject().apply {
                addProperty("title", title)
                addProperty("message", message)
                addProperty("app", app)
                addProperty("duration", duration)
                addProperty("position", position)
                addProperty("is_group", isGroup)
                addProperty("group_name", groupName)
                addProperty("sender", sender)
                mediaUrl?.let { addProperty("media_url", it) }
            }

            if (withinRateLimit) {
                pendingMessages.add(json)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (pendingMessages.isNotEmpty()) {
                        val merged = mergeMessages(pendingMessages)
                        pendingMessages.clear()
                        displayOverlay(merged)
                    }
                }, rateLimitMillis)
            } else {
                displayOverlay(json)
            }
        }
    }

    private fun mergeMessages(messages: List<JsonObject>): JsonObject {
        val merged = JsonObject()
        merged.addProperty("title", messages.firstOrNull()?.get("title")?.asString ?: "Notification")
        merged.addProperty("message", messages.joinToString("\n") { it.get("message")?.asString ?: "" })
        merged.addProperty("app", messages.firstOrNull()?.get("app")?.asString ?: "")
        merged.addProperty("duration", 10L)
        merged.addProperty("is_group", true)
        return merged
    }

    private fun displayOverlay(json: JsonObject) {
        removeOverlay()
        if (soundId != 0) soundPool.play(soundId, 1f, 1f, 1, 0, 1f)

        val isGroup = json.get("is_group")?.asBoolean == true
        val layout = if (isGroup && json.has("group_name")) {
            createGroupNotificationView(
                json.get("title")?.asString ?: "",
                json.get("message")?.asString ?: "",
                json.get("app")?.asString ?: "",
                json.get("group_name")?.asString ?: "Group",
                json.get("sender")?.asString,
                json.get("media_url")?.asString
            )
        } else {
            createBasicNotificationView(
                json.get("title")?.asString ?: "",
                json.get("message")?.asString ?: "",
                json.get("app")?.asString ?: "",
                json.get("media_url")?.asString
            )
        }

        val pos = json.get("position")?.asInt ?: -1
        val smartPosition = resolvePosition(json.get("app")?.asString ?: "", isGroup, 0, pos)
        showOverlay(layout, smartPosition, json.get("duration")?.asLong ?: 10)
    }

    private fun resolvePosition(app: String, isGroup: Boolean, priority: Int, reqPos: Int): Int {
        if (reqPos >= 0) return reqPos
        return when (app.lowercase()) {
            "messenger", "whatsapp" -> if (isGroup) 4 else 0
            else -> 2
        }
    }

    /* ---------------- Overlay UI ---------------- */

    private fun createBasicNotificationView(title: String, message: String, app: String, mediaUrl: String?): View {
        val layout = LayoutInflater.from(this).inflate(R.layout.notification_basic, null)
        layout.findViewById<TextView>(R.id.tv_app_name)?.text = app.ifEmpty { "Notification" }
        layout.findViewById<TextView>(R.id.tv_title)?.text = title
        layout.findViewById<TextView>(R.id.tv_message)?.text = message
        layout.findViewById<TextView>(R.id.tv_time)?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        mediaUrl?.let { url ->
            val iv = layout.findViewById<ImageView>(R.id.iv_media)
            executor.execute {
                val bmp = downloadImage(url)
                Handler(Looper.getMainLooper()).post { iv?.setImageBitmap(bmp); iv?.visibility = View.VISIBLE }
            }
        }
        return layout
    }

    private fun createGroupNotificationView(title: String, message: String, app: String, groupName: String, sender: String?, mediaUrl: String?): View {
        val layout = LayoutInflater.from(this).inflate(R.layout.notification_group, null)
        layout.findViewById<TextView>(R.id.tv_group_name)?.text = groupName
        val container = layout.findViewById<LinearLayout>(R.id.messages_container)
        val item = LayoutInflater.from(this).inflate(R.layout.item_group_message, container, false)
        item.findViewById<TextView>(R.id.tv_sender)?.text = sender ?: "System"
        item.findViewById<TextView>(R.id.tv_message)?.text = message
        container?.addView(item)
        return layout
    }

    private fun showOverlay(view: View, position: Int, duration: Long) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when (position) {
                0 -> Gravity.TOP or Gravity.END
                2 -> Gravity.BOTTOM or Gravity.END
                4 -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
                else -> Gravity.TOP or Gravity.END
            }
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
            Handler(Looper.getMainLooper()).postDelayed({ removeOverlay() }, duration * 1000)
        } catch (e: Exception) { Log.e(TAG, "Error adding overlay", e) }
    }

    private fun removeOverlay() {
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)
    private fun downloadImage(url: String): Bitmap? = try { URL(url).openStream().use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }

    /* ---------------- HTTP Server (FIXED) ---------------- */

    inner class MyHttpServer(port: Int, service: NotifierService) : NanoHTTPD(port) {
        private val ref = WeakReference(service)
        private val gson = Gson()

        override fun serve(session: IHTTPSession): Response {
            return try {
                if (session.uri == "/notify" && session.method == Method.POST) {
                    val files = HashMap<String, String>()
                    session.parseBody(files)
                    val rawBody = files["postData"] ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing Body")

                    val json = JsonParser.parseString(rawBody).asJsonObject
                    ref.get()?.showNotification(
                        title = json.get("title")?.asString ?: "Notification",
                        message = json.get("message")?.asString ?: "",
                        app = json.get("app")?.asString ?: "Phone",
                        duration = json.get("duration")?.asLong ?: 10,
                        position = json.get("position")?.asInt ?: -1,
                        isGroup = json.get("is_group")?.asBoolean ?: false,
                        groupName = json.get("group_name")?.asString,
                        sender = json.get("sender")?.asString,
                        mediaUrl = json.get("media_url")?.asString
                    )
                    newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"ok\"}")
                } else {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server Error", e)
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "TV Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    private fun createNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("TV Notifier Active").setSmallIcon(android.R.drawable.ic_dialog_info).build()
}
