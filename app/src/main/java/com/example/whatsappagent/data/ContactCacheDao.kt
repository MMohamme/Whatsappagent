package com.example.whatsappagent.data

import androidx.room.*

@Dao
interface ContactCacheDao {
    @Query("SELECT * FROM contact_cache WHERE name = :name ORDER BY lastUpdated DESC LIMIT 1")
    suspend fun getByName(name: String): ContactCacheEntity?

    @Query("SELECT * FROM contact_cache WHERE phoneNumber = :phoneNumber LIMIT 1")
    suspend fun getByPhone(phoneNumber: String): ContactCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactCacheEntity>)

    @Query("SELECT * FROM contact_cache")
    suspend fun getAll(): List<ContactCacheEntity>

    @Query("SELECT COUNT(*) FROM contact_cache")
    suspend fun getCount(): Int

    @Query("DELETE FROM contact_cache")
    suspend fun clearAll()
}
