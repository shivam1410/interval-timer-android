package com.shivam1410.intervaltimer

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Library.init(this)
        TogglSync.init(this)
        Timer.init(this)
        Media.init(this)
        History.init(this)
        Drive.init(this)
    }
}
