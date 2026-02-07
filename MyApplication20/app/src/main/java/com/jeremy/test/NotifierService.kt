package com.jeremy.test

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayDeque

class NotifierService : Service() {

    companion object {
        const val CHANNEL_ID = "TvNotifierChannel"
        const val PORT = 7979
    }

    private lateinit var httpServer: MyHttpServer
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var exoPlayer: ExoPlayer? = null
    private val executor = Executors.newFixedThreadPool(2)

    // Queue for notifications
    private val notificationQueue = ArrayDeque<Pair<View, Long>>()
    private val handler = Handler(Looper.getMainLooper())
    private var isShowing = false

    // Coil loader supporting GIFs
    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(1, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Notifier Active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        )

        try {
            httpServer = MyHttpServer(PORT, this)
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** ---------------- Notification Queue ---------------- */
    fun processIncomingNotification(json: JsonObject) {
        handler.post {
            val layoutRes = if (json.get("is_group")?.asBoolean == true)
                R.layout.notification_group else R.layout.notification_basic

            val view = LayoutInflater.from(this).inflate(layoutRes, null)
            val titleView = view.findViewById<TextView>(R.id.tv_title)
            val messageView = view.findViewById<TextView>(R.id.tv_message)
            val mediaUrl = json.get("media_url")?.asString
            val duration = json.get("duration")?.asLong ?: 10

            titleView?.text = json.get("title")?.asString ?: "Notification"

            val sender = json.get("sender")?.asString
            val msgText = json.get("message")?.asString ?: ""
            messageView?.text = if (!sender.isNullOrEmpty())
                "<b>$sender:</b> $msgText" else msgText

            handleMedia(view, mediaUrl)

            // Add to queue
            notificationQueue.add(Pair(view, duration))
            showNextNotification()
        }
    }

    private fun showNextNotification() {
        if (isShowing || notificationQueue.isEmpty()) return
        isShowing = true
        val (view, duration) = notificationQueue.removeFirst()
        showOverlay(view, duration)
    }

    /** ---------------- Overlay Display ---------------- */
    private fun showOverlay(view: View, duration: Long) {
        removeOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 50
            y = 50
        }

        try {
            windowManager.addView(view, params)
            overlayView = view

            // Fade-in animation
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(300).start()

            handler.postDelayed({
                removeOverlay()
                isShowing = false
                showNextNotification()
            }, duration * 1000)
        } catch (e: Exception) {
            e.printStackTrace()
            isShowing = false
            showNextNotification()
        }
    }

    private fun removeOverlay() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null

        overlayView?.let {
            try {
                // Fade-out before removing
                it.animate().alpha(0f).setDuration(300).withEndAction {
                    try { windowManager.removeView(it) } catch (ignored: Exception) {}
                }.start()
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    /** ---------------- Media Handling ---------------- */
    private fun handleMedia(view: View, url: String?) {
        val imageView = view.findViewById<ImageView>(R.id.iv_media)
        val playerView = view.findViewById<PlayerView>(R.id.player_view)

        if (url.isNullOrEmpty()) return

        when {
            url.contains(".mp4") || url.contains("m3u8") || url.contains("stream") -> {
                playerView?.visibility = View.VISIBLE
                imageView?.visibility = View.GONE
                startVideo(playerView, url)
            }
            else -> {
                imageView?.visibility = View.VISIBLE
                playerView?.visibility = View.GONE
                imageView?.load(url, imageLoader) {
                    crossfade(true)
                    allowHardware(false)
                }
            }
        }
    }

    private fun startVideo(playerView: PlayerView?, url: String) {
        playerView ?: return
        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            volume = 0f
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
        playerView.player = exoPlayer
        playerView.useController = false
    }

    /** ---------------- HTTP Server ---------------- */
    inner class MyHttpServer(port: Int, service: NotifierService) : NanoHTTPD(port) {
        private val ref = WeakReference(service)
        override fun serve(session: IHTTPSession): Response {
            if (session.method == Method.POST && session.uri == "/notify") {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val body = files["postData"] ?: return newFixedLengthResponse("Error")
                val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                ref.get()?.processIncomingNotification(json)
                return newFixedLengthResponse("OK")
            }
            return newFixedLengthResponse("Not Found")
        }
    }

    /** ---------------- Extras ---------------- */
    override fun onDestroy() {
        httpServer.stop()
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: android.content.Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "TV Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }
}
