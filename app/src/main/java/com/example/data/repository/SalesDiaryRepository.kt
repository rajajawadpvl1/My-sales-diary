package com.example.data.repository

import com.example.data.backup.BackupSerializer
import com.example.data.local.CourseDao
import com.example.data.local.CourseEntity
import com.example.data.local.LeadDao
import com.example.data.local.LeadEntity
import com.example.data.model.CallStatus
import com.example.util.ClockProvider
import com.example.util.LocalDateHelper
import com.example.util.SystemClockProvider
import kotlinx.coroutines.flow.Flow

data class RestoreSummary(
    val leadsRestored: Int,
    val coursesRestored: Int,
)

class SalesDiaryRepository(
    private val leadDao: LeadDao,
    private val courseDao: CourseDao,
    private val clock: ClockProvider = SystemClockProvider,
) {
    val leadsFlow: Flow<List<LeadEntity>> = leadDao.observeAllLeads()
    val coursesFlow: Flow<List<CourseEntity>> = courseDao.observeAllCourses()

    suspend fun addCourse(name: String, price: String = ""): Boolean {
        val cleanName = name.trim()
        val cleanPrice = price.trim()
        if (cleanName.isEmpty()) return false
        val existing = courseDao.getAllCoursesOnce()
        if (existing.any { it.name.equals(cleanName, ignoreCase = true) }) {
            return false
        }
        return courseDao.insertCourse(CourseEntity(name = cleanName, price = cleanPrice)) != -1L
    }

    suspend fun updateCourse(id: Long, newName: String, newPrice: String): Boolean {
        val cleanName = newName.trim()
        val cleanPrice = newPrice.trim()
        if (cleanName.isEmpty()) return false
        val current = courseDao.findById(id) ?: return false
        val existing = courseDao.getAllCoursesOnce()
        if (existing.any { it.id != id && it.name.equals(cleanName, ignoreCase = true) }) {
            return false
        }
        courseDao.updateCourse(id, cleanName, cleanPrice)
        if (!current.name.equals(cleanName, ignoreCase = false)) {
            leadDao.updateCourseNameForLeads(oldCourseName = current.name, newCourseName = cleanName)
        }
        return true
    }

    suspend fun deleteCourse(id: Long) {
        courseDao.deleteCourseById(id)
    }

    /**
     * Saves a new lead or updates an existing lead while strictly enforcing:
     * - Local date follow-up rules (+2 calendar days for Interested, custom for Call Back, optional retry for Not Connected/Busy, closed for Not Interested/Wrong Number/Enrolled)
     * - Deduplication: a student cannot appear multiple times for an active follow-up.
     */
    suspend fun saveLead(
        editingLeadId: Long?,
        studentName: String,
        phoneNumber: String,
        course: String,
        callDateIso: String,
        status: CallStatus,
        notes: String,
        customFollowUpDateIso: String?,
        customFollowUpTime: String?,
        scheduleRetryFollowUp: Boolean,
    ): LeadEntity {
        val cleanPhone = phoneNumber.trim()
        val normalizedPhone = LocalDateHelper.normalizePhone(cleanPhone)
        val safeCallDate = if (LocalDateHelper.isValidIsoDate(callDateIso)) {
            callDateIso
        } else {
            clock.todayIso()
        }
        val cleanCourse = course.trim()
        val now = clock.nowMillis()

        // Persist any newly entered course name automatically if provided
        if (cleanCourse.isNotEmpty()) {
            addCourse(cleanCourse)
        }

        val (resolvedFollowUpDate, resolvedFollowUpTime) = computeFollowUp(
            status = status,
            callDateIso = safeCallDate,
            customDateIso = customFollowUpDateIso,
            customTime = customFollowUpTime,
            scheduleRetry = scheduleRetryFollowUp,
        )

        if (editingLeadId != null) {
            val existing = leadDao.findById(editingLeadId)
            val updated = LeadEntity(
                id = editingLeadId,
                studentName = studentName.trim(),
                phoneNumber = cleanPhone,
                normalizedPhone = normalizedPhone,
                course = cleanCourse,
                callDate = safeCallDate,
                status = status.name,
                notes = notes.trim(),
                followUpDate = resolvedFollowUpDate,
                followUpTime = resolvedFollowUpTime,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            leadDao.updateLead(updated)
            return updated
        }

        // Prevent duplicate active follow-ups for the same student phone number
        if (resolvedFollowUpDate != null) {
            val activeSamePhone = leadDao.findActiveFollowUpByPhone(normalizedPhone)
            if (activeSamePhone != null) {
                val merged = activeSamePhone.copy(
                    studentName = studentName.trim().ifEmpty { activeSamePhone.studentName },
                    phoneNumber = cleanPhone,
                    course = cleanCourse,
                    callDate = safeCallDate,
                    status = status.name,
                    notes = notes.trim().ifEmpty { activeSamePhone.notes },
                    followUpDate = resolvedFollowUpDate,
                    followUpTime = resolvedFollowUpTime,
                    updatedAt = now,
                )
                leadDao.updateLead(merged)
                return merged
            }
        }

        val entity = LeadEntity(
            studentName = studentName.trim(),
            phoneNumber = cleanPhone,
            normalizedPhone = normalizedPhone,
            course = cleanCourse,
            callDate = safeCallDate,
            status = status.name,
            notes = notes.trim(),
            followUpDate = resolvedFollowUpDate,
            followUpTime = resolvedFollowUpTime,
            createdAt = now,
            updatedAt = now,
        )
        val insertedId = leadDao.insertLead(entity)
        return entity.copy(id = insertedId)
    }

    /**
     * Applies an after-call outcome directly from the Reminders tab.
     * Automatically reschedules or closes the reminder so it leaves the current due list.
     */
    suspend fun applyAfterCallAction(
        leadId: Long,
        newStatus: CallStatus,
        customFollowUpDateIso: String? = null,
        customFollowUpTime: String? = null,
        updatedNote: String? = null,
    ): LeadEntity? {
        val existing = leadDao.findById(leadId) ?: return null
        val today = clock.todayIso()
        val now = clock.nowMillis()

        val scheduleRetry = newStatus == CallStatus.NOT_CONNECTED || newStatus == CallStatus.BUSY
        val (nextDate, nextTime) = computeFollowUp(
            status = newStatus,
            callDateIso = today,
            customDateIso = customFollowUpDateIso,
            customTime = customFollowUpTime,
            scheduleRetry = scheduleRetry && !customFollowUpDateIso.isNullOrBlank(),
        )

        val finalNotes = when {
            updatedNote == null -> existing.notes
            updatedNote.trim().isEmpty() -> existing.notes
            else -> updatedNote.trim()
        }

        val updated = existing.copy(
            callDate = today,
            status = newStatus.name,
            notes = finalNotes,
            followUpDate = nextDate,
            followUpTime = nextTime,
            updatedAt = now,
        )
        leadDao.updateLead(updated)
        return updated
    }

    suspend fun deleteLead(id: Long) {
        leadDao.deleteLeadById(id)
    }

    suspend fun exportLeadsCsv(): String {
        val leads = leadDao.getAllLeadsOnce()
        return BackupSerializer.toCsv(leads)
    }

    suspend fun exportBackupJson(): String {
        val leads = leadDao.getAllLeadsOnce()
        val courses = courseDao.getAllCoursesOnce()
        return BackupSerializer.toBackupJson(
            leads = leads,
            courses = courses,
            exportedAtMillis = clock.nowMillis(),
        )
    }

    suspend fun restoreFromBackupJson(rawJson: String): Result<RestoreSummary> {
        val parsed = BackupSerializer.parseBackupJson(
            rawJson = rawJson,
            fallbackTodayIso = clock.todayIso(),
            nowMillis = clock.nowMillis(),
        )
        return parsed.map { (leads, courses) ->
            // Deduplicate active follow-ups by normalizedPhone in case a backup had duplicates
            val dedupedLeads = deduplicateActiveFollowUps(leads)
            leadDao.replaceAllLeads(dedupedLeads)

            val coursesByKey = LinkedHashMap<String, CourseEntity>()
            for (course in courses) {
                val clean = course.name.trim()
                if (clean.isNotEmpty()) {
                    coursesByKey.putIfAbsent(clean.lowercase(), CourseEntity(name = clean, price = course.price.trim()))
                }
            }
            for (lead in dedupedLeads) {
                val clean = lead.course.trim()
                if (clean.isNotEmpty()) {
                    coursesByKey.putIfAbsent(clean.lowercase(), CourseEntity(name = clean, price = ""))
                }
            }
            val finalCourses = coursesByKey.values.toList()
            courseDao.replaceAllCourses(finalCourses)

            RestoreSummary(
                leadsRestored = dedupedLeads.size,
                coursesRestored = finalCourses.size,
            )
        }
    }

    private fun deduplicateActiveFollowUps(leads: List<LeadEntity>): List<LeadEntity> {
        val seenActivePhones = mutableSetOf<String>()
        val result = mutableListOf<LeadEntity>()
        // Keep the most recently updated entry when active follow-ups collide
        val sorted = leads.sortedByDescending { it.updatedAt }
        for (lead in sorted) {
            if (lead.hasActiveFollowUp) {
                if (seenActivePhones.add(lead.normalizedPhone)) {
                    result.add(lead)
                }
            } else {
                result.add(lead)
            }
        }
        return result
    }

    companion object {
        fun computeFollowUp(
            status: CallStatus,
            callDateIso: String,
            customDateIso: String?,
            customTime: String?,
            scheduleRetry: Boolean,
        ): Pair<String?, String?> {
            val safeTime = if (LocalDateHelper.isValidTime(customTime)) customTime else null
            return when (status) {
                CallStatus.INTERESTED -> {
                    LocalDateHelper.addDays(callDateIso, 2) to null
                }
                CallStatus.CALL_BACK -> {
                    val date = if (LocalDateHelper.isValidIsoDate(customDateIso)) {
                        customDateIso
                    } else {
                        LocalDateHelper.addDays(callDateIso, 1)
                    }
                    date to safeTime
                }
                CallStatus.NOT_CONNECTED, CallStatus.BUSY -> {
                    if (scheduleRetry && LocalDateHelper.isValidIsoDate(customDateIso)) {
                        customDateIso to safeTime
                    } else {
                        null to null
                    }
                }
                CallStatus.NOT_INTERESTED,
                CallStatus.WRONG_NUMBER,
                CallStatus.ENROLLED,
                CallStatus.SWITCHED_OFF -> null to null
            }
        }
    }
}
