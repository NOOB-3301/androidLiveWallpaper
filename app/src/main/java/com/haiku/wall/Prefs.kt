package com.haiku.wall

import android.content.Context
import androidx.core.content.edit
import com.haiku.wall.store.PlanStore

/** Main-process settings. replayOnUnlock lives in PlanStore's flag file because the `:wallpaper` process reads it. */
class Prefs(ctx: Context) {
    private val app = ctx.applicationContext
    private val sp = app.getSharedPreferences("haiku", Context.MODE_PRIVATE)

    var categories: Set<String>
        get() = sp.getStringSet("categories", null)?.toSet().orEmpty()
        set(v) = sp.edit { putStringSet("categories", v) }
    var customTheme: String
        get() = sp.getString("customTheme", null).orEmpty()
        set(v) = sp.edit { putString("customTheme", v) }
    var intervalHours: Int
        get() = sp.getInt("intervalHours", 0)
        set(v) = sp.edit { putInt("intervalHours", v) }
    var seconds: Int
        get() = sp.getInt("seconds", 12)
        set(v) = sp.edit { putInt("seconds", v) }
    var replayOnUnlock: Boolean
        get() = PlanStore.replayOnUnlock(app)
        set(v) = PlanStore.setReplayOnUnlock(app, v)
    var lastError: String?
        get() = sp.getString("lastError", null)
        set(v) = sp.edit { putString("lastError", v) }
    var lastRunMs: Long
        get() = sp.getLong("lastRunMs", 0L)
        set(v) = sp.edit { putLong("lastRunMs", v) }
}
