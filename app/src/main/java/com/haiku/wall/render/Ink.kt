package com.haiku.wall.render

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Procedural paper tooth. One seeded field per plan, shared by every ink and by the paper itself, so the grain
 * of every mark lines up like pigment on one real sheet. Tiles repeat and are sampled in screen pixels.
 */
internal object Ink {
    const val T = 256 // shortcut: 256px tile repeats visibly on very flat washes, raise to 512 if a frame shows tiling

    /** Returns [paper, ink0, ink1, ...]. Paper = faint fibre shading to draw once over the ground. */
    fun tiles(plan: Plan): Array<Bitmap> {
        val seed = plan.total.toInt() * 31 + plan.ground
        val n = T * T
        val fine = FloatArray(n)   // paper tooth, ~2-4px, contrast stretched to roughly 0..1
        val coarse = FloatArray(n) // blotchy pressure/paper unevenness, ~16-64px
        for (i in 0 until n) {
            val x = i % T; val y = i / T
            val f = 0.45f * vn(x, y, 2, seed) + 0.35f * vn(x, y, 4, seed + 1) + 0.2f * hash(x, y, seed + 2)
            val c = 0.6f * vn(x, y, 16, seed + 3) + 0.4f * vn(x, y, 64, seed + 4)
            fine[i] = ((f - 0.5f) * 2.6f + 0.5f).coerceIn(0f, 1f)
            coarse[i] = ((c - 0.5f) * 2f + 0.5f).coerceIn(0f, 1f)
        }
        val dark = Color.luminance(plan.ground) < 0.4f
        val fibre = if (dark) 0xFFFFFF else 0x000000
        val px = IntArray(n)
        for (i in 0 until n) {
            val a = 0.055f * (1f - fine[i]) * (1f - fine[i]) + 0.02f * (1f - coarse[i])
            px[i] = (a8(if (dark) a * 0.6f else a) shl 24) or fibre
        }
        val out = ArrayList<Bitmap>(plan.inks.size + 1)
        out += Bitmap.createBitmap(px, T, T, Bitmap.Config.ARGB_8888)
        val alpha = FloatArray(n)
        for (i in 0 until n) {
            // dry media skip the paper valleys first (low tooth), then a few random misses
            val drop = fine[i] < plan.speckle * 0.5f || hash(i % T, i / T, seed + 5) < plan.speckle * 0.05f
            alpha[i] = if (drop) 0f else lerp(1f, fine[i], plan.tooth) * lerp(1f, coarse[i], plan.grain)
        }
        for (ink in plan.inks) {
            val rgb = ink and 0xFFFFFF
            for (i in 0 until n) px[i] = (a8(alpha[i]) shl 24) or rgb
            out += Bitmap.createBitmap(px, T, T, Bitmap.Config.ARGB_8888)
        }
        return out.toTypedArray()
    }

    private fun a8(a: Float) = (a * 255f + 0.5f).toInt().coerceIn(0, 255)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun hash(x: Int, y: Int, s: Int): Float {
        var h = x * 374761393 + y * 668265263 + s * 1442695041
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777216f
    }

    /** Value noise with lattice period T/cell, so the tile wraps seamlessly. */
    private fun vn(x: Int, y: Int, cell: Int, s: Int): Float {
        val p = T / cell
        val fx = (x + 0.5f) / cell; val fy = (y + 0.5f) / cell
        val ix = fx.toInt(); val iy = fy.toInt()
        val tx = smooth(fx - ix); val ty = smooth(fy - iy)
        val x0 = ix % p; val x1 = (ix + 1) % p; val y0 = iy % p; val y1 = (iy + 1) % p
        return lerp(lerp(hash(x0, y0, s), hash(x1, y0, s), tx), lerp(hash(x0, y1, s), hash(x1, y1, s), tx), ty)
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
}
