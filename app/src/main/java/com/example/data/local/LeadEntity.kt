package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.CallStatus

@Entity(
    tableName = "leads",
    indices = [
        Index(value = ["normalizedPhone"]),
        Index(value = ["followUpDate"]),
        Index(value = ["status"]),
    ]
)
data class LeadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val studentName: String = "",
    val phoneNumber: String,
    val normalizedPhone: String,
    val course: String,
    val callDate: String, // Local ISO date: YYYY-MM-DD
    val status: String, // CallStatus.name
    val notes: String = "",
    val followUpDate: String? = null, // Local ISO date: YYYY-MM-DD, null when no active follow-up
    val followUpTime: String? = null, // Local 24h time: HH:mm, optional
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val callStatus: CallStatus
        get() = CallStatus.fromStored(status)

    val displayName: String
        get() = studentName.trim().ifEmpty { phoneNumber }

    val hasActiveFollowUp: Boolean
        get() = !followUpDate.isNullOrBlank() && !callStatus.closesReminder
}
