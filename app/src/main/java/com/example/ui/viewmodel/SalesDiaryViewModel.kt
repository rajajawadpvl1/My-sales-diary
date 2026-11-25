package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.CourseEntity
import com.example.data.local.LeadEntity
import com.example.data.model.CallStatus
import com.example.data.repository.RestoreSummary
import com.example.data.repository.SalesDiaryRepository
import com.example.util.ClockProvider
import com.example.util.LocalDateHelper
import com.example.util.SystemClockProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiaryTab {
    NOTES,
    REMINDERS,
}

data class DashboardStats(
    val totalLeads: Int = 0,
    val interestedCount: Int = 0,
    val dueTodayCount: Int = 0,
    val overdueCount: Int = 0,
    val enrolledCount: Int = 0,
) {
    val activeUrgentReminders: Int
        get() = overdueCount + dueTodayCount
}

data class ReminderBuckets(
    val overdue: List<LeadEntity> = emptyList(),
    val dueToday: List<LeadEntity> = emptyList(),
    val upcoming: List<LeadEntity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = overdue.isEmpty() && dueToday.isEmpty() && upcoming.isEmpty()
}

data class LeadFormState(
    val editingLeadId: Long? = null,
    val studentName: String = "",
    val phoneNumber: String = "",
    val course: String = "",
    val callDateIso: String = LocalDateHelper.todayIso(),
    val status: CallStatus = CallStatus.INTERESTED,
    val customFollowUpDateIso: String = LocalDateHelper.addDays(LocalDateHelper.todayIso(), 1),
    val customFollowUpTime: String? = "10:00",
    val scheduleRetryCall: Boolean = false,
    val notes: String = "",
    val phoneError: String? = null,
    val isFormExpanded: Boolean = true,
) {
    val isEditing: Boolean
        get() = editingLeadId != null

    val computedAutoFollowUpIso: String?
        get() = if (status.isAutoTwoDayFollowUp) {
            LocalDateHelper.addDays(callDateIso, 2)
        } else {
            null
        }
}

data class SalesDiaryUiState(
    val selectedTab: DiaryTab = DiaryTab.NOTES,
    val todayIso: String = LocalDateHelper.todayIso(),
    val stats: DashboardStats = DashboardStats(),
    val allLeads: List<LeadEntity> = emptyList(),
    val filteredLeads: List<LeadEntity> = emptyList(),
    val reminders: ReminderBuckets = ReminderBuckets(),
    val courses: List<CourseEntity> = emptyList(),
    val searchQuery: String = "",
    val form: LeadFormState = LeadFormState(),
)

sealed interface UiMessage {
    data class Info(val text: String) : UiMessage
    data class Error(val text: String) : UiMessage
}

class SalesDiaryViewModel(
    private val repository: SalesDiaryRepository,
    private val clock: ClockProvider = SystemClockProvider,
) : ViewModel() {

    private val selectedTabFlow = MutableStateFlow(DiaryTab.NOTES)
    private val searchQueryFlow = MutableStateFlow("")
    private val todayIsoFlow = MutableStateFlow(clock.todayIso())
    private val formFlow = MutableStateFlow(createInitialForm(clock.todayIso()))

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val metaFlow = combine(
        selectedTabFlow,
        searchQueryFlow,
        todayIsoFlow,
        formFlow,
    ) { tab, query, today, form ->
        MetaState(tab, query, today, form)
    }

    val uiState: StateFlow<SalesDiaryUiState> = combine(
        repository.leadsFlow,
        repository.coursesFlow,
        metaFlow,
    ) { leads, courses, meta ->
        val today = meta.todayIso
        val query = meta.searchQuery.trim()
        val normalizedQueryDigits = query.filter { it.isDigit() }

        val filtered = if (query.isEmpty()) {
            leads
        } else {
            leads.filter { lead ->
                lead.studentName.contains(query, ignoreCase = true) ||
                    lead.phoneNumber.contains(query, ignoreCase = true) ||
                    (normalizedQueryDigits.isNotEmpty() && lead.normalizedPhone.contains(normalizedQueryDigits)) ||
                    lead.course.contains(query, ignoreCase = true)
            }
        }

        val activeReminders = leads.filter { it.hasActiveFollowUp }
        val reminderComparator = compareBy<LeadEntity>(
            { it.followUpDate.orEmpty() },
            { it.followUpTime ?: "99:99" },
            { -it.updatedAt },
        )

        val overdue = activeReminders
            .filter { it.followUpDate!! < today }
            .sortedWith(reminderComparator)
        val dueToday = activeReminders
            .filter { it.followUpDate!! == today }
            .sortedWith(reminderComparator)
        val upcoming = activeReminders
            .filter { it.followUpDate!! > today }
            .sortedWith(reminderComparator)

        val stats = DashboardStats(
            totalLeads = leads.size,
            interestedCount = leads.count { it.callStatus == CallStatus.INTERESTED },
            dueTodayCount = dueToday.size,
            overdueCount = overdue.size,
            enrolledCount = leads.count { it.callStatus == CallStatus.ENROLLED },
        )

        SalesDiaryUiState(
            selectedTab = meta.tab,
            todayIso = today,
            stats = stats,
            allLeads = leads,
            filteredLeads = filtered,
            reminders = ReminderBuckets(
                overdue = overdue,
                dueToday = dueToday,
                upcoming = upcoming,
            ),
            courses = courses,
            searchQuery = meta.searchQuery,
            form = meta.form,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SalesDiaryUiState(
            todayIso = clock.todayIso(),
            form = createInitialForm(clock.todayIso()),
        ),
    )

    fun refreshTodayDate() {
        val latestToday = clock.todayIso()
        val previousToday = todayIsoFlow.value
        todayIsoFlow.value = latestToday
        if (latestToday != previousToday) {
            formFlow.update { current ->
                if (!current.isEditing && current.callDateIso == previousToday) {
                    current.copy(
                        callDateIso = latestToday,
                        customFollowUpDateIso = LocalDateHelper.addDays(latestToday, 1),
                    )
                } else {
                    current
                }
            }
        }
    }

    fun selectTab(tab: DiaryTab) {
        selectedTabFlow.value = tab
    }

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
    }

    fun toggleFormExpanded() {
        formFlow.update { it.copy(isFormExpanded = !it.isFormExpanded) }
    }

    fun openNewLeadForm() {
        selectedTabFlow.value = DiaryTab.NOTES
        val defaultCourse = uiState.value.courses.firstOrNull()?.name.orEmpty()
        formFlow.value = createInitialForm(clock.todayIso(), defaultCourse).copy(isFormExpanded = true)
    }

    fun onStudentNameChange(value: String) {
        formFlow.update { it.copy(studentName = value) }
    }

    fun onPhoneNumberChange(value: String) {
        formFlow.update { it.copy(phoneNumber = value, phoneError = null) }
    }

    fun onCourseSelected(course: String) {
        formFlow.update { it.copy(course = course) }
    }

    fun onCallDateSelected(isoDate: String) {
        formFlow.update { it.copy(callDateIso = isoDate) }
    }

    fun onStatusSelected(status: CallStatus) {
        val today = clock.todayIso()
        formFlow.update { current ->
            val nextCustomDate = when {
                LocalDateHelper.isValidIsoDate(current.customFollowUpDateIso) -> current.customFollowUpDateIso
                else -> LocalDateHelper.addDays(today, 1)
            }
            val shouldEnableRetry = when (status) {
                CallStatus.NOT_CONNECTED, CallStatus.BUSY -> current.scheduleRetryCall
                else -> false
            }
            current.copy(
                status = status,
                customFollowUpDateIso = nextCustomDate,
                scheduleRetryCall = shouldEnableRetry,
            )
        }
    }

    fun onCustomFollowUpDateSelected(isoDate: String) {
        formFlow.update { it.copy(customFollowUpDateIso = isoDate) }
    }

    fun onCustomFollowUpTimeSelected(hhmm: String?) {
        formFlow.update { it.copy(customFollowUpTime = hhmm) }
    }

    fun onScheduleRetryToggle(enabled: Boolean) {
        val today = clock.todayIso()
        formFlow.update { current ->
            current.copy(
                scheduleRetryCall = enabled,
                customFollowUpDateIso = if (enabled && !LocalDateHelper.isValidIsoDate(current.customFollowUpDateIso)) {
                    LocalDateHelper.addDays(today, 1)
                } else {
                    current.customFollowUpDateIso
                },
            )
        }
    }

    fun onNotesChange(value: String) {
        formFlow.update { it.copy(notes = value) }
    }

    fun startEditingLead(lead: LeadEntity) {
        val today = clock.todayIso()
        val status = lead.callStatus
        val hasRetryDate = status.supportsOptionalRetryFollowUp && !lead.followUpDate.isNullOrBlank()
        formFlow.value = LeadFormState(
            editingLeadId = lead.id,
            studentName = lead.studentName,
            phoneNumber = lead.phoneNumber,
            course = lead.course,
            callDateIso = lead.callDate,
            status = status,
            customFollowUpDateIso = lead.followUpDate ?: LocalDateHelper.addDays(today, 1),
            customFollowUpTime = lead.followUpTime,
            scheduleRetryCall = hasRetryDate,
            notes = lead.notes,
            phoneError = null,
            isFormExpanded = true,
        )
        selectedTabFlow.value = DiaryTab.NOTES
    }

    fun cancelEditing() {
        val defaultCourse = uiState.value.courses.firstOrNull()?.name.orEmpty()
        formFlow.value = createInitialForm(clock.todayIso(), defaultCourse)
    }

    fun submitLeadForm() {
        val form = formFlow.value
        val digits = LocalDateHelper.normalizePhone(form.phoneNumber)
        if (digits.length < 5) {
            formFlow.update { it.copy(phoneError = "Please enter a valid phone number.") }
            return
        }

        val wasEditing = form.isEditing
        viewModelScope.launch {
            val saved = repository.saveLead(
                editingLeadId = form.editingLeadId,
                studentName = form.studentName,
                phoneNumber = form.phoneNumber,
                course = form.course,
                callDateIso = form.callDateIso,
                status = form.status,
                notes = form.notes,
                customFollowUpDateIso = form.customFollowUpDateIso,
                customFollowUpTime = form.customFollowUpTime,
                scheduleRetryFollowUp = form.scheduleRetryCall,
            )

            val defaultCourse = uiState.value.courses.firstOrNull()?.name
                ?: form.course
            formFlow.value = createInitialForm(clock.todayIso(), defaultCourse)

            val feedback = when {
                saved.followUpDate != null -> {
                    val dateLabel = LocalDateHelper.formatShortDate(saved.followUpDate)
                    val verb = if (wasEditing) "Lead updated" else "Lead saved"
                    "$verb • Follow-up set for $dateLabel"
                }
                wasEditing -> "Lead updated • Reminder closed"
                else -> "Lead saved"
            }
            _messages.tryEmit(UiMessage.Info(feedback))
        }
    }

    fun deleteLead(lead: LeadEntity) {
        viewModelScope.launch {
            repository.deleteLead(lead.id)
            if (formFlow.value.editingLeadId == lead.id) {
                cancelEditing()
            }
            _messages.tryEmit(UiMessage.Info("Deleted ${lead.displayName}"))
        }
    }

    fun applyReminderAction(
        lead: LeadEntity,
        newStatus: CallStatus,
        customDateIso: String? = null,
        customTime: String? = null,
        updatedNote: String? = null,
    ) {
        viewModelScope.launch {
            val updated = repository.applyAfterCallAction(
                leadId = lead.id,
                newStatus = newStatus,
                customFollowUpDateIso = customDateIso,
                customFollowUpTime = customTime,
                updatedNote = updatedNote,
            )
            if (updated != null) {
                val msg = when {
                    updated.followUpDate != null -> {
                        val dateText = LocalDateHelper.formatShortDate(updated.followUpDate)
                        val timeText = LocalDateHelper.formatTimeDisplay(updated.followUpTime)
                        val suffix = if (timeText != null) " at $timeText" else ""
                        "${updated.displayName} → ${newStatus.label} ($dateText$suffix)"
                    }
                    newStatus == CallStatus.ENROLLED -> "${updated.displayName} marked Enrolled!"
                    else -> "${updated.displayName} → ${newStatus.label} (Closed)"
                }
                _messages.tryEmit(UiMessage.Info(msg))
            }
        }
    }

    fun addCourse(name: String, price: String) {
        val cleanName = name.trim()
        val cleanPrice = price.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val added = repository.addCourse(cleanName, cleanPrice)
            if (added) {
                formFlow.update { it.copy(course = cleanName) }
                val priceNote = if (cleanPrice.isNotEmpty()) " ($cleanPrice)" else ""
                _messages.tryEmit(UiMessage.Info("Added course: $cleanName$priceNote"))
            } else {
                _messages.tryEmit(UiMessage.Error("Course already exists"))
            }
        }
    }

    fun updateCourse(course: CourseEntity, newName: String, newPrice: String) {
        val cleanName = newName.trim()
        val cleanPrice = newPrice.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            val updated = repository.updateCourse(course.id, cleanName, cleanPrice)
            if (updated) {
                if (formFlow.value.course.equals(course.name, ignoreCase = true)) {
                    formFlow.update { it.copy(course = cleanName) }
                }
                val priceNote = if (cleanPrice.isNotEmpty()) " ($cleanPrice)" else ""
                _messages.tryEmit(UiMessage.Info("Updated course: $cleanName$priceNote"))
            } else {
                _messages.tryEmit(UiMessage.Error("Another course already has that name"))
            }
        }
    }

    fun deleteCourse(course: CourseEntity) {
        viewModelScope.launch {
            repository.deleteCourse(course.id)
            val remaining = uiState.value.courses.filter { it.id != course.id }
            if (formFlow.value.course.equals(course.name, ignoreCase = true)) {
                val fallback = remaining.firstOrNull()?.name.orEmpty()
                formFlow.update { it.copy(course = fallback) }
            }
            _messages.tryEmit(UiMessage.Info("Removed course: ${course.name}"))
        }
    }

    suspend fun generateCsvContent(): String = repository.exportLeadsCsv()

    suspend fun generateJsonBackupContent(): String = repository.exportBackupJson()

    fun restoreBackupJson(rawJson: String) {
        viewModelScope.launch {
            val result: Result<RestoreSummary> = repository.restoreFromBackupJson(rawJson)
            result.onSuccess { summary ->
                _messages.tryEmit(
                    UiMessage.Info(
                        "Restored ${summary.leadsRestored} leads and ${summary.coursesRestored} courses"
                    )
                )
            }.onFailure { err ->
                _messages.tryEmit(
                    UiMessage.Error(err.message ?: "Could not restore backup file.")
                )
            }
        }
    }

    fun emitInfo(text: String) {
        _messages.tryEmit(UiMessage.Info(text))
    }

    fun emitError(text: String) {
        _messages.tryEmit(UiMessage.Error(text))
    }

    private fun createInitialForm(
        todayIso: String,
        defaultCourse: String = "",
    ): LeadFormState {
        return LeadFormState(
            callDateIso = todayIso,
            course = defaultCourse,
            status = CallStatus.INTERESTED,
            customFollowUpDateIso = LocalDateHelper.addDays(todayIso, 1),
            customFollowUpTime = "10:00",
            isFormExpanded = true,
        )
    }

    private data class MetaState(
        val tab: DiaryTab,
        val searchQuery: String,
        val todayIso: String,
        val form: LeadFormState,
    )

    class Factory(
        private val repository: SalesDiaryRepository,
        private val clock: ClockProvider = SystemClockProvider,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SalesDiaryViewModel(repository, clock) as T
        }
    }
}
