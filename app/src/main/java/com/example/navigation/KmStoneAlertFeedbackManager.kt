package com.example.navigation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.model.KmAlertSoundOption
import com.example.model.KmStoneAlertSettings
import com.example.model.KmVibrationIntensity
import com.example.model.KmVibrationPattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

data class KmStoneAlertDispatchRecord(
    val eventKind: KmStoneAlertKind,
    val stoneChainage: String,
    val directionSide: String,
    val vibrationTriggered: Boolean,
    val soundTriggered: Boolean,
    val vibrationPattern: KmVibrationPattern,
    val soundOption: KmAlertSoundOption,
    val timestampMs: Long
)

/**
 * Handles vibration, haptic feedback, and synthesized/custom sound playback for KM Stone Arrival
 * and Advance Warning events.
 *
 * Respects:
 * - Device silent / vibrate ringer mode & Do Not Disturb filter
 * - Active Camera video recording or Survey recording (never interrupts recording)
 * - Independent arrival vs. advance-warning sound & vibration controls
 * - Non-overlapping playback jobs
 */
object KmStoneAlertFeedbackManager {

    private val feedbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeSoundJob: Job? = null

    private val _lastDispatchRecord = MutableStateFlow<KmStoneAlertDispatchRecord?>(null)
    val lastDispatchRecord: StateFlow<KmStoneAlertDispatchRecord?> = _lastDispatchRecord.asStateFlow()

    private const val ALERT_CHANNEL_ID = "ctecs_km_stone_arrival_channel"
    private const val ALERT_NOTIFICATION_ID = 4109

    fun dispatchAlert(
        context: Context,
        event: KmStoneAlertEvent,
        settings: KmStoneAlertSettings,
        isRecordingActive: Boolean = false,
        hostView: View? = null
    ): KmStoneAlertDispatchRecord {
        val isAdvance = event.kind == KmStoneAlertKind.ADVANCE_WARNING

        val shouldVibrate = if (isAdvance) {
            settings.enabled && settings.vibrationEnabled && settings.advanceWarningVibrationEnabled && !isRecordingActive
        } else {
            settings.enabled && settings.vibrationEnabled && !isRecordingActive
        }

        val shouldPlaySound = if (isAdvance) {
            settings.enabled && settings.soundEnabled && settings.advanceWarningSoundEnabled && !isRecordingActive &&
                canPlayAudibleAlert(context)
        } else {
            settings.enabled && settings.soundEnabled && !isRecordingActive && canPlayAudibleAlert(context)
        }

        val chosenPattern = if (isAdvance) {
            settings.advanceWarningVibrationPattern
        } else {
            settings.vibrationPattern
        }

        val chosenSound = if (isAdvance) {
            settings.advanceWarningSoundOption
        } else {
            settings.soundOption
        }

        if (settings.hapticFeedbackEnabled && !isRecordingActive) {
            try {
                hostView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } catch (_: Exception) {}
        }

        var didVibrate = false
        if (shouldVibrate) {
            val timings = settings.resolveVibrationTimings(chosenPattern)
            didVibrate = triggerVibrationWaveform(
                context = context,
                timings = timings,
                intensity = settings.vibrationIntensity
            )
        }

        var didSound = false
        if (shouldPlaySound) {
            didSound = triggerAlertSound(
                context = context,
                soundOption = chosenSound,
                customSoundUri = settings.customSoundUri,
                volumePercent = settings.alertVolumePercent
            )
        }

        if (!isRecordingActive) {
            postHeadsUpBannerIfPermitted(context, event)
        }

        val record = KmStoneAlertDispatchRecord(
            eventKind = event.kind,
            stoneChainage = event.stoneFormattedChainage,
            directionSide = event.directionSide,
            vibrationTriggered = didVibrate,
            soundTriggered = didSound,
            vibrationPattern = chosenPattern,
            soundOption = chosenSound,
            timestampMs = event.timestampMs
        )
        _lastDispatchRecord.value = record
        return record
    }

    fun testVibration(
        context: Context,
        settings: KmStoneAlertSettings,
        patternOverride: KmVibrationPattern? = null
    ): Boolean {
        val pattern = patternOverride ?: settings.vibrationPattern
        val timings = settings.resolveVibrationTimings(pattern)
        val ok = triggerVibrationWaveform(
            context = context,
            timings = timings,
            intensity = settings.vibrationIntensity
        )
        _lastDispatchRecord.value = KmStoneAlertDispatchRecord(
            eventKind = KmStoneAlertKind.ARRIVAL,
            stoneChainage = "TEST",
            directionSide = "LHS",
            vibrationTriggered = ok,
            soundTriggered = false,
            vibrationPattern = pattern,
            soundOption = settings.soundOption,
            timestampMs = System.currentTimeMillis()
        )
        return ok
    }

    fun testSound(
        context: Context,
        settings: KmStoneAlertSettings,
        soundOverride: KmAlertSoundOption? = null
    ): Boolean {
        val option = soundOverride ?: settings.soundOption
        val ok = triggerAlertSound(
            context = context,
            soundOption = option,
            customSoundUri = settings.customSoundUri,
            volumePercent = settings.alertVolumePercent,
            bypassSilentCheckForPreview = false
        )
        _lastDispatchRecord.value = KmStoneAlertDispatchRecord(
            eventKind = KmStoneAlertKind.ARRIVAL,
            stoneChainage = "TEST",
            directionSide = "LHS",
            vibrationTriggered = false,
            soundTriggered = ok,
            vibrationPattern = settings.vibrationPattern,
            soundOption = option,
            timestampMs = System.currentTimeMillis()
        )
        return ok
    }

    fun canPlayAudibleAlert(context: Context): Boolean {
        return try {
            val audioMgr = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val ringerOk = audioMgr == null || audioMgr.ringerMode == AudioManager.RINGER_MODE_NORMAL
            val notifMgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val dndFilter = notifMgr?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            val dndOk = dndFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
                dndFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ringerOk && dndOk
        } catch (_: Exception) {
            true
        }
    }

    @Suppress("DEPRECATION")
    fun triggerVibrationWaveform(
        context: Context,
        timings: LongArray,
        intensity: KmVibrationIntensity
    ): Boolean {
        if (timings.isEmpty()) return false
        return try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return false

            val safeTimings = timings.mapIndexed { index, ms ->
                if (index % 2 == 0) ms.coerceIn(0L, 400L) else ms.coerceIn(25L, 600L)
            }.toLongArray()

            val amplitudes = IntArray(safeTimings.size) { index ->
                if (index % 2 == 0) 0 else intensity.amplitude.coerceIn(1, 255)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.cancel()
                val effect = VibrationEffect.createWaveform(safeTimings, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                vibrator.cancel()
                vibrator.vibrate(safeTimings, -1)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun triggerAlertSound(
        context: Context,
        soundOption: KmAlertSoundOption,
        customSoundUri: String,
        volumePercent: Int,
        bypassSilentCheckForPreview: Boolean = false
    ): Boolean {
        if (!bypassSilentCheckForPreview && !canPlayAudibleAlert(context)) {
            return false
        }
        val gain = (volumePercent.coerceIn(0, 100) / 100f).coerceIn(0f, 1f)
        if (gain <= 0.01f) return false

        activeSoundJob?.cancel()
        activeSoundJob = feedbackScope.launch {
            try {
                if (soundOption == KmAlertSoundOption.CUSTOM_SOUND && customSoundUri.isNotBlank()) {
                    val uri = Uri.parse(customSoundUri)
                    val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                    if (ringtone != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ringtone.volume = gain
                        }
                        ringtone.play()
                        return@launch
                    }
                }
                playSynthesizedTone(soundOption, gain)
            } catch (_: Exception) {}
        }
        return true
    }

    private fun playSynthesizedTone(option: KmAlertSoundOption, gain: Float) {
        val sampleRate = 24000
        val segments: List<Pair<Double, Int>> = when (option) {
            KmAlertSoundOption.DEFAULT_BEEP -> listOf(880.0 to 150)
            KmAlertSoundOption.DOUBLE_BEEP -> listOf(960.0 to 110, 0.0 to 65, 1180.0 to 130)
            KmAlertSoundOption.DIGITAL_CHIME -> listOf(784.0 to 105, 1046.5 to 115, 1318.5 to 170)
            KmAlertSoundOption.SOFT_ALERT -> listOf(587.3 to 140, 0.0 to 45, 784.0 to 160)
            KmAlertSoundOption.CUSTOM_SOUND -> listOf(880.0 to 150)
        }
        val totalSamples = segments.sumOf { (_, ms) -> (sampleRate * ms) / 1000 }
        if (totalSamples <= 0) return

        val pcm = ShortArray(totalSamples)
        var cursor = 0
        for ((freqHz, durationMs) in segments) {
            val count = (sampleRate * durationMs) / 1000
            for (i in 0 until count) {
                if (cursor >= totalSamples) break
                if (freqHz <= 1.0) {
                    pcm[cursor++] = 0
                } else {
                    val env = when {
                        i < count / 8 -> i.toDouble() / (count / 8.0).coerceAtLeast(1.0)
                        i > (count * 7) / 8 -> (count - i).toDouble() / (count / 8.0).coerceAtLeast(1.0)
                        else -> 1.0
                    }
                    val sample = sin(2.0 * PI * freqHz * i / sampleRate) * env * gain
                    pcm[cursor++] = (sample * Short.MAX_VALUE * 0.75).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            val totalDurationMs = segments.sumOf { it.second }.toLong()
            Thread.sleep((totalDurationMs + 60L).coerceAtMost(1200L))
        } catch (_: Exception) {
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {}
            try {
                track.release()
            } catch (_: Exception) {}
        }
    }

    private fun postHeadsUpBannerIfPermitted(context: Context, event: KmStoneAlertEvent) {
        if (!LockScreenNavigationService.hasNotificationPermission(context)) return
        try {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                mgr.getNotificationChannel(ALERT_CHANNEL_ID) == null
            ) {
                val ch = NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "KM Stone Arrival Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts when reaching or approaching a verified highway KM Stone."
                    setSound(null, null)
                    enableVibration(false)
                }
                mgr.createNotificationChannel(ch)
            }
            val title = if (event.kind == KmStoneAlertKind.ARRIVAL) {
                "KM Stone Reached: ${event.stoneFormattedChainage}"
            } else {
                "Approaching KM ${event.stoneFormattedChainage} (${event.distanceMetres} m)"
            }
            val body = "CH ${event.currentFormattedChainage} | ${event.directionSide}"
            val notif = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_ctecs_nav)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setTimeoutAfter(8_000L)
                .build()
            mgr.notify(ALERT_NOTIFICATION_ID, notif)
        } catch (_: Exception) {}
    }
}
