package com.haiku.wall.gen

import com.haiku.wall.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal OpenRouter chat client. Error messages carry the HTTP status and OpenRouter's text, never the key. */
object OpenRouter {

    private class Transient(msg: String, val rateLimited: Boolean, cause: Throwable? = null) : Exception(msg, cause)

    /**
     * [turns] are (role, text) pairs; [imageDataUrl] is attached to the last one. [system] goes in one text block
     * with cache_control (a body field, read by Anthropic models via OpenRouter, ignored by others).
     */
    suspend fun chat(
        system: String?,
        turns: List<Pair<String, String>>,
        maxTokens: Int,
        json: Boolean = false,
        imageDataUrl: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val key = Config.apiKey()
        val body = JSONObject()
            .put("model", Config.model())
            .put("max_tokens", maxTokens)
            .put("messages", messages(system, turns, imageDataUrl))
        if (json) body.put("response_format", JSONObject().put("type", "json_object"))
        val payload = body.toString()
        try {
            post(key, payload)
        } catch (e: Transient) { // retry once on 429 / 5xx / network
            delay(if (e.rateLimited) 10_000 else 3_000)
            try { post(key, payload) } catch (e2: Transient) { throw GenerateException(e2.message ?: "OpenRouter failed", e2) }
        }
    }

    private fun messages(system: String?, turns: List<Pair<String, String>>, image: String?): JSONArray {
        val arr = JSONArray()
        if (system != null) arr.put(
            JSONObject().put("role", "system").put(
                "content", JSONArray().put(
                    JSONObject().put("type", "text").put("text", system)
                        .put("cache_control", JSONObject().put("type", "ephemeral"))
                )
            )
        )
        turns.forEachIndexed { i, (role, text) ->
            val content: Any = if (image != null && i == turns.lastIndex) JSONArray()
                .put(JSONObject().put("type", "text").put("text", text))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", image)))
            else text
            arr.put(JSONObject().put("role", role).put("content", content))
        }
        return arr
    }

    private fun post(key: String, payload: String): String {
        val c = URL(Config.apiUrl()).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 120_000
            c.readTimeout = 120_000
            if (key.isNotBlank()) c.setRequestProperty("Authorization", "Bearer " + key)
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("HTTP-Referer", "https://github.com/haiku-wall")
            c.setRequestProperty("User-Agent", "HaikuWallpaper")
            c.setRequestProperty("X-Title", "Haiku Wallpaper")
            c.outputStream.use { it.write(payload.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 429 || code >= 500) throw Transient("OpenRouter HTTP $code: ${errorText(text)}", code == 429)
            if (code !in 200..299) throw GenerateException("OpenRouter HTTP $code: ${errorText(text)}")
            return content(text)
        } catch (e: IOException) {
            throw Transient("OpenRouter network error: ${e.message ?: e.javaClass.simpleName}", false, e)
        } finally {
            c.disconnect()
        }
    }

    private fun errorText(body: String): String =
        runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: body.take(300).ifBlank { "(no body)" }

    private fun content(body: String): String {
        val o = runCatching { JSONObject(body) }.getOrElse { throw GenerateException("OpenRouter sent non-JSON: ${body.take(200)}") }
        o.optJSONObject("error")?.let { throw GenerateException("OpenRouter error: ${it.optString("message", it.toString())}") }
        val choice = o.optJSONArray("choices")?.optJSONObject(0)
            ?: throw GenerateException("OpenRouter reply has no choices: ${body.take(200)}")
        val msg = choice.optJSONObject("message")
        val text = if (msg == null || msg.isNull("content")) "" else msg.optString("content") // optString(null) would be "null"
        if (text.isBlank()) throw GenerateException("OpenRouter returned empty content (finish_reason=${choice.optString("finish_reason")})")
        return text
    }
}
