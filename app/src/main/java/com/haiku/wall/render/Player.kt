package com.haiku.wall.render

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Incremental CPU renderer (docs/CONTRACT.md "Drawing rules"). Each [advance] paints only the new slice of every
 * mark active since the previous call onto an offscreen bitmap and returns the dirty rect in output pixels.
 * Not thread-safe; one owner thread.
 */
class Player(private val plan: Plan, val outW: Int, val outH: Int) {
    private val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bmp)
    private val scale = max(outW / plan.w.toFloat(), outH / plan.h.toFloat())
    private val dx = (outW - plan.w * scale) / 2f
    private val dy = (outH - plan.h * scale) / 2f
    private val m = Matrix().apply { setScale(scale, scale); postTranslate(dx, dy) }
    private val inv = Matrix().also { m.invert(it) }
    private val tiles = Ink.tiles(plan)
    private val paperShader = BitmapShader(tiles[0], Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(inv) }
    private val inkPaints = Array(plan.inks.size) { i ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(tiles[i + 1], Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply { setLocalMatrix(inv) }
            if (plan.multiply) {
                // PorterDuff.Mode.MULTIPLY is Skia "modulate" (alpha*alpha), it would punch holes; true multiply is API 29.
                if (Build.VERSION.SDK_INT >= 29) blendMode = BlendMode.MULTIPLY
                else xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN) // shortcut: API 28 approximates multiply with darken
            }
        }
    }
    private val drawn = FloatArray(plan.markCount)
    private val active = IntArray(plan.markCount)
    private var nActive = 0
    private var next = 0
    private var started = false
    private val path = Path()
    private val clip = Path()
    private val rf = RectF()
    private val rf2 = RectF()
    private val dirty = Rect()
    // shortcut: frame cuts leave a faint AA line on near-solid translucent strokes (tooth hides it, measured with tools/preview's Skia),
    // upgrade to redrawing active marks over a base layer if a flat marker medium shows it

    var finished = false
        private set

    init {
        canvas.concat(m)
    }

    fun bitmap(): Bitmap = bmp

    /** Free the drawing bitmap. The Player is dead afterwards. */
    fun release() {
        finished = true
        recycleTiles()
        bmp.recycle()
    }

    /**
     * Draw everything due up to [t] ms. Returns the changed area in output pixels, or null when nothing changed.
     * The returned Rect is reused by the next call.
     */
    fun advance(t: Long): Rect? {
        if (finished) return null
        dirty.setEmpty()
        if (!started) {
            started = true
            canvas.drawColor(plan.ground or Color.BLACK)
            canvas.drawPaint(Paint().apply { shader = paperShader })
            dirty.set(0, 0, outW, outH)
        }
        val end = t >= plan.total
        val n = plan.markCount
        while (next < n && (end || plan.t0[next] <= t)) active[nActive++] = next++
        var keep = 0
        for (i in 0 until nActive) {
            val mk = active[i]
            val d = plan.t1[mk] - plan.t0[mk]
            val f = if (end || d <= 0) 1f else ((t - plan.t0[mk]).toFloat() / d).coerceIn(0f, 1f)
            if (f > drawn[mk]) {
                when (plan.kind[mk]) {
                    Plan.K_STROKE -> stroke(mk, drawn[mk], f)
                    Plan.K_AREA -> area(mk, drawn[mk], f)
                    Plan.K_TEXT -> text(drawn[mk], f)
                }
                drawn[mk] = f
            }
            if (f < 1f) active[keep++] = mk
        }
        nActive = keep
        if (next == n && nActive == 0) {
            finished = true
            recycleTiles()
        }
        return if (dirty.intersect(0, 0, outW, outH)) dirty else null
    }

    private fun recycleTiles() = tiles.forEach { if (!it.isRecycled) it.recycle() }

    private fun inkPaint(ink: Int, a: Float) = inkPaints[ink].apply { alpha = (a * 255f).roundToInt() }

    private fun addDirty(r: RectF) {
        m.mapRect(r)
        dirty.union(floor(r.left).toInt() - 2, floor(r.top).toInt() - 2, ceil(r.right).toInt() + 2, ceil(r.bottom).toInt() + 2)
    }

    // ---- stroke: union of per-segment quads (all clockwise, WINDING) + outer bevels + round caps at the true ends.

    private fun stroke(mk: Int, f0: Float, f1: Float) {
        val n = plan.pCount[mk]
        if (n == 0) return
        val o = plan.pOff[mk]
        val p = plan.pts
        val c = plan.cum(mk)
        val len = c[o + n - 1]
        if (len <= 0f && f1 < 1f) return // a dot (stipple) appears whole when its time is up
        val s0 = f0 * len
        val s1 = f1 * len
        path.rewind()
        path.fillType = Path.FillType.WINDING
        for (j in 0 until n - 1) {
            val c0 = c[o + j]
            val c1 = c[o + j + 1]
            if (c0 >= s1) break
            // each interior joint belongs to the one slice whose [s0, s1) holds it
            if (j + 1 < n - 1 && c1 >= s0 && c1 < s1) joint(o, j + 1, c)
            val sl = c1 - c0
            if (c1 <= s0 || sl < 1e-4f) continue
            val u0 = ((s0 - c0) / sl).coerceIn(0f, 1f)
            val u1 = ((s1 - c0) / sl).coerceIn(0f, 1f)
            if (u1 <= u0) continue
            val i0 = (o + j) * 3
            val i1 = i0 + 3
            val ex = p[i1] - p[i0]
            val ey = p[i1 + 1] - p[i0 + 1]
            val nx = -ey / sl
            val ny = ex / sl
            val x0 = p[i0] + ex * u0; val y0 = p[i0 + 1] + ey * u0
            val x1 = p[i0] + ex * u1; val y1 = p[i0 + 1] + ey * u1
            val h0 = (p[i0 + 2] + (p[i1 + 2] - p[i0 + 2]) * u0) * 0.5f
            val h1 = (p[i0 + 2] + (p[i1 + 2] - p[i0 + 2]) * u1) * 0.5f
            // this vertex order has positive signed area in y-down space = Path.Direction.CW
            path.moveTo(x0 - nx * h0, y0 - ny * h0)
            path.lineTo(x1 - nx * h1, y1 - ny * h1)
            path.lineTo(x1 + nx * h1, y1 + ny * h1)
            path.lineTo(x0 + nx * h0, y0 + ny * h0)
            path.close()
        }
        if (f0 <= 0f || len <= 0f) path.addCircle(p[o * 3], p[o * 3 + 1], p[o * 3 + 2] * 0.5f, Path.Direction.CW)
        if (f1 >= 1f) { val e = (o + n - 1) * 3; path.addCircle(p[e], p[e + 1], p[e + 2] * 0.5f, Path.Direction.CW) }
        if (path.isEmpty) return
        canvas.drawPath(path, inkPaint(plan.ink[mk].toInt(), plan.alpha[mk]))
        path.computeBounds(rf, true)
        addDirty(rf)
    }

    /** Fill the gap on the outer side of the bend at point o+v (bevel), or a round join for hairpins. */
    private fun joint(o: Int, v: Int, c: FloatArray) {
        val l0 = c[o + v] - c[o + v - 1]
        val l1 = c[o + v + 1] - c[o + v]
        if (l0 < 1e-4f || l1 < 1e-4f) return
        val p = plan.pts
        val i = (o + v) * 3
        val x = p[i]; val y = p[i + 1]; val h = p[i + 2] * 0.5f
        val d0x = (x - p[i - 3]) / l0; val d0y = (y - p[i - 2]) / l0
        val d1x = (p[i + 3] - x) / l1; val d1y = (p[i + 4] - y) / l1
        val cross = d0x * d1y - d0y * d1x
        val dot = d0x * d1x + d0y * d1y
        if (dot < -0.5f) { path.addCircle(x, y, h, Path.Direction.CW); return }
        if (cross > -1e-3f && cross < 1e-3f) return
        val s = if (cross > 0f) -h else h
        val ax = x - d0y * s; val ay = y + d0x * s
        val bx = x - d1y * s; val by = y + d1x * s
        path.moveTo(x, y)
        if ((ax - x) * (by - y) - (ay - y) * (bx - x) >= 0f) { path.lineTo(ax, ay); path.lineTo(bx, by) }
        else { path.lineTo(bx, by); path.lineTo(ax, ay) }
        path.close()
    }

    // ---- area: even-odd loops clipped to the ring between the old and new reveal radius.

    private fun area(mk: Int, f0: Float, f1: Float) {
        val r = plan.r[mk]
        if (r <= 0f && f1 < 1f) return // no reveal circle: appear whole at the end
        val lc = plan.loopCount[mk]
        if (lc == 0) return
        val lp = plan.loopPts
        // shortcut: loops are re-pathed every slice, cache the Path per active mark if huge washes stutter
        path.rewind()
        path.fillType = Path.FillType.EVEN_ODD
        for (l in plan.loopFirst[mk] until plan.loopFirst[mk] + lc) {
            val off = plan.loopOff[l]
            val len = plan.loopLen[l]
            if (len < 6) continue
            path.moveTo(lp[off], lp[off + 1])
            var k = off + 2
            while (k < off + len) { path.lineTo(lp[k], lp[k + 1]); k += 2 }
            path.close()
        }
        if (path.isEmpty) return
        path.computeBounds(rf, true)
        val cx = plan.cx[mk]; val cy = plan.cy[mk]
        canvas.save()
        if (r > 0f) {
            val r1 = r * f1
            rf2.set(cx - r1, cy - r1, cx + r1, cy + r1)
            if (!rf.intersect(rf2)) { canvas.restore(); return }
            clip.rewind()
            clip.addCircle(cx, cy, r1, Path.Direction.CW)
            canvas.clipPath(clip)
            if (f0 > 0f) {
                clip.rewind()
                clip.addCircle(cx, cy, r * f0, Path.Direction.CW)
                canvas.clipOutPath(clip)
            }
        }
        canvas.drawPath(path, inkPaint(plan.ink[mk].toInt(), plan.alpha[mk]))
        canvas.restore()
        addDirty(rf)
    }

    // ---- text: Patrick Hand, 3 centred lines, halo in ground colour then ink, revealed left to right by x clips.

    private class Line(val path: Path, val x0: Float, val adv: FloatArray, val top: Float, val bottom: Float, val first: Int, val pad: Float) {
        val n get() = adv.size - 1
        fun xAt(c: Float): Float = when {
            c <= 0f -> x0 - pad
            c >= n -> x0 + adv[n] + pad
            else -> { val i = c.toInt(); x0 + adv[i] + (adv[i + 1] - adv[i]) * (c - i) }
        }
    }

    private var lines: Array<Line>? = null
    private var chars = 0
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = plan.ground or Color.BLACK
    }
    // the same paper grain over the halo, so the halo is invisible on bare paper and only hides marks under the letters
    private val haloGrain = Paint(haloPaint).apply { shader = paperShader }

    private fun layoutText(): Array<Line> {
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = HandFont.get(); textSize = 100f }
        val w = plan.w.toFloat()
        val widest = plan.haiku.maxOfOrNull { tp.measureText(it) / 100f } ?: 0f
        val fpx = if (widest > 0f) min(plan.textSize * w, 0.9f * w / widest) else plan.textSize * w
        tp.textSize = fpx
        haloPaint.strokeWidth = 0.35f * fpx
        haloGrain.strokeWidth = 0.35f * fpx
        val fm = tp.fontMetrics
        val lh = 1.25f * fpx
        val pad = 0.25f * fpx
        val count = plan.haiku.size
        var first = 0
        return Array(count) { i ->
            val s = plan.haiku[i]
            val base = plan.textY * plan.h + (i - (count - 1) / 2f) * lh - (fm.ascent + fm.descent) / 2f
            val widths = FloatArray(s.length)
            tp.getTextWidths(s, widths)
            val adv = FloatArray(s.length + 1)
            for (k in s.indices) adv[k + 1] = adv[k] + widths[k]
            val x0 = w / 2f - adv[s.length] / 2f
            val gp = Path()
            tp.getTextPath(s, 0, s.length, x0, base, gp)
            Line(gp, x0, adv, base + fm.top - pad, base + fm.bottom + pad, first, pad).also { first += s.length }
        }.also { chars = first }
    }

    private fun text(f0: Float, f1: Float) {
        val ls = lines ?: layoutText().also { lines = it }
        if (chars == 0) return
        val c0 = f0 * chars
        val c1 = f1 * chars
        val ink = inkPaint(plan.textInk, 1f)
        for (ln in ls) {
            val a = c0 - ln.first
            val b = c1 - ln.first
            if (b <= 0f || a >= ln.n) continue
            // clip edges on whole output pixels so consecutive slices meet without an anti-aliased seam
            val xa = snapX(ln.xAt(a))
            val xb = snapX(ln.xAt(b))
            if (xb <= xa) continue
            canvas.save()
            canvas.clipRect(xa, ln.top, xb, ln.bottom)
            if (plan.textHalo) { canvas.drawPath(ln.path, haloPaint); canvas.drawPath(ln.path, haloGrain) }
            canvas.drawPath(ln.path, ink)
            canvas.restore()
            rf.set(xa, ln.top, xb, ln.bottom)
            addDirty(rf)
        }
    }

    private fun snapX(x: Float) = ((x * scale + dx).roundToInt() - dx) / scale
}

/** The hand font for text marks, loaded once per process by [com.haiku.wall.App]. */
object HandFont {
    @Volatile private var tf: Typeface? = null

    fun init(ctx: Context) {
        if (tf == null) tf = runCatching { Typeface.createFromAsset(ctx.assets, "fonts/hand.ttf") }.getOrNull()
    }

    internal fun get(): Typeface = tf ?: Typeface.DEFAULT
}
