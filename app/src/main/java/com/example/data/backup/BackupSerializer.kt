package com.example.data.backup

import com.example.data.local.CourseEntity
import com.example.data.local.LeadEntity
import com.example.data.model.CallStatus
import com.example.util.LocalDateHelper
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

@JsonClass(generateAdapter = true)
data class LeadBackupDto(
    val studentName: String = "",
    val phoneNumber: String,
    val course: String,
    val callDate: String,
    val status: String,
    val notes: String = "",
    val followUpDate: String? = null,
    val followUpTime: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@JsonClass(generateAdapter = true)
data class CourseBackupDto(
    val name: String,
    val price: String = "",
)

@JsonClass(generateAdapter = true)
data class SalesDiaryBackup(
    val schemaVersion: Int = 2,
    val appName: String = "My Sales Diary",
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val courses: List<String> = emptyList(),
    val courseItems: List<CourseBackupDto>? = null,
    val leads: List<LeadBackupDto> = emptyList(),
)

object BackupSerializer {

    private val moshi: Moshi = Moshi.Builder().build()
    private val backupAdapter = moshi.adapter(SalesDiaryBackup::class.java).indent("  ")

    fun toCsv(leads: List<LeadEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("Student Name,Phone Number,Interested Course,Call Status,Call Date,Follow-up Date,Follow-up Time,Notes")
        for (lead in leads) {
            val fields = listOf(
                lead.studentName,
                lead.phoneNumber,
                lead.course,
                lead.callStatus.label,
                lead.callDate,
                lead.followUpDate.orEmpty(),
                lead.followUpTime.orEmpty(),
                lead.notes,
            )
            sb.appendLine(fields.joinToString(",") { escapeCsv(it) })
        }
        return sb.toString()
    }

    fun toBackupJson(
        leads: List<LeadEntity>,
        courses: List<CourseEntity>,
        exportedAtMillis: Long = System.currentTimeMillis(),
    ): String {
        val payload = SalesDiaryBackup(
            schemaVersion = 2,
            exportedAtMillis = exportedAtMillis,
            courses = courses.map { it.name },
            courseItems = courses.map { CourseBackupDto(name = it.name, price = it.price) },
            leads = leads.map { lead ->
                LeadBackupDto(
                    studentName = lead.studentName,
                    phoneNumber = lead.phoneNumber,
                    course = lead.course,
                    callDate = lead.callDate,
                    status = lead.callStatus.name,
                    notes = lead.notes,
                    followUpDate = lead.followUpDate,
                    followUpTime = lead.followUpTime,
                    createdAt = lead.createdAt,
                    updatedAt = lead.updatedAt,
                )
            },
        )
        return backupAdapter.toJson(payload)
    }

    fun parseBackupJson(rawJson: String, fallbackTodayIso: String, nowMillis: Long): Result<Pair<List<LeadEntity>, List<CourseEntity>>> {
        return runCatching {
            val trimmed = rawJson.trim()
            require(trimmed.isNotEmpty()) { "Backup file is empty." }
            val parsed = backupAdapter.fromJson(trimmed)
                ?: error("Invalid backup file format.")

            val sanitizedCourses = if (!parsed.courseItems.isNullOrEmpty()) {
                parsed.courseItems
                    .mapNotNull { dto ->
                        val cleanName = dto.name.trim()
                        if (cleanName.isEmpty()) null else CourseEntity(name = cleanName, price = dto.price.trim())
                    }
                    .distinctBy { it.name.lowercase() }
            } else {
                parsed.courses
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .map { CourseEntity(name = it, price = "") }
            }

            val sanitizedLeads = parsed.leads.mapNotNull { dto ->
                val cleanPhone = dto.phoneNumber.trim()
                val normalized = LocalDateHelper.normalizePhone(cleanPhone)
                if (normalized.isEmpty()) return@mapNotNull null

                val status = CallStatus.fromStored(dto.status)
                val safeCallDate = if (LocalDateHelper.isValidIsoDate(dto.callDate)) {
                    dto.callDate
                } else {
                    fallbackTodayIso
                }
                val safeFollowUpDate = when {
                    status.closesReminder -> null
                    LocalDateHelper.isValidIsoDate(dto.followUpDate) -> dto.followUpDate
                    status.isAutoTwoDayFollowUp -> LocalDateHelper.addDays(safeCallDate, 2)
                    else -> null
                }
                val safeFollowUpTime = if (safeFollowUpDate != null && LocalDateHelper.isValidTime(dto.followUpTime)) {
                    dto.followUpTime
                } else {
                    null
                }

                LeadEntity(
                    id = 0L,
                    studentName = dto.studentName.trim(),
                    phoneNumber = cleanPhone,
                    normalizedPhone = normalized,
                    course = dto.course.trim(),
                    callDate = safeCallDate,
                    status = status.name,
                    notes = dto.notes.trim(),
                    followUpDate = safeFollowUpDate,
                    followUpTime = safeFollowUpTime,
                    createdAt = if (dto.createdAt > 0L) dto.createdAt else nowMillis,
                    updatedAt = if (dto.updatedAt > 0L) dto.updatedAt else nowMillis,
                )
            }

            sanitizedLeads to sanitizedCourses
        }
    }

    private fun escapeCsv(value: String): String {
        val needsQuoting = value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        return if (needsQuoting) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
