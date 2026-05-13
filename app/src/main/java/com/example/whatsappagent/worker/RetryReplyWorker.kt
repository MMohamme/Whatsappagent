package com.example.whatsappagent.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.MessageStatus

class RetryReplyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.messageDao()

        val toRetry = buildList {
            addAll(dao.getMessagesByStatus(MessageStatus.SEND_PENDING))
            addAll(dao.getMessagesByStatus(MessageStatus.SENDING))
            addAll(dao.getMessagesByStatus(MessageStatus.FAILED))
            addAll(dao.getMessagesByStatus(MessageStatus.REPLY_PENDING))
            addAll(dao.getMessagesByStatus(MessageStatus.REPLY_FAILED))
        }.distinctBy { it.customId }

        if (toRetry.isEmpty()) return Result.success()

        toRetry.forEach { msg ->
            dao.updateStatus(msg.customId, MessageStatus.NEEDS_REVIEW)
        }
        AgentLogger.log(AgentLogger.LogType.INFO, "RetryWorker: ${toRetry.size} haengende Antworten in Review verschoben")
        return Result.success()
    }
}
