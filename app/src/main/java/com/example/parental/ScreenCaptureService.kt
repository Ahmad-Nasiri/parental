package com.example.parental

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var ws: WebSocket? = null

    companion object {
        const val CHANNEL_ID = "screen_cap"
        const val NOTIF_ID = 1
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_DATA = "data"
        const val SERVER_URL = "ws://192.168.100.102:8080?role=phone"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "اشتراک صفحه", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("اشتراک صفحه فعال است")
            .setContentText("صفحه این دستگاه برای والدین نمایش داده می‌شود")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }

        val rc = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (rc != -1 && data != null) startProjection(rc, data)
        return START_STICKY
    }

    private fun startProjection(rc: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(rc, data)

        val m = resources.displayMetrics
        val w = m.widthPixels
        val h = m.heightPixels
        val dpi = m.densityDpi

        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "cap", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        connectWs()

        imageReader?.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try { sendFrame(img, w, h) } catch (_: Exception) {} finally { img.close() }
        }, null)
    }

    private fun sendFrame(image: Image, w: Int, h: Int) {
        val p = image.planes[0]
        val rowStride = p.rowStride
        val pixelStride = p.pixelStride
        val padding = rowStride - pixelStride * w

        val bmp = Bitmap.createBitmap(w + padding / pixelStride, h, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(p.buffer)

        val cropped = Bitmap.createBitmap(bmp, 0, 0, w, h)
        val scaled = Bitmap.createScaledBitmap(cropped, w / 2, h / 2, true)

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 45, baos)
        ws?.send(ByteString.of(*baos.toByteArray()))

        bmp.recycle(); cropped.recycle(); scaled.recycle()
    }

    private fun connectWs() {
        val client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder().url(SERVER_URL).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onFailure(webSocket: WebSocket, t: Throwable, r: Response?) {
                android.os.Handler(mainLooper).postDelayed({ connectWs() }, 3000)
            }
        })
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        ws?.close(1000, null)
        super.onDestroy()
    }
}
