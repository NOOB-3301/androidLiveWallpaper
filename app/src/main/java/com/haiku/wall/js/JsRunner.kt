package com.haiku.wall.js

import android.content.Context
import androidx.javascriptengine.EvaluationFailedException
import androidx.javascriptengine.IsolateStartupParameters
import androidx.javascriptengine.IsolateTerminatedException
import androidx.javascriptengine.JavaScriptIsolate
import androidx.javascriptengine.JavaScriptSandbox
import androidx.javascriptengine.MemoryLimitExceededException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A drawing program failed. [message] is the kit's or V8's own text, fit to paste into a repair prompt. */
class JsError(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Runs assets/kit/kit.js + an LLM drawing program in a fresh androidx.javascriptengine isolate and returns the plan
 * JSON (docs/CONTRACT.md). Main process only. Call [init] once (App.onCreate or Pipeline.generate) before [run].
 * tools/run-program.mjs runs the same script in node; keep [compose] identical to its compose().
 */
object JsRunner {
    private const val TIMEOUT_MS = 10_000L
    private const val HEAP_BYTES = 128L * 1024 * 1024
    // The plan is read back in slices: without JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT every result crosses
    // binder (~1 MB), and a full plan can be ~2 MB. kit.js escapes non-ASCII, so a slice never splits a character.
    private const val SLICE = 200_000

    @Volatile private var app: Context? = null
    @Volatile private var kit: String? = null

    /** Stores the application context (no JS work, safe in any process). */
    fun init(context: Context) {
        app = context.applicationContext
    }

    fun compose(input: String, kit: String, program: String): String =
        "globalThis.__input = $input;\n${kit.trimEnd()}\n;try{(function(){\n$program\n})();}catch(e){__fail(e)}\n__finish()"

    suspend fun run(input: String, program: String): String = withContext(Dispatchers.IO) {
        val ctx = app ?: throw JsError("JsRunner.init(context) was not called")
        if (!JavaScriptSandbox.isSupported()) throw JsError("JS sandbox not supported on this device")
        val kitJs = kit ?: ctx.assets.open("kit/kit.js").bufferedReader().use { it.readText() }.also { kit = it }
        val sandbox = try {
            JavaScriptSandbox.createConnectedInstanceAsync(ctx).await()
        } catch (e: Exception) {
            throw JsError("JS sandbox failed to start: ${e.message}", e)
        }
        var isolate: JavaScriptIsolate? = null
        try {
            isolate = if (sandbox.isFeatureSupported(JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE)) {
                sandbox.createIsolate(IsolateStartupParameters().apply { setMaxHeapSizeBytes(HEAP_BYTES) })
            } else {
                sandbox.createIsolate()
            }
            val iso = isolate
            withTimeoutOrNull(TIMEOUT_MS) {
                // __finish() caches its result, so the slices below re-read the same string
                val len = iso.evaluateJavaScriptAsync(compose(input, kitJs, program) + ".length.toString()").await().trim().toInt()
                val out = StringBuilder(len)
                var i = 0
                while (i < len) {
                    out.append(iso.evaluateJavaScriptAsync("__finish().substring($i, ${i + SLICE})").await())
                    i += SLICE
                }
                out.toString()
            } ?: throw JsError("the program ran longer than ${TIMEOUT_MS / 1000} s (an endless loop, or far too many marks); simplify it")
        } catch (e: JsError) {
            throw e
        } catch (e: MemoryLimitExceededException) {
            throw JsError("the program ran out of memory (128 MB); build fewer or smaller arrays and draw fewer marks", e)
        } catch (e: EvaluationFailedException) {
            throw JsError(clean(e.message), e)
        } catch (e: IsolateTerminatedException) {
            throw JsError("the JS isolate stopped: ${e.message}", e)
        } catch (e: NumberFormatException) {
            throw JsError("__finish() did not return a plan string", e)
        } finally {
            isolate?.close() // also stops an evaluation that is still running after a timeout
            sandbox.close()
        }
    }

    // "Uncaught Error: program line 3: ..." -> "program line 3: ..."
    private fun clean(msg: String?): String =
        (msg ?: "JavaScript error").trim().removePrefix("Uncaught ").removePrefix("Error: ").ifBlank { "JavaScript error" }
}
