package com.jeremy.test

import android.app.*
import android.content.*
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.util.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fi.iki.elonen.NanoHTTPD
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class NotifierService : Service() {

    private lateinit var httpServer: MyHttpServer
    private var overlayView: View? = null
    private lateinit var windowManager: WindowManager
    private val executor = Executors.newFixedThreadPool(2)
    private var exoPlayer: ExoPlayer? = null

    // GIF-enabled Image Loader
    private val imageLoader by lazy {
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                else add(GifDecoder.Factory())
            }.build()
    }

    companion object {
        const val CHANNEL_ID = "TvNotifierChannel"
        const val PORT = 7979
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannels()
        startForeground(1, createForegroundNotification())
        
        try {
            httpServer = MyHttpServer(PORT, this)
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        } catch (e: Exception) { Log.e("TV_SERVER", "Start failed", e) }
    }

    /* ---------------- Logic Core ---------------- */

    fun processIncomingNotification(json: JsonObject) {
        Handler(Looper.getMainLooper()).post {
            val isGroup = json.get("is_group")?.asBoolean ?: false
            val mediaUrl = json.get("media_url")?.asString
            
            val layoutRes = if (isGroup) R.layout.notification_group else R.layout.notification_basic
            val view = LayoutInflater.from(this).inflate(layoutRes, null)

            // Basic Text Setup
            view.findViewById<TextView>(R.id.tv_title)?.text = json.get("title")?.asString ?: "Notification"
            val msgBody = json.get("message")?.asString ?: ""
            val sender = json.get("sender")?.asString
            
            val messageTextView = view.findViewById<TextView>(R.id.tv_message)
            if (isGroup && sender != null) {
                messageTextView?.text = android.text.Html.fromHtml("<b>$sender:</b> $msgBody", 0)
            } else {
                messageTextView?.text = msgBody
            }

            // Media Handling (GIF / Image / Video)
            handleMedia(view, mediaUrl)

            showOverlay(view, json.get("duration")?.asLong ?: 10)
        }
    }

    private fun handleMedia(view: View, url: String?) {
        val imageView = view.findViewById<ImageView>(R.id.iv_media)
        val playerView = view.findViewById<PlayerView>(R.id.player_view)

        if (url.isNullOrEmpty()) return

        if (url.contains(".mp4") || url.contains("m3u8") || url.contains("stream")) {
            // VIDEO
            playerView?.visibility = View.VISIBLE
            imageView?.visibility = View.GONE
            startVideo(playerView, url)
        } else {
            // IMAGE or GIF
            imageView?.visibility = View.VISIBLE
            playerView?.visibility = View.GONE
            imageView?.load(url, imageLoader) {
                crossfade(true)
                allowHardware(false) // Better for overlays
            }
        }
    }

    private fun startVideo(playerView: PlayerView?, url: String) {
        playerView?.let {
            exoPlayer?.release()
            exoPlayer = ExoPlayer.Builder(this).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                volume = 0f // Muted by default
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
            it.player = exoPlayer
            it.useController = false
        }
    }

    /* ---------------- UI Engine ---------------- */

    private fun showOverlay(view: View, duration: Long) {
        removeOverlay()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 50; y = 50
        }

        windowManager.addView(view, params)
        overlayView = view
        Handler(Looper.getMainLooper()).postDelayed({ removeOverlay() }, duration * 1000)
    }

    private fun removeOverlay() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        overlayView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        overlayView = null
    }

    /* ---------------- HTTP Server ---------------- */

    inner class MyHttpServer(port: Int, service: NotifierService) : NanoHTTPD(port) {
        private val ref = WeakReference(service)
        override fun serve(session: IHTTPSession): Response {
            if (session.method == Method.POST && session.uri == "/notify") {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val body = files["postData"] ?: return newFixedLengthResponse("Error")
                val json = JsonParser.parseString(body).asJsonObject
                ref.get()?.processIncomingNotification(json)
                return newFixedLengthResponse("OK")
            }
            return newFixedLengthResponse("Not Found")
        }
    }

    /* ---------------- Extras ---------------- */

    override fun onDestroy() {
        httpServer.stop()
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(CHANNEL_ID, "TV Overlay", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }
    }

    private fun createForegroundNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("TV Notifier Active").setSmallIcon(android.R.drawable.ic_dialog_info).build()
}
