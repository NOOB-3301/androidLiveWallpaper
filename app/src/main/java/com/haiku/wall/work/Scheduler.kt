package com.haiku.wall.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Scheduler {
    const val PERIODIC = "haiku-periodic"
    const val NOW = "haiku-now"

    private fun online() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)

    /** 0 cancels; otherwise every [intervalHours] hours (>= Android's 15 min floor). UPDATE keeps the current schedule slot. */
    fun apply(ctx: Context, intervalHours: Int) {
        val wm = WorkManager.getInstance(ctx)
        if (intervalHours <= 0) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val req = PeriodicWorkRequestBuilder<GenerateWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(online().setRequiresBatteryNotLow(true).build())
            .addTag(PERIODIC)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** Expedited when quota allows. Expedited work only accepts network/storage constraints, so no battery check here. */
    fun generateNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<GenerateWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(online().build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
    }
}
