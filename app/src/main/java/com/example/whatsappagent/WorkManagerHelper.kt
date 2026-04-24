package com.example.whatsappagent.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

object WorkManagerHelper {

    private const val SYNC_WORK_NAME = "WhatsAppSyncWork"

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
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}