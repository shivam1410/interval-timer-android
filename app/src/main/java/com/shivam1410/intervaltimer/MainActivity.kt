package com.shivam1410.intervaltimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableLongStateOf

class MainActivity : ComponentActivity() {
    private val now = mutableLongStateOf(System.currentTimeMillis())
    private val handler = Handler(Looper.getMainLooper())

    // The UI clock runs only while the activity is visible; in the background nothing ticks.
    private val tick = object : Runnable {
        override fun run() {
            now.longValue = System.currentTimeMillis()
            if (Timer.session.value.running(Timer.plan) && Timer.session.value.left(now.longValue) == 0L) Timer.sync(playCue = true)
            handler.postDelayed(this, 1000 - now.longValue % 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (Media.missing) Media.sync()
        Updater.check(this)
        setContent { AppTheme { Root(now.longValue) } }
    }

    override fun onStart() {
        super.onStart()
        handler.post(tick)
    }

    override fun onStop() {
        handler.removeCallbacks(tick)
        super.onStop()
    }
}
