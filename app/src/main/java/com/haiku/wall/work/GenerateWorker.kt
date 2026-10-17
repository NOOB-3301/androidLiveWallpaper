package com.haiku.wall.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.haiku.wall.Prefs
import com.haiku.wall.R
import com.haiku.wall.gen.Pipeline
import com.haiku.wall.store.PlanStore
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class GenerateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val theme = (prefs.categories.shuffled().take(Random.nextInt(1, 3)) + prefs.customTheme.trim())
            .filter { it.isNotEmpty() }
            .joinToString(", ")
            .ifEmpty { "any season, any weather" }
        // shortcut: displayMetrics can exclude system bars, use WindowManager.maximumWindowMetrics if crops look off
        val dm = applicationContext.resources.displayMetrics
        val h = (1080.0 * max(dm.widthPixels, dm.heightPixels) / min(dm.widthPixels, dm.heightPixels))
            .roundToInt().coerceIn(1800, 2800)
        return try {
            val res = Pipeline.generate(applicationContext, theme, 1080, h, prefs.seconds)
            PlanStore.save(applicationContext, res.planJson)
            prefs.lastError = null
            prefs.lastRunMs = System.currentTimeMillis()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // GenerateException normally; anything else is surfaced the same way instead of failing silently
            prefs.lastError = e.message ?: e.javaClass.simpleName
            when {
                Scheduler.PERIODIC !in tags -> Result.failure()
                runAttemptCount < 2 -> Result.retry() // 3 attempts total, then wait for the next period
                else -> Result.success()
            }
        }
    }

    /** Required for expedited work on API < 31, where it runs as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Haiku generation", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Writing a new haiku…")
            .setOngoing(true)
            .build()
        return ForegroundInfo(1, notification)
    }

    private companion object {
        const val CHANNEL = "generate"
    }
}
