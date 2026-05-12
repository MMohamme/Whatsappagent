package com.example.whatsappagent.worker

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.whatsappagent.AgentLogger
import com.example.whatsappagent.data.AppDatabase
import com.example.whatsappagent.data.ContactCacheEntity
import com.example.whatsappagent.data.remote.AgentApiService
import com.example.whatsappagent.data.remote.AgentRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ContactIndexerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val apiService: AgentApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://humming-opposite-deforest.ngrok-free.dev/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(AgentApiService::class.java)
    }

    override suspend fun doWork(): Result {
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.contactCacheDao()
        val repository = AgentRepository(apiService, db)

        AgentLogger.log(AgentLogger.LogType.INFO, "🔍 Starte Kontakt-Indizierung...")

        try {
            val contacts = fetchWhatsAppContacts(applicationContext.contentResolver)
            if (contacts.isNotEmpty()) {
                dao.insertContacts(contacts)
                AgentLogger.log(AgentLogger.LogType.INFO, "✅ ${contacts.size} WhatsApp-Kontakte indiziert.")
                
                // Task 4: Sync phone numbers to backend
                AgentLogger.log(AgentLogger.LogType.INFO, "📤 Synchronisiere Telefonnummern mit Backend...")
                dao.getAll().forEach { cached ->
                    repository.syncContactPhoneNumber(
                        contactName = cached.name,
                        phoneNumber = cached.phoneNumber
                    )
                }
                AgentLogger.log(AgentLogger.LogType.INFO, "✅ Backend-Sync abgeschlossen.")
            } else {
                AgentLogger.log(AgentLogger.LogType.INFO, "⚠️ Keine WhatsApp-Kontakte gefunden.")
            }
        } catch (e: Exception) {
            AgentLogger.log(AgentLogger.LogType.ERROR, "❌ Fehler bei Kontakt-Indizierung: ${e.message}")
            return Result.retry()
        }

        return Result.success()
    }

    private fun fetchWhatsAppContacts(resolver: ContentResolver): List<ContactCacheEntity> {
        val contacts = mutableListOf<ContactCacheEntity>()
        
        val projection = arrayOf(
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.Data.DATA1, // JID
            ContactsContract.Data.DATA3  // Phone Number or Action Text
        )

        val selection = "${ContactsContract.Data.MIMETYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            "vnd.android.cursor.item/vnd.com.whatsapp.profile",
            "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile"
        )

        val cursor: Cursor? = resolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.Data.DISPLAY_NAME)
            val jidIndex = it.getColumnIndex(ContactsContract.Data.DATA1)
            val data3Index = it.getColumnIndex(ContactsContract.Data.DATA3)

            while (it.moveToNext()) {
                val name = it.getString(nameIndex) ?: continue
                val jid = it.getString(jidIndex) ?: ""
                val data3 = it.getString(data3Index) ?: ""
                
                var phoneNumber = jid.split("@")[0]
                
                if (phoneNumber.isBlank() || !phoneNumber.all { c -> c.isDigit() }) {
                    phoneNumber = data3.filter { c -> c.isDigit() }
                }

                if (phoneNumber.isNotBlank()) {
                    contacts.add(ContactCacheEntity(phoneNumber, name))
                }
            }
        }
        
        return contacts
    }
}
