package com.haiku.wall

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * AI endpoint settings. A build-time key (.env) wins and hides the settings UI; otherwise the user's saved values are used.
 * Needs [init] first (App.onCreate), so the WorkManager worker in the main process can read the saved values.
 */
object Config {
    const val OPENAI = "openai"
    const val ANTHROPIC = "anthropic"
    const val DEFAULT_URL = "https://openrouter.ai/api/v1/chat/completions"
    const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"

    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp == null) sp = ctx.applicationContext.getSharedPreferences("ai", Context.MODE_PRIVATE)
    }

    /** True when the key was baked in at build time; the settings UI is then hidden. */
    val fromBuild: Boolean get() = BuildConfig.OPENROUTER_API_KEY.isNotBlank()

    private fun saved(name: String, default: String = "") = sp?.getString(name, null).orEmpty().ifBlank { default }

    fun provider(): String = if (fromBuild) OPENAI else saved("provider", OPENAI)
    fun apiKey(): String = if (fromBuild) BuildConfig.OPENROUTER_API_KEY else saved("key")
    fun model(): String = if (fromBuild) BuildConfig.OPENROUTER_MODEL else saved("model")
    fun apiUrl(): String = when {
        fromBuild -> BuildConfig.OPENROUTER_API_URL
        provider() == ANTHROPIC -> ANTHROPIC_URL
        else -> saved("url", DEFAULT_URL)
    }

    fun configured(): Boolean = apiKey().isNotBlank() && model().isNotBlank()

    fun save(provider: String, model: String, key: String, url: String) = sp!!.edit {
        putString("provider", provider); putString("model", model.trim()); putString("key", key.trim()); putString("url", url.trim())
    }

    // shortcut: judge is always on, add a Prefs toggle if its extra model call costs too much
    fun judgeEnabled(): Boolean = true
}
