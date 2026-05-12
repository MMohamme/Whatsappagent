package com.example.whatsappagent

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager

object DeviceControl {

    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Wakes up the screen if it's off.
     */
    fun wakeScreen(context: Context, durationMs: Long = 15_000L) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        
        // Release old wakeLock if held
        releaseWakeLock()
        
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WAAgent::ReplyWakeLock"
        ).apply {
            acquire(durationMs)
        }
        AgentLogger.log(AgentLogger.LogType.INFO, "💡 Bildschirm aufgeweckt (${durationMs/1000}s)")
    }

    fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    /**
     * Attempts to dismiss the keyguard (lock screen).
     */
    fun dismissKeyguard(activity: Activity) {
        try {
            val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                km.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissError() {
                        AgentLogger.log(AgentLogger.LogType.ERROR, "Keyguard dismiss error")
                    }
                    override fun onDismissSucceeded() {
                        AgentLogger.log(AgentLogger.LogType.INFO, "Keyguard dismissed")
                    }
                    override fun onDismissCancelled() {
                        AgentLogger.log(AgentLogger.LogType.INFO, "Keyguard dismiss cancelled")
                    }
                })
            } else {
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        } catch (e: SecurityException) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "Missing DISABLE_KEYGUARD permission: ${e.message}")
        } catch (e: Exception) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "Keyguard dismiss failed: ${e.message}")
        }
    }
}
