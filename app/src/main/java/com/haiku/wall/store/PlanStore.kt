package com.haiku.wall.store

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import com.haiku.wall.render.HandFont
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** The only link between the main and `:wallpaper` processes: files under filesDir. */
object PlanStore {
    private const val KEEP = 30

    /** Shown when there is no current plan and no fallback asset, so the wallpaper never crashes. */
    internal const val MINIMAL_PLAN = """{"v":1,"w":1080,"h":2400,"total":4000,"ground":"#F3F0E8","inks":["#2B2F3A"],
"medium":{"tooth":0.45,"grain":0.25,"speckle":0.05,"blend":"normal"},
"haiku":["blank paper waits","the pen has not yet decided","a breath, then the ink"],
"text":{"y":0.5,"size":0.07,"ink":0,"halo":true},
"marks":[{"k":"s","ink":0,"a":0.85,"t0":0,"t1":1200,"p":[220,1500,3, 420,1488,4, 640,1496,4, 860,1484,3]},
{"k":"t","t0":1200,"t1":4000}]}"""

    private fun dir(ctx: Context): File {
        HandFont.init(ctx.applicationContext ?: ctx) // every renderer caller goes through here first
        return File(ctx.filesDir, "plans").apply { mkdirs() }
    }

    private fun current(ctx: Context) = File(dir(ctx), "current.json")

    fun save(ctx: Context, planJson: String) {
        val d = dir(ctx)
        val tmp = File(d, "current.json.tmp")
        FileOutputStream(tmp).use { it.write(planJson.toByteArray()); it.fd.sync() }
        if (!tmp.renameTo(File(d, "current.json"))) throw IOException("rename to current.json failed")
        val h = File(d, "history").apply { mkdirs() }
        File(h, "${System.currentTimeMillis()}.json").writeText(planJson)
        history(ctx).drop(KEEP).forEach { it.delete() }
    }

    /** Make an existing page the current one without adding a duplicate history entry. */
    fun restore(ctx: Context, planJson: String) {
        val d = dir(ctx)
        val tmp = File(d, "current.json.tmp")
        FileOutputStream(tmp).use { it.write(planJson.toByteArray()); it.fd.sync() }
        if (!tmp.renameTo(File(d, "current.json"))) throw IOException("rename to current.json failed")
    }

    fun currentJson(ctx: Context): String {
        runCatching { current(ctx).takeIf { it.isFile }?.readText() }.getOrNull()?.let { return it }
        return runCatching {
            val names = ctx.assets.list("fallback")?.filter { it.endsWith(".json") }.orEmpty()
            ctx.assets.open("fallback/" + names.random()).bufferedReader().use { it.readText() }
        }.getOrDefault(MINIMAL_PLAN)
    }

    /** 0 when there is no current.json. */
    fun currentMtime(ctx: Context): Long = current(ctx).lastModified()

    /** Newest first. */
    fun history(ctx: Context): List<File> =
        File(dir(ctx), "history").listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .sortedByDescending { it.name.removeSuffix(".json").toLongOrNull() ?: 0L }

    // On by default: the marker file means "off", so a fresh install replays when you come back to the home screen.
    private fun replayOffFlag(ctx: Context) = File(ctx.filesDir, "replay_off")

    fun setReplayOnUnlock(ctx: Context, on: Boolean) {
        val f = replayOffFlag(ctx)
        if (on) f.delete() else f.createNewFile()
    }

    fun replayOnUnlock(ctx: Context): Boolean = !replayOffFlag(ctx).exists()

    /** First line of the haiku of the newest [n] history plans; reads only the "haiku" key. */
    fun recentHaiku(ctx: Context, n: Int): List<String> = history(ctx).take(n).mapNotNull { f ->
        runCatching {
            JsonReader(f.bufferedReader()).use { r ->
                var first: String? = null
                r.beginObject()
                while (r.hasNext() && first == null) {
                    if (r.nextName() != "haiku") { r.skipValue(); continue }
                    r.beginArray()
                    if (r.hasNext() && r.peek() == JsonToken.STRING) first = r.nextString()
                    break
                }
                first
            }
        }.getOrNull()
    }
}
