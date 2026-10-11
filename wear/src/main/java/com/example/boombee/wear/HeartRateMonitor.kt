package com.example.boombee.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.startExercise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "boombeelogs"

/**
 * Live heart rate for a session, via Health Services' ExerciseClient
 * (exercise type BOXING). Unlike a plain sensor listener, an exercise keeps
 * the optical sensor running with the screen off; [BatchingMode.HEART_RATE_5_SECONDS]
 * asks for screen-off updates at least every 5s so the coach can still adapt.
 *
 * Only one app can own an exercise at a time — if Samsung Health is already
 * tracking a workout, starting ours ends theirs, so we only start when the
 * user turned heart rate on and granted the permission.
 */
class HeartRateMonitor(private val context: Context, private val scope: CoroutineScope) {

    private val client = HealthServices.getClient(context).exerciseClient
    private var running = false

    private val _bpm = MutableStateFlow<Int?>(null)
    /** Latest reading, or null if none arrived in the last [STALE_MS]. */
    val bpm: StateFlow<Int?> = _bpm
    private var lastReadingAt = 0L

    private val callback = object : ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val latest = update.latestMetrics.getData(DataType.HEART_RATE_BPM).lastOrNull() ?: return
            val value = latest.value.toInt()
            if (value in 30..240) {
                _bpm.value = value
                lastReadingAt = SystemClock.elapsedRealtime()
            }
        }

        override fun onRegistered() = Unit
        override fun onRegistrationFailed(throwable: Throwable) {
            Log.w(TAG, "HeartRateMonitor: callback registration failed", throwable)
        }
        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit
        override fun onAvailabilityChanged(dataType: androidx.health.services.client.data.DataType<*, *>, availability: Availability) = Unit
    }

    /** Called once a second by the session tick; clears a reading that's gone stale (watch off the wrist). */
    fun currentBpm(): Int? {
        if (_bpm.value != null && SystemClock.elapsedRealtime() - lastReadingAt > STALE_MS) _bpm.value = null
        return _bpm.value
    }

    fun start() {
        if (running || !hasPermission(context)) return
        running = true
        _bpm.value = null
        scope.launch {
            try {
                val capabilities = client.getCapabilities()
                val boxing = if (ExerciseType.BOXING in capabilities.supportedExerciseTypes) ExerciseType.BOXING else ExerciseType.WORKOUT
                val typeCaps = capabilities.getExerciseTypeCapabilities(boxing)
                if (DataType.HEART_RATE_BPM !in typeCaps.supportedDataTypes) {
                    Log.w(TAG, "HeartRateMonitor: heart rate not supported for $boxing")
                    running = false
                    return@launch
                }
                val batching = if (BatchingMode.HEART_RATE_5_SECONDS in capabilities.supportedBatchingModeOverrides) {
                    setOf(BatchingMode.HEART_RATE_5_SECONDS)
                } else {
                    emptySet()
                }
                client.setUpdateCallback(callback)
                client.startExercise(
                    ExerciseConfig.Builder(boxing)
                        .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                        .setIsAutoPauseAndResumeEnabled(false)
                        .setIsGpsEnabled(false)
                        .setBatchingModeOverrides(batching)
                        .build(),
                )
                Log.d(TAG, "HeartRateMonitor: started ($boxing, batching=$batching)")
            } catch (e: Exception) {
                Log.w(TAG, "HeartRateMonitor: start failed", e)
                running = false
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        _bpm.value = null
        scope.launch {
            try {
                client.endExercise()
            } catch (e: Exception) {
                Log.w(TAG, "HeartRateMonitor: end failed", e)
            }
        }
    }

    companion object {
        private const val STALE_MS = 15_000L

        /** Wear OS 6 (API 36) split BODY_SENSORS into per-type health permissions. */
        val PERMISSION: String =
            if (Build.VERSION.SDK_INT >= 36) "android.permission.health.READ_HEART_RATE" else Manifest.permission.BODY_SENSORS

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
}
