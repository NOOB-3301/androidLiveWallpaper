package com.haiku.wall.render

import android.graphics.Color
import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader
import kotlin.math.sqrt

/**
 * Plan v1 (see docs/CONTRACT.md "Plan JSON"), held in primitive columns so a 6000-mark / 120k-point plan
 * stays a few MB in the `:wallpaper` process. Marks are sorted by t0.
 */
class Plan private constructor(
    val w: Int,
    val h: Int,
    val total: Long,
    val ground: Int,
    val inks: IntArray,
    val tooth: Float,
    val grain: Float,
    val speckle: Float,
    val multiply: Boolean,
    val haiku: List<String>,
    val textY: Float,
    val textSize: Float,
    val textInk: Int,
    val textHalo: Boolean,
    // per mark columns
    internal val kind: ByteArray,     // K_STROKE | K_AREA | K_TEXT
    internal val ink: ByteArray,
    internal val alpha: FloatArray,
    internal val t0: IntArray,
    internal val t1: IntArray,
    internal val pOff: IntArray,      // stroke: first point index into pts (x,y,w triples)
    internal val pCount: IntArray,    // stroke: number of points
    internal val cx: FloatArray,
    internal val cy: FloatArray,
    internal val r: FloatArray,
    internal val loopFirst: IntArray, // area: first loop index into loopOff/loopLen
    internal val loopCount: IntArray,
    // shared buffers
    internal val pts: FloatArray,
    internal val loopOff: IntArray,
    internal val loopLen: IntArray,   // floats in the loop (2 per point)
    internal val loopPts: FloatArray,
) {
    val markCount: Int get() = kind.size

    private var cumArr: FloatArray? = null
    private var cumDone: BooleanArray? = null

    /** Cumulative arc length per stroke point, indexed by point index (pOff[m] + i). Computed on first use per mark. */
    internal fun cum(m: Int): FloatArray {
        val c = cumArr ?: FloatArray(pts.size / 3).also { cumArr = it }
        val done = cumDone ?: BooleanArray(kind.size).also { cumDone = it }
        if (!done[m]) {
            val o = pOff[m]
            var acc = 0f
            for (i in 0 until pCount[m]) {
                if (i > 0) {
                    val dx = pts[(o + i) * 3] - pts[(o + i - 1) * 3]
                    val dy = pts[(o + i) * 3 + 1] - pts[(o + i - 1) * 3 + 1]
                    acc += sqrt(dx * dx + dy * dy)
                }
                c[o + i] = acc
            }
            done[m] = true
        }
        return c
    }

    companion object {
        internal const val K_STROKE: Byte = 1
        internal const val K_AREA: Byte = 2
        internal const val K_TEXT: Byte = 3

        fun parse(json: String): Plan = JsonReader(StringReader(json)).use { Parser().read(it) }
    }

    private class FBuf(cap: Int = 256) {
        var a = FloatArray(cap); var n = 0
        fun add(v: Float) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
        fun out() = a.copyOf(n)
    }

    private class IBuf(cap: Int = 256) {
        var a = IntArray(cap); var n = 0
        fun add(v: Int) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
        fun out() = a.copyOf(n)
    }

    private class Parser {
        var w = 1080; var h = 2400; var total = 0L
        var ground = Color.parseColor("#F3F0E8"); var inks = intArrayOf(Color.BLACK)
        var tooth = 0f; var grain = 0f; var speckle = 0f; var multiply = false
        var haiku = emptyList<String>()
        var textY = 0.45f; var textSize = 0.07f; var textInk = 0; var textHalo = true
        val kind = IBuf(); val ink = IBuf(); val alpha = FBuf(); val t0 = IBuf(); val t1 = IBuf()
        val pOff = IBuf(); val pCount = IBuf(); val cx = FBuf(); val cy = FBuf(); val r = FBuf()
        val loopFirst = IBuf(); val loopCount = IBuf()
        val pts = FBuf(4096); val loopOff = IBuf(); val loopLen = IBuf(); val loopPts = FBuf(4096)

        fun read(rd: JsonReader): Plan {
            rd.beginObject()
            while (rd.hasNext()) when (rd.nextName()) {
                "w" -> w = num(rd).toInt()
                "h" -> h = num(rd).toInt()
                "total" -> total = num(rd).toLong()
                "ground" -> ground = color(rd.nextString())
                "inks" -> { val l = IBuf(4); rd.beginArray(); while (rd.hasNext()) l.add(color(rd.nextString())); rd.endArray(); if (l.n > 0) inks = l.out() }
                "medium" -> medium(rd)
                "haiku" -> { val l = ArrayList<String>(3); rd.beginArray(); while (rd.hasNext()) l.add(str(rd)); rd.endArray(); haiku = l }
                "text" -> text(rd)
                "marks" -> { rd.beginArray(); while (rd.hasNext()) mark(rd); rd.endArray() }
                else -> rd.skipValue()
            }
            rd.endObject()
            require(w > 0 && h > 0) { "bad plan size ${w}x$h" }
            return build()
        }

        fun medium(rd: JsonReader) {
            rd.beginObject()
            while (rd.hasNext()) when (rd.nextName()) {
                "tooth" -> tooth = num(rd).toFloat().coerceIn(0f, 1f)
                "grain" -> grain = num(rd).toFloat().coerceIn(0f, 1f)
                "speckle" -> speckle = num(rd).toFloat().coerceIn(0f, 1f)
                "blend" -> multiply = str(rd) == "multiply"
                else -> rd.skipValue()
            }
            rd.endObject()
        }

        fun text(rd: JsonReader) {
            if (rd.peek() == JsonToken.NULL) { rd.nextNull(); return }
            rd.beginObject()
            while (rd.hasNext()) when (rd.nextName()) {
                "y" -> textY = num(rd).toFloat()
                "size" -> textSize = num(rd).toFloat()
                "ink" -> textInk = num(rd).toInt()
                "halo" -> textHalo = if (rd.peek() == JsonToken.BOOLEAN) rd.nextBoolean() else { rd.skipValue(); true }
                else -> rd.skipValue()
            }
            rd.endObject()
        }

        fun mark(rd: JsonReader) {
            var k: Byte = 0; var mInk = 0; var a = 1f; var mt0 = 0; var mt1 = 0
            var mcx = 0f; var mcy = 0f; var mr = 0f
            var po = pts.n / 3; var pc = 0; var lf = loopOff.n; var lc = 0
            rd.beginObject()
            while (rd.hasNext()) when (rd.nextName()) {
                "k" -> k = when (str(rd)) { "s" -> K_STROKE; "a" -> K_AREA; "t" -> K_TEXT; else -> 0 }
                "ink" -> mInk = num(rd).toInt()
                "a" -> a = num(rd).toFloat().coerceIn(0f, 1f)
                "t0" -> mt0 = num(rd).toInt()
                "t1" -> mt1 = num(rd).toInt()
                "cx" -> mcx = num(rd).toFloat()
                "cy" -> mcy = num(rd).toFloat()
                "r" -> mr = num(rd).toFloat()
                "p" -> {
                    po = pts.n / 3
                    var c = 0; rd.beginArray(); while (rd.hasNext()) { pts.add(num(rd).toFloat()); c++ }; rd.endArray()
                    val extra = c % 3; repeat(extra) { pts.n-- } // drop a dangling partial triple
                    pc = c / 3
                }
                "loops" -> {
                    lf = loopOff.n; lc = 0
                    rd.beginArray()
                    while (rd.hasNext()) {
                        val start = loopPts.n
                        rd.beginArray(); while (rd.hasNext()) loopPts.add(num(rd).toFloat()); rd.endArray()
                        if ((loopPts.n - start) % 2 == 1) loopPts.n--
                        loopOff.add(start); loopLen.add(loopPts.n - start); lc++
                    }
                    rd.endArray()
                }
                else -> rd.skipValue()
            }
            rd.endObject()
            if (k == 0.toByte()) return // unknown mark kind: ignore (its points stay in the buffer, harmless)
            kind.add(k.toInt()); ink.add(mInk); alpha.add(a); t0.add(mt0); t1.add(maxOf(mt0, mt1))
            pOff.add(po); pCount.add(if (k == K_STROKE) pc else 0)
            cx.add(mcx); cy.add(mcy); r.add(mr)
            loopFirst.add(lf); loopCount.add(if (k == K_AREA) lc else 0)
        }

        fun build(): Plan {
            val n = kind.n
            val nInk = inks.size
            var ord: IntArray? = null
            for (i in 1 until n) if (t0.a[i] < t0.a[i - 1]) { ord = (0 until n).sortedBy { t0.a[it] }.toIntArray(); break }
            fun ints(b: IBuf) = ord?.let { o -> IntArray(n) { b.a[o[it]] } } ?: b.out()
            fun floats(b: FBuf) = ord?.let { o -> FloatArray(n) { b.a[o[it]] } } ?: b.out()
            val kindCol = ints(kind); val inkCol = ints(ink)
            val endMs = (0 until n).maxOfOrNull { t1.a[it] }?.toLong() ?: 0L
            return Plan(
                w, h, if (total > 0) total else endMs, ground, inks, tooth, grain, speckle, multiply,
                haiku, textY, textSize, textInk.coerceIn(0, nInk - 1), textHalo,
                ByteArray(n) { kindCol[it].toByte() },
                ByteArray(n) { inkCol[it].coerceIn(0, nInk - 1).toByte() },
                floats(alpha), ints(t0), ints(t1), ints(pOff), ints(pCount), floats(cx), floats(cy), floats(r),
                ints(loopFirst), ints(loopCount),
                pts.out(), loopOff.out(), loopLen.out(), loopPts.out(),
            )
        }

        fun num(rd: JsonReader): Double =
            if (rd.peek() == JsonToken.NULL) { rd.nextNull(); 0.0 } else rd.nextDouble().let { if (it.isNaN()) 0.0 else it }

        fun str(rd: JsonReader): String =
            if (rd.peek() == JsonToken.NULL) { rd.nextNull(); "" } else rd.nextString()

        fun color(s: String): Int = runCatching { Color.parseColor(s.trim()) }.getOrDefault(Color.BLACK)
    }
}
