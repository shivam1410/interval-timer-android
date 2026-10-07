package com.shivam1410.intervaltimer

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Timer.init(this)
        Media.init(this)
        History.init(this)
        Drive.init(this)
    }
}
