package com.example.whatsappagent.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for contact settings operations
 */
@Dao
interface ContactSettingsDao {
    
    @Query("SELECT * FROM contact_settings ORDER BY contactName")
    fun getAllContactSettings(): Flow<List<ContactSettingsEntity>>
    
    @Query("SELECT * FROM contact_settings WHERE phoneNumber = :phoneNumber")
    suspend fun getContactSettingsByPhone(phoneNumber: String): ContactSettingsEntity?

    @Query("SELECT * FROM contact_settings WHERE contactName = :contactName")
    suspend fun getContactSettingsByName(contactName: String): ContactSettingsEntity?
    
    @Query("SELECT * FROM contact_settings WHERE isActive = 1 ORDER BY contactName")
    fun getActiveContactSettings(): Flow<List<ContactSettingsEntity>>
    
    @Query("SELECT * FROM contact_settings WHERE category = :category ORDER BY contactName")
    suspend fun getSettingsByCategory(category: String): List<ContactSettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateContactSettings(settings: ContactSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(settings: List<ContactSettingsEntity>)
    
    @Update
    suspend fun updateContactSettings(settings: ContactSettingsEntity)
    
    @Query("UPDATE contact_settings SET isActive = :isActive WHERE phoneNumber = :phoneNumber")
    suspend fun updateIsActive(phoneNumber: String, isActive: Boolean)

    @Query("UPDATE contact_settings SET isActive = :isActive WHERE contactName = :contactName")
    suspend fun updateContactActiveStatusByName(contactName: String, isActive: Boolean)
    
    @Query("UPDATE contact_settings SET delayMin = :delayMin, delayMax = :delayMax WHERE phoneNumber = :phoneNumber")
    suspend fun updateContactDelays(phoneNumber: String, delayMin: Int, delayMax: Int)
    
    @Query("DELETE FROM contact_settings WHERE phoneNumber = :phoneNumber")
    suspend fun deleteContactSettings(phoneNumber: String)

    @Query("DELETE FROM contact_settings WHERE contactName = :contactName")
    suspend fun deleteContactSettingsByName(contactName: String)
}
