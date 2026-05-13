package com.example.whatsappagent.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

object WorkManagerHelper {

    private const val SYNC_WORK_NAME = "WhatsAppSyncWork"
    private const val CONTACT_INDEX_WORK_NAME = "WhatsAppContactIndexWork"
    private const val RETRY_WORK_NAME = "WhatsAppRetryWork"
    private const val EVENT_RECIPIENT_POLL_WORK_NAME = "WhatsAppEventRecipientPollWork"

    /**
     * Reiht den SyncWorker ein. Er startet sofort, sobald eine Netzwerkverbindung besteht.
     * Nutzt APPEND_OR_REPLACE, um mehrfache parallele Ausführungen zu verhindern.
     */
    fun triggerSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Nur bei Internet ausführen
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }

    /**
     * Schedult einen periodischen Check für hängengebliebene Antworten.
     */
    fun scheduleRetryCheck(context: Context) {
        val retryRequest = PeriodicWorkRequestBuilder<RetryReplyWorker>(15, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RETRY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            retryRequest
        )
    }

    /**
     * Polls approved due event recipients even when no new inbound WhatsApp message arrives.
     */
    fun scheduleEventRecipientPolling(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val pollRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            EVENT_RECIPIENT_POLL_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            pollRequest
        )
    }

    /**
     * Reiht den ContactIndexerWorker ein.
     */
    fun triggerContactIndexing(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val contactIndexRequest = OneTimeWorkRequestBuilder<ContactIndexerWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            CONTACT_INDEX_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            contactIndexRequest
        )
    }
}
