package com.example.whatsappagent

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.util.Log

class AgentService : Service() {

    private val CHANNEL_ID = "agent_channel"
    private val NOTIF_ID = 1

    private val BASE_URL = "https://humming-opposite-deforest.ngrok-free.dev"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var isConnected = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var healthCheckRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Initialisiere..."))
        startHealthCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startHealthCheck() {
        healthCheckRunnable = object : Runnable {
            override fun run() {
                checkBackend()
                checkNotificationListener()
                handler.postDelayed(this, 5000)
            }
        }
        healthCheckRunnable?.let { handler.post(it) }
    }

    private fun checkNotificationListener() {
        val application = applicationContext
        val now = System.currentTimeMillis()
        val COOLDOWN = 60_000L // 1 minute rebind cooldown

        if (isNotificationServiceEnabled(application)) {
            val isRunning = WhatsAppListener.isRunning && isListenerActuallyConnected()
            
            if (!isRunning) {
                if (now - WhatsAppListener.lastRebindRequestAt < COOLDOWN) {
                    return
                }
                
                Log.d("WA_AGENT", "Listener inactive. Requesting rebind...")
                WhatsAppListener.lastRebindRequestAt = now
                try {
                    android.service.notification.NotificationListenerService.requestRebind(
                        android.content.ComponentName(application, WhatsAppListener::class.java)
                    )
                } catch (e: Exception) {
                    Log.e("WA_AGENT", "Failed to request rebind: ${e.message}")
                }
            }
        }
    }

    private fun isListenerActuallyConnected(): Boolean {
        val inst = WhatsAppListener.instance ?: return false
        return try {
            inst.activeNotifications != null // Check binder validity
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isNotificationServiceEnabled(context: android.content.Context): Boolean {
        val packageNames = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return packageNames != null && packageNames.contains(context.packageName)
    }

    override fun onDestroy() {
        super.onDestroy()
        healthCheckRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun checkBackend() {
        val request = Request.Builder()
            .url("$BASE_URL/health")
            .addHeader("ngrok-skip-browser-warning", "1")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                updateStatus(false)
                Log.e("WA_AGENT", "FEHLER: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                updateStatus(response.isSuccessful)
            }
        })
    }

    private fun updateStatus(connected: Boolean) {
        isConnected = connected
        val text = if (connected) "Backend verbunden ✅" else "Backend getrennt ❌"
        val notification = buildNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, notification)

        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_CONNECTED, connected)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WhatsApp Agent")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Agent Service", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_STATUS_UPDATE = "com.example.whatsappagent.STATUS_UPDATE"
        const val EXTRA_CONNECTED = "connected"
    }
}