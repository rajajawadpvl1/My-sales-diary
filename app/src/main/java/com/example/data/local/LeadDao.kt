package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {
    @Query("SELECT * FROM leads ORDER BY updatedAt DESC, id DESC")
    fun observeAllLeads(): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads ORDER BY updatedAt DESC, id DESC")
    suspend fun getAllLeadsOnce(): List<LeadEntity>

    @Query("SELECT * FROM leads WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): LeadEntity?

    @Query("SELECT * FROM leads WHERE normalizedPhone = :normalizedPhone AND followUpDate IS NOT NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findActiveFollowUpByPhone(normalizedPhone: String): LeadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLead(lead: LeadEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(leads: List<LeadEntity>)

    @Update
    suspend fun updateLead(lead: LeadEntity)

    @Query("UPDATE leads SET course = :newCourseName WHERE course = :oldCourseName")
    suspend fun updateCourseNameForLeads(oldCourseName: String, newCourseName: String)

    @Query("DELETE FROM leads WHERE id = :id")
    suspend fun deleteLeadById(id: Long)

    @Query("DELETE FROM leads")
    suspend fun deleteAllLeads()

    @Transaction
    suspend fun replaceAllLeads(leads: List<LeadEntity>) {
        deleteAllLeads()
        if (leads.isNotEmpty()) {
            insertAll(leads)
        }
    }
}
