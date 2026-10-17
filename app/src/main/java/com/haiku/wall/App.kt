package com.haiku.wall

import android.app.Application
import com.haiku.wall.render.HandFont

/** Runs in both the main and `:wallpaper` processes, so it must stay free of network, JS and WorkManager setup. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        HandFont.init(this)
    }
}
