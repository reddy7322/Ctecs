package com.example.util

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.Toast

object ScreenOffUtil {

    fun turnScreenOff(context: Context, onShowMessage: ((String) -> Unit)? = null) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenOn = powerManager?.isInteractive ?: true

        // If screen is already off, do not trigger another screen-off action
        if (!isScreenOn) {
            return
        }

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, AdminReceiver::class.java)

        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            try {
                dpm.lockNow()
            } catch (e: Exception) {
                val msg = "Screen-off failed: ${e.localizedMessage ?: "Permission unavailable"}"
                if (onShowMessage != null) onShowMessage(msg) else Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            val msg = "Screen-off requires Device Admin permission. Enable in Settings."
            if (onShowMessage != null) {
                onShowMessage(msg)
            } else {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }

            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Enable Device Admin permission to turn off screen directly from Home Page."
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore launch failures
            }
        }
    }
}
