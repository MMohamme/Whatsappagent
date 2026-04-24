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
        val handler = android.os.Handler(mainLooper)
        val runnable = object : Runnable {
            override fun run() {
                checkBackend()
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(runnable)
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