package com.haiku.wall.gen

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.haiku.wall.Config
import com.haiku.wall.js.JsError
import com.haiku.wall.js.JsRunner
import com.haiku.wall.render.HandFont
import com.haiku.wall.render.Plan
import com.haiku.wall.render.PlanRenderer
import com.haiku.wall.store.PlanStore
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import kotlin.random.Random

class GenerateException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

data class GenResult(val planJson: String, val haiku: List<String>)

/** Direct → Compose → Run (+2 repairs) → Judge (+1 revision). See docs/CONTRACT.md "Pipeline (D)". */
object Pipeline {
    private const val TAG = "Pipeline"

    private class Concept(val json: String, val haiku: List<String>, val image: String, val medium: String, val layout: String)
    private class Drawn(val program: String, val plan: String)

    suspend fun generate(ctx: Context, theme: String, w: Int, h: Int, seconds: Int): GenResult = try {
        steps(ctx, theme, w, h, seconds)
    } catch (e: CancellationException) {
        throw e
    } catch (e: GenerateException) {
        throw e
    } catch (e: Exception) { // e.g. a missing asset or a renderer bug: still surface as GenerateException
        throw GenerateException("${e.javaClass.simpleName}: ${e.message}", e)
    }

    private suspend fun steps(ctx: Context, theme: String, w: Int, h: Int, seconds: Int): GenResult {
        fun asset(path: String) = ctx.assets.open(path).bufferedReader().use { it.readText() }
        JsRunner.init(ctx) // idempotent; only stores the app context
        val now = LocalDateTime.now()
        val recent = PlanStore.recentHaiku(ctx, 12)
        val c = direct(
            fill(
                asset("prompts/direct.md"), mapOf(
                    "theme" to theme, "season" to seasonOf(now.monthValue), "timeOfDay" to timeOfDayOf(now.hour),
                    "width" to "$w", "height" to "$h", "seconds" to "$seconds",
                    "recent" to recent.joinToString("\n") { "- $it" }.ifEmpty { "(none yet)" },
                )
            )
        )
        val examples = ctx.assets.list("examples").orEmpty().filter { it.endsWith(".js") }.map { asset("examples/$it") }
        val system = assembleSystemPrompt(asset("prompts/system.md"), asset("prompts/kit-api.md"), pickExamples(examples, c.medium))
        val input = JSONObject().put("W", w).put("H", h).put("haiku", JSONArray(c.haiku))
            .put("seed", Random.nextInt(1, Int.MAX_VALUE)).put("seconds", seconds).toString()
        val brief = conceptPrompt(c.json, w, h, seconds)

        val first = composeAndRun(system, brief, input, null)
        if (!Config.judgeEnabled()) return GenResult(first.plan, c.haiku)

        // Judge and revision are best-effort: any failure keeps the first valid plan.
        fun judgeTpl() = asset("prompts/judge.md")
        HandFont.init(ctx) // the thumbnail must show the real hand font, legibility is judged on it
        val s1 = bestEffort("judge") { judge(judgeTpl(), c, first.plan) } ?: return GenResult(first.plan, c.haiku)
        Log.i(TAG, "judge: $s1")
        if (!s1.needsRevision) return GenResult(first.plan, c.haiku)
        val best = bestEffort("revision") {
            val second = composeAndRun(system, brief, input, first.program to revisionPrompt(s1))
            val s2 = judge(judgeTpl(), c, second.plan)
            Log.i(TAG, "revision judge: $s2")
            if (s2.beats(s1)) second else first
        } ?: first
        return GenResult(best.plan, c.haiku)
    }

    private suspend fun direct(prompt: String): Concept {
        var last: Exception? = null
        repeat(2) { // a second try for malformed JSON; HTTP retries live in OpenRouter
            try {
                val o = JSONObject(extractJson(OpenRouter.chat(null, listOf("user" to prompt), 800, json = true)))
                val lines = o.getJSONArray("haiku").let { a -> List(a.length()) { a.getString(it).trim() } }
                require(lines.size == 3 && lines.all { it.isNotEmpty() }) { "haiku must be 3 non-empty lines, got $lines" }
                return Concept(o.toString(2), lines, o.optString("image"), o.optString("medium"), o.optString("layout"))
            } catch (e: JSONException) {
                last = e
            } catch (e: IllegalArgumentException) {
                last = e
            }
        }
        throw GenerateException("Direct step returned unusable JSON: ${last?.message}", last)
    }

    /** Compose (optionally continuing from [prior] = previous program + feedback), run, repair up to twice. */
    private suspend fun composeAndRun(system: String, brief: String, input: String, prior: Pair<String, String>?): Drawn {
        val turns = mutableListOf("user" to brief)
        if (prior != null) turns += listOf("assistant" to prior.first, "user" to prior.second)
        var error = ""
        repeat(3) {
            val program = stripFences(OpenRouter.chat(system, turns, 16_000))
            try {
                return Drawn(program, JsRunner.run(input, program))
            } catch (e: JsError) {
                error = e.message ?: "unknown JS error"
                // shortcut: sandbox (not program) failures matched by JsRunner's wording, use a dedicated exception type if B adds one
                if (listOf("not supported", "was not called", "failed to start").any { it in error }) throw GenerateException(error, e)
                Log.w(TAG, "JS error, repairing: $error")
                turns += listOf("assistant" to program, "user" to repairPrompt(error))
            }
        }
        throw GenerateException("JS program still failing after 2 repairs: $error")
    }

    private suspend fun judge(template: String, c: Concept, plan: String): Scores {
        val bmp = PlanRenderer.renderFinal(Plan.parse(plan), 270, 600)
        val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        bmp.recycle()
        val prompt = fill(
            template, mapOf(
                "haiku" to c.haiku.joinToString(" / "), "image" to c.image, "medium" to c.medium, "layout" to c.layout,
            )
        )
        val image = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val o = JSONObject(extractJson(OpenRouter.chat(null, listOf("user" to prompt), 400, json = true, imageDataUrl = image)))
        return Scores(
            o.getDouble("concept"), o.getDouble("cohesion"), o.getDouble("legibility"),
            o.getDouble("balance"), o.getDouble("craft"), o.optString("critique"),
        )
    }

    private inline fun <T> bestEffort(step: String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$step failed, keeping the first plan: ${e.message}")
        null
    }
}
