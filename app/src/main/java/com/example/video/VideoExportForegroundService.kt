package com.example.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R

/**
 * Lightweight Android ForegroundService (`dataSync`) that keeps short and long video exports
 * running reliably when the user navigates away from Camera/Gallery or rotates the screen.
 */
class VideoExportForegroundService : Service() {

    companion object {
        private const val TAG = "VideoExportFGS"
        private const val CHANNEL_ID = "ctecs_video_export_channel"
        private const val NOTIFICATION_ID = 4108
        private const val RESULT_NOTIFICATION_ID = 4109

        const val ACTION_START = "com.example.video.ACTION_START_EXPORT"
        const val ACTION_CANCEL = "com.example.video.ACTION_CANCEL_EXPORT"
        const val ACTION_STOP = "com.example.video.ACTION_STOP_EXPORT"
        const val EXTRA_FILE_NAME = "extra_file_name"

        fun updateNotification(context: Context, state: VideoExportState) {
            if (!state.isActive) return
            try {
                ensureChannel(context)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                nm.notify(NOTIFICATION_ID, buildProgressNotification(context, state))
            } catch (_: Throwable) {}
        }

        fun finishAndStop(context: Context, finalState: VideoExportState) {
            try {
                ensureChannel(context)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null && (finalState.status == VideoExportStatus.COMPLETED || finalState.status == VideoExportStatus.FAILED)) {
                    val title = if (finalState.status == VideoExportStatus.COMPLETED) {
                        "Video Export Complete (${finalState.method.displayLabel})"
                    } else {
                        "Video Export Failed"
                    }
                    val text = if (finalState.status == VideoExportStatus.COMPLETED) {
                        "${finalState.fileName} • ${finalState.formatElapsedAndEta()}"
                    } else {
                        finalState.errorMessage ?: "Unable to export video."
                    }
                    val resultNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()
                    nm.notify(RESULT_NOTIFICATION_ID, resultNotification)
                }
            } catch (_: Throwable) {}

            try {
                context.stopService(Intent(context, VideoExportForegroundService::class.java))
            } catch (_: Throwable) {}
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Video Export",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Shows real-time progress for CTECS video exports"
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }

        private fun buildProgressNotification(context: Context, state: VideoExportState): Notification {
            val cancelIntent = Intent(context, VideoExportForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            val cancelPending = PendingIntent.getService(
                context,
                1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val indeterminate = state.status == VideoExportStatus.PREPARING || state.progress <= 0.01f
            val subText = if (indeterminate) {
                "Preparing ${state.fileName}..."
            } else {
                "${state.method.displayLabel} • ${state.progressPercent}% • ${state.formatElapsedAndEta()}"
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Exporting Highway Video")
                .setContentText(subText)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(100, state.progressPercent, indeterminate)
                .addAction(0, "Cancel", cancelPending)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                VideoExportManager.cancelActiveExport(this)
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
            else -> {
                try {
                    ensureChannel(this)
                    val state = VideoExportManager.exportState.value.let {
                        if (it.fileName.isBlank()) {
                            it.copy(fileName = intent?.getStringExtra(EXTRA_FILE_NAME).orEmpty())
                        } else it
                    }
                    val notification = buildProgressNotification(this, state)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Unable to elevate video export foreground notification: ${e.message}")
                    stopSelfSafely()
                }
                return START_NOT_STICKY
            }
        }
    }

    private fun stopSelfSafely() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {}
        stopSelf()
    }
}
