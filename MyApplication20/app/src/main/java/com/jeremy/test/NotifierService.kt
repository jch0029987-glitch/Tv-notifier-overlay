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

    // --- Rate limiting ---
    private var lastNotificationTime = 0L
    private val rateLimitMillis = 3000L // 3 seconds
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

        // --- SoundPool ---
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attrs).build()
        soundId = soundPool.load(this, R.raw.notify, 1)

        // --- HTTP Server ---
        try {
            httpServer = MyHttpServer(PORT, this)
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d(TAG, "HTTP server started on port $PORT")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start HTTP server", e)
        }
    }

    override fun onDestroy() {
        httpServer.stop()
        removeOverlay()
        executor.shutdown()
        soundPool.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /* ---------------- Notification ---------------- */

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

            // --- Rate limiting ---
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
                // Merge message
                pendingMessages.add(json)
                Log.d(TAG, "Rate limit active, merging message")
                // Schedule merged overlay after rateLimitMillis
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
        merged.addProperty("title", messages.first().get("title")?.asString ?: "Notification")
        merged.addProperty("message", messages.joinToString("\n") { it.get("message")?.asString ?: "" })
        merged.addProperty("app", messages.first().get("app")?.asString ?: "")
        merged.addProperty("duration", messages.first().get("duration")?.asLong ?: 10)
        merged.addProperty("position", messages.first().get("position")?.asInt ?: -1)
        merged.addProperty("is_group", true)
        merged.addProperty("group_name", messages.first().get("group_name")?.asString)
        return merged
    }

    private fun displayOverlay(json: JsonObject) {
        removeOverlay()

        // --- Play sound ---
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)

        val layout = if (json.get("is_group")?.asBoolean == true && json.get("group_name")?.asString != null) {
            createGroupNotificationView(
                json.get("title")?.asString ?: "Notification",
                json.get("message")?.asString ?: "",
                json.get("app")?.asString ?: "",
                json.get("group_name")?.asString ?: "",
                json.get("sender")?.asString,
                json.get("media_url")?.asString
            )
        } else {
            createBasicNotificationView(
                json.get("title")?.asString ?: "Notification",
                json.get("message")?.asString ?: "",
                json.get("app")?.asString ?: "",
                json.get("media_url")?.asString
            )
        }

        val smartPosition = resolvePosition(
            json.get("app")?.asString ?: "",
            json.get("is_group")?.asBoolean ?: false,
            0,
            json.get("position")?.asInt ?: -1
        )

        showOverlay(layout, smartPosition, json.get("duration")?.asLong ?: 10)
    }

    /* ---------------- Smart Position ---------------- */

    private fun resolvePosition(app: String, isGroup: Boolean, priority: Int, requestedPosition: Int): Int {
        if (requestedPosition >= 0) return requestedPosition
        if (priority > 0) return 4
        return when (app.lowercase()) {
            "messenger", "telegram", "whatsapp", "signal", "discord" -> if (isGroup) 4 else 0
            "phone", "dialer" -> 4
            "system", "android" -> 5
            else -> 2
        }
    }

    /* ---------------- Overlay UI ---------------- */

    private fun createBasicNotificationView(title: String, message: String, app: String, mediaUrl: String?): View {
        val layout = LayoutInflater.from(this).inflate(R.layout.notification_basic, null)
        layout.findViewById<TextView>(R.id.tv_app_icon)?.text = getAppIcon(app)
        layout.findViewById<TextView>(R.id.tv_app_name)?.text = app.ifEmpty { "Notification" }
        layout.findViewById<TextView>(R.id.tv_title)?.text = title
        layout.findViewById<TextView>(R.id.tv_message)?.text = message
        layout.findViewById<TextView>(R.id.tv_time)?.text =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        mediaUrl?.let { url ->
            val imageView = layout.findViewById<ImageView>(R.id.iv_media)
            imageView?.visibility = View.VISIBLE
            executor.execute {
                try {
                    val bitmap = downloadImage(url)
                    Handler(Looper.getMainLooper()).post { imageView.setImageBitmap(bitmap) }
                } catch (_: Exception) {}
            }
        }

        return layout
    }

    private fun createGroupNotificationView(title: String, message: String, app: String, groupName: String, sender: String?, mediaUrl: String?): View {
        val layout = LayoutInflater.from(this).inflate(R.layout.notification_group, null)
        layout.findViewById<TextView>(R.id.tv_group_name)?.text = groupName

        val messagesContainer = layout.findViewById<LinearLayout>(R.id.messages_container)
        val messageItem = LayoutInflater.from(this).inflate(R.layout.item_group_message, messagesContainer, false)
        sender?.let { messageItem.findViewById<TextView>(R.id.tv_sender)?.apply { text = it; visibility = View.VISIBLE } }
        messageItem.findViewById<TextView>(R.id.tv_message)?.text = message
        messageItem.findViewById<TextView>(R.id.tv_time)?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        messagesContainer?.addView(messageItem)

        mediaUrl?.let { url ->
            val imageView = messageItem.findViewById<ImageView>(R.id.iv_media)
            imageView?.visibility = View.VISIBLE
            executor.execute {
                try {
                    val bitmap = downloadImage(url)
                    Handler(Looper.getMainLooper()).post { imageView.setImageBitmap(bitmap) }
                } catch (_: Exception) {}
            }
        }

        return layout
    }

    private fun getAppIcon(app: String): String = when (app.lowercase()) {
        "messenger" -> "💙"
        "telegram" -> "📱"
        "discord" -> "🎮"
        else -> "📺"
    }

    private fun showOverlay(view: View, position: Int, duration: Long) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)

        params.gravity = when (position) {
            0 -> Gravity.TOP or Gravity.END
            1 -> Gravity.TOP or Gravity.START
            2 -> Gravity.BOTTOM or Gravity.END
            3 -> Gravity.BOTTOM or Gravity.START
            4 -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            5 -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            else -> Gravity.TOP or Gravity.END
        }

        windowManager.addView(view, params)
        overlayView = view
        view.postDelayed({ removeOverlay() }, duration * 1000)
    }

    private fun removeOverlay() {
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }

    private fun canDrawOverlays(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    private fun downloadImage(url: String): Bitmap? = try { BufferedInputStream(URL(url).openStream()).use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }

    /* ---------------- HTTP Server ---------------- */

    inner class MyHttpServer(port: Int, service: NotifierService) : NanoHTTPD(port) {
        private val ref = WeakReference(service)
        private val gson = Gson()

        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                "/health" -> newFixedLengthResponse("OK")
                "/notify" -> handleNotify(session)
                else -> newFixedLengthResponse("Invalid endpoint")
            }
        }

        private fun handleNotify(session: IHTTPSession): Response {
            if (session.method != Method.POST) return newFixedLengthResponse("POST required")
            val files = HashMap<String, String>()
            session.parseBody(files)
            val json = gson.fromJson(files["postData"], JsonObject::class.java)

            ref.get()?.showNotification(
                title = json["title"]?.asString ?: "Notification",
                message = json["message"]?.asString ?: "",
                app = json["app"]?.asString ?: "",
                duration = json["duration"]?.asLong ?: 10,
                position = json["position"]?.asInt ?: -1,
                priority = json["priority"]?.asInt ?: 0,
                isGroup = json["is_group"]?.asBoolean ?: false,
                groupName = json["group_name"]?.asString,
                sender = json["sender"]?.asString,
                mediaUrl = json["media_url"]?.asString
            )
            return newFixedLengthResponse("""{"status":"ok"}""")
        }
    }

    /* ---------------- Notification Channel ---------------- */

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val silentChannel = NotificationChannel(
                CHANNEL_ID,
                "TV Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null); setShowBadge(false) }
            manager.createNotificationChannel(silentChannel)
        }
    }

    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Notifier Active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
}