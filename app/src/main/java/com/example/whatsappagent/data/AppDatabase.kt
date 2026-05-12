package com.example.whatsappagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MessageEntity::class,
        NoteEntity::class,
        EventEntity::class,
        EventTicketEntity::class,
        EventRecipientEntity::class,
        SendAttemptEntity::class,
        ContactSettingsEntity::class,
        ContactCacheEntity::class
    ], 
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun noteDao(): NoteDao
    abstract fun eventDao(): EventDao
    abstract fun eventTicketDao(): EventTicketDao
    abstract fun eventRecipientDao(): EventRecipientDao
    abstract fun sendAttemptDao(): SendAttemptDao
    abstract fun contactSettingsDao(): ContactSettingsDao
    abstract fun contactCacheDao(): ContactCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. contact_settings migrieren
                db.execSQL("""
                    CREATE TABLE contact_settings_new (
                        phoneNumber TEXT NOT NULL PRIMARY KEY,
                        contactName TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        relation TEXT NOT NULL DEFAULT 'Kontakt',
                        category TEXT NOT NULL DEFAULT 'FREUNDE',
                        style TEXT NOT NULL DEFAULT 'Standard',
                        delayMin INTEGER NOT NULL DEFAULT 1500,
                        delayMax INTEGER NOT NULL DEFAULT 3000,
                        preferredLang TEXT NOT NULL DEFAULT 'de',
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("""
                    INSERT OR IGNORE INTO contact_settings_new (phoneNumber, contactName, isActive, relation, category, style, delayMin, delayMax, preferredLang, updatedAt)
                    SELECT COALESCE(cc.phoneNumber, cs.contactName), cs.contactName, 
                           CASE WHEN cs.isActive THEN 1 ELSE 0 END, 
                           cs.relation, cs.category, cs.style, cs.delayMin, cs.delayMax, cs.preferredLang, cs.updatedAt
                    FROM contact_settings cs
                    LEFT JOIN contact_cache cc ON cs.contactName = cc.name
                """)
                db.execSQL("DROP TABLE contact_settings")
                db.execSQL("ALTER TABLE contact_settings_new RENAME TO contact_settings")

                // 2. contact_cache migrieren
                db.execSQL("""
                    CREATE TABLE contact_cache_new (
                        phoneNumber TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        lastUpdated INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    INSERT OR IGNORE INTO contact_cache_new (phoneNumber, name, lastUpdated)
                    SELECT phoneNumber, name, lastUpdated FROM contact_cache
                """)
                db.execSQL("DROP TABLE contact_cache")
                db.execSQL("ALTER TABLE contact_cache_new RENAME TO contact_cache")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "whatsapp_agent_db"
                )
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
