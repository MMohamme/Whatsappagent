package com.example.whatsappagent.worker

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
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
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer ${com.example.whatsappagent.BuildConfig.APP_API_TOKEN}")
                    .addHeader("ngrok-skip-browser-warning", "1")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(com.example.whatsappagent.BuildConfig.BACKEND_BASE_URL)
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
            if (ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                AgentLogger.log(AgentLogger.LogType.INFO, "Kontakt-Indizierung uebersprungen: READ_CONTACTS fehlt")
                return Result.success()
            }
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
        val phoneContacts = fetchPhoneContacts(resolver)
        val contacts = linkedMapOf<String, ContactCacheEntity>()
        
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
                
                var phoneNumber = normalizePhone(jid.split("@")[0])
                
                if (phoneNumber.isBlank()) {
                    phoneNumber = normalizePhone(data3)
                }
                if (phoneNumber.isBlank()) {
                    phoneNumber = phoneContacts[normalizeName(name)].orEmpty()
                }

                if (phoneNumber.isNotBlank()) {
                    contacts[phoneNumber] = ContactCacheEntity(phoneNumber, name)
                }
            }
        }
        
        return contacts.values.toList()
    }

    private fun fetchPhoneContacts(resolver: ContentResolver): Map<String, String> {
        val contacts = linkedMapOf<String, String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                val number = normalizePhone(cursor.getString(numberIndex) ?: "")
                if (number.isNotBlank()) {
                    contacts.putIfAbsent(normalizeName(name), number)
                }
            }
        }
        return contacts
    }

    private fun normalizePhone(raw: String): String =
        raw.filter { it.isDigit() }

    private fun normalizeName(raw: String): String =
        raw.trim().lowercase().replace(Regex("\\s+"), " ")
}
