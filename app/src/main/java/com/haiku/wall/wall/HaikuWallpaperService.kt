package com.haiku.wall.wall

import android.app.WallpaperColors
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import com.haiku.wall.render.HandFont
import com.haiku.wall.render.Plan
import com.haiku.wall.render.PlanRenderer
import com.haiku.wall.render.Player
import com.haiku.wall.store.PlanStore

/**
 * Runs in `:wallpaper`. Plays the current plan once (30 fps cap, dirty rects only), then draws nothing.
 * After the last frame the drawing bitmap is freed; the final frame is re-rendered only when the surface needs it.
 * All engine callbacks and frames run on the main looper, so there is no locking.
 */
class HaikuWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = HaikuEngine()

    private inner class HaikuEngine : Engine(), Choreographer.FrameCallback {
        private var plan: Plan? = null
        private var planMtime = Long.MIN_VALUE
        private var player: Player? = null
        private var w = 0
        private var h = 0
        private var visible = false
        private var finished = false
        private var needFull = true    // next post must cover the whole surface
        private var scheduled = false
        private var elapsedNs = 0L     // playback clock, paused while hidden
        private var lastFrameNs = 0L   // 0 = clock paused
        private var lastDrawNs = 0L
        private var hiddenAt = 0L      // elapsedRealtime of the last hide; 0 = never hidden, counts as away
        private val state = getSharedPreferences("wall_state", MODE_PRIVATE) // only this process touches it

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            HandFont.init(applicationContext)
            setOffsetNotificationsEnabled(false)
            finished = !loadIfChanged() // an already played plan starts as the finished picture
        }

        override fun onDestroy() {
            stop()
            releasePlayer()
            super.onDestroy()
        }

        override fun onComputeColors(): WallpaperColors? {
            val p = plan ?: return null
            return WallpaperColors(Color.valueOf(p.ground), Color.valueOf(p.inks[0]), null)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width != w || height != h) {
                w = width; h = height
                releasePlayer() // recreated at the new size and caught up to the clock on the next draw
            }
            needFull = true
            kick() // deferred one frame: on surface creation onSurfaceRedrawNeeded follows and draws synchronously
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            needFull = true
            if (visible && w > 0 && h > 0) { if (finished) drawFinal() else render() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stop()
            releasePlayer()
            needFull = true
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            if (!v) { stop(); hiddenAt = SystemClock.elapsedRealtime(); return }
            val unplayed = loadIfChanged()
            // Replay only a finished drawing after real time away, so a quick hide/show flap can never restart it.
            val away = SystemClock.elapsedRealtime() - hiddenAt >= REPLAY_AWAY_MS
            val replay = finished && away && PlanStore.replayOnUnlock(this@HaikuWallpaperService)
            Log.i(TAG, "visible: unplayed=$unplayed finished=$finished away=$away replay=$replay")
            if (unplayed || replay) restart()
            if (!finished && getSystemService(PowerManager::class.java)?.isPowerSaveMode == true) {
                markPlayed() // battery saver: skip the animation, show the finished picture
                releasePlayer()
                needFull = true
            }
            kick()
        }

        /** Reloads the plan when current.json changed (or on first use). True if the loaded plan was never played to the end. */
        private fun loadIfChanged(): Boolean {
            val ctx = this@HaikuWallpaperService
            val m = PlanStore.currentMtime(ctx)
            val old = plan
            if (old != null && m == planMtime) return false
            planMtime = m
            plan = runCatching { Plan.parse(PlanStore.currentJson(ctx)) }
                .onFailure { Log.w(TAG, "bad plan, using the minimal one", it) }
                .getOrElse { Plan.parse(PlanStore.MINIMAL_PLAN) }
            if (old != null) notifyColorsChanged()
            return state.getLong(PLAYED, -1L) != m
        }

        /** Remembered across engine and process restarts, so a new engine does not replay a plan that already played. */
        private fun markPlayed() {
            finished = true
            state.edit().putLong(PLAYED, planMtime).apply()
        }

        private fun restart() {
            releasePlayer()
            elapsedNs = 0L
            lastFrameNs = 0L
            finished = false
            needFull = true
        }

        private fun kick() {
            if (!visible || w == 0 || h == 0 || scheduled || (finished && !needFull)) return
            scheduled = true
            Choreographer.getInstance().postFrameCallback(this)
        }

        private fun stop() {
            if (scheduled) Choreographer.getInstance().removeFrameCallback(this)
            scheduled = false
            lastFrameNs = 0L // pause the clock; hidden time is not counted
        }

        override fun doFrame(frameTimeNanos: Long) {
            scheduled = false
            if (!visible) return
            if (finished) { if (needFull) drawFinal(); return }
            if (lastFrameNs != 0L) elapsedNs += frameTimeNanos - lastFrameNs
            lastFrameNs = frameTimeNanos
            // 30 fps cap: skip vsyncs closer than ~30 ms to the last drawn one (the clock keeps running)
            if (needFull || frameTimeNanos - lastDrawNs >= 30_000_000L) {
                lastDrawNs = frameTimeNanos
                render()
            }
            if (!finished) kick()
        }

        /** Advance the player to the clock and post what changed (everything if needFull). */
        private fun render() {
            val p = player ?: try {
                Player(plan ?: return, w, h).also { player = it }
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "no memory for the drawing bitmap", e)
                return
            }
            val dirty = p.advance(elapsedNs / 1_000_000L)
            val r = if (needFull) Rect(0, 0, w, h) else dirty
            if (r != null) needFull = !post(p.bitmap(), r)
            if (p.finished) {
                markPlayed()
                releasePlayer()
            }
        }

        /** Draw the final frame once from scratch, then free it again. */
        // shortcut: renders on the main thread (100s of ms for a 6000-mark plan), move to a HandlerThread if it janks
        private fun drawFinal() {
            val p = plan ?: return
            var bmp: Bitmap? = null
            try {
                bmp = PlanRenderer.renderFinal(p, w, h)
                if (post(bmp, Rect(0, 0, w, h))) needFull = false
            } catch (e: Throwable) { // OOM or a bad plan must not kill the wallpaper process
                Log.w(TAG, "final frame failed", e)
            } finally {
                bmp?.recycle()
            }
        }

        /** Copy [r] of [bmp] to the surface. False if the surface could not be locked or posted. */
        private fun post(bmp: Bitmap, r: Rect): Boolean {
            val holder = surfaceHolder
            return try {
                // lockCanvas may grow r to the real dirty area; the bitmap holds the whole picture, so copying r is right
                val c = holder.lockCanvas(r) ?: return false
                try { c.drawBitmap(bmp, r, r, null) } finally { holder.unlockCanvasAndPost(c) }
                true
            } catch (e: Exception) {
                Log.w(TAG, "surface post failed", e)
                false
            }
        }

        private fun releasePlayer() {
            player?.release()
            player = null
        }
    }

    private companion object {
        const val TAG = "HaikuWall"
        const val PLAYED = "playedMtime"
        const val REPLAY_AWAY_MS = 3_000L
    }
}
