package com.example.whatsappagent.worker

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.whatsappagent.WhatsAppListener
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.MessageStatus

class RetryReplyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.messageDao()
        
        // Find messages that are stuck in REPLY_PENDING or REPLY_FAILED
        val pending = dao.getMessagesByStatus(MessageStatus.REPLY_PENDING)
        val failed = dao.getMessagesByStatus(MessageStatus.REPLY_FAILED)
        
        val toRetry = pending + failed
        
        if (toRetry.isEmpty()) return Result.success()
        
        toRetry.forEach { msg ->
            // Re-send broadcast
            val intent = Intent(WhatsAppListener.ACTION_SEND_REPLY).apply {
                putExtra(WhatsAppListener.EXTRA_SENDER, msg.sender)
                // For REPLY_PENDING/FAILED, we need the original reply. 
                // Wait, if it's the USER message that is stuck, we need to find its assistant reply.
                // But the task plan says:
                /*
                pending.forEach { msg ->
                    // Broadcast erneut senden
                    val intent = Intent(ACTION_SEND_REPLY).apply {
                        putExtra(EXTRA_SENDER, msg.sender)
                        putExtra(EXTRA_REPLY, msg.text)
                        putExtra(EXTRA_CUSTOM_ID, msg.customId)
                    }
                    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                }
                */
                // Actually, MessageEntity with status REPLY_PENDING is the USER message.
                // We need to find the corresponding 'assistant' reply.
                
                val assistantReply = db.messageDao().getLastMessages(msg.sender, 5)
                    .find { it.role == "assistant" && it.customId == "reply_${msg.customId}" }
                
                if (assistantReply != null) {
                    putExtra(WhatsAppListener.EXTRA_REPLY, assistantReply.text)
                    putExtra(WhatsAppListener.EXTRA_CUSTOM_ID, msg.customId)
                    LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(this)
                }
            }
        }
        return Result.success()
    }
}
