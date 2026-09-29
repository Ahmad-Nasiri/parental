package com.example.parental

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var mpm: MediaProjectionManager
    private val REQ = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 150, 60, 40)
        }

        layout.addView(TextView(this).apply {
            text = "این دستگاه تحت نظارت والدین است.\n\nبرای شروع اشتراک صفحه، دکمه زیر را بزنید."
            textSize = 18f
        })

        layout.addView(Button(this).apply {
            text = "شروع اشتراک صفحه"
            setOnClickListener {
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ)
            }
        })

        layout.addView(Button(this).apply {
            text = "توقف"
            setOnClickListener {
                stopService(Intent(this@MainActivity, ScreenCaptureService::class.java))
            }
        })

        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ && resultCode == Activity.RESULT_OK && data != null) {
            val i = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(this, i)
        }
    }
}
