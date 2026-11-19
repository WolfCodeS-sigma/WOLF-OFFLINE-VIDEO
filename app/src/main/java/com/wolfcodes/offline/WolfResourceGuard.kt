package com.wolfcodes.offline

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.File

/** Conservative preflight and scene-boundary checks for mid-range Android phones. */
object WolfResourceGuard {
    data class Snapshot(
        val availableMemoryMb: Long,
        val lowMemory: Boolean,
        val thermalStatus: Int,
        val freeStorageBytes: Long
    )

    fun snapshot(context: Context, directory: File): Snapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            power.currentThermalStatus
        } else {
            0
        }
        return Snapshot(
            availableMemoryMb = memory.availMem / (1024L * 1024L),
            lowMemory = memory.lowMemory,
            thermalStatus = thermal,
            freeStorageBytes = directory.usableSpace
        )
    }

    fun ensureSafeToStart(context: Context, directory: File, estimatedOutputBytes: Long) {
        val current = snapshot(context, directory)
        if (current.lowMemory || current.availableMemoryMb < 160L) {
            throw IllegalStateException(
                "ذاكرة الهاتف المتاحة منخفضة (" + current.availableMemoryMb + " MB). أغلق التطبيقات الأخرى ثم أعد المحاولة."
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            current.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        ) {
            throw IllegalStateException("حرارة الهاتف مرتفعة. أوقف التصدير واترك الهاتف يبرد قبل المحاولة.")
        }
        if (current.freeStorageBytes < estimatedOutputBytes) {
            throw IllegalStateException("المساحة الحرة غير كافية لتصدير فيديو بهذه المدة. وفر مساحة إضافية ثم حاول.")
        }
    }

    /** Called between scenes so a long export can stop before the device overheats. */
    fun ensureSafeToContinue(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        if (memory.lowMemory || memory.availMem < 120L * 1024L * 1024L) {
            throw IllegalStateException("الذاكرة المتاحة أصبحت منخفضة أثناء التصدير؛ أوقفنا العملية لحماية الهاتف.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
                throw IllegalStateException("ارتفعت حرارة الهاتف أثناء التصدير؛ أوقفنا العملية لحمايته.")
            }
        }
    }
}
