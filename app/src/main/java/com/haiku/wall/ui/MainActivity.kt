package com.haiku.wall.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.haiku.wall.Config
import com.haiku.wall.Prefs
import com.haiku.wall.ui.theme.HaikuTheme
import com.haiku.wall.work.Scheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Honour the persisted interval (0 = Off); nothing is scheduled until the user picks one here or in settings.
        if (savedInstanceState == null) Scheduler.apply(this, Prefs(this).intervalHours)
        setContent {
            HaikuTheme {
                var ready by remember { mutableStateOf(Config.configured()) }
                if (ready) MainScreen() else AiGate { ready = true }
            }
        }
    }
}
