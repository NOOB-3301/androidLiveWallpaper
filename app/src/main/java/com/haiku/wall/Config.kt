package com.haiku.wall

object Config {
    fun apiKey(): String = BuildConfig.OPENROUTER_API_KEY
    fun apiUrl(): String = BuildConfig.OPENROUTER_API_URL
    fun model(): String = BuildConfig.OPENROUTER_MODEL
    // shortcut: judge is always on, add a Prefs toggle if its extra model call costs too much
    fun judgeEnabled(): Boolean = true
}
