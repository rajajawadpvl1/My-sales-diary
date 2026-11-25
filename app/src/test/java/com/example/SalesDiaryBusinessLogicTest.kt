package com.example

import com.example.data.backup.BackupSerializer
import com.example.data.local.CourseDao
import com.example.data.local.CourseEntity
import com.example.data.local.LeadDao
import com.example.data.local.LeadEntity
import com.example.data.model.CallStatus
import com.example.data.repository.SalesDiaryRepository
import com.example.ui.viewmodel.SalesDiaryViewModel
import com.example.util.ClockProvider
import com.example.util.LocalDateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeClock(
    var today: String = "2026-11-24",
    var now: Long = 1_795_500_000_000L,
) : ClockProvider {
    override fun todayIso(): String = today
    override fun nowMillis(): Long = now
}

private class InMemoryLeadDao : LeadDao {
    private val store = MutableStateFlow<List<LeadEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAllLeads(): Flow<List<LeadEntity>> =
        store.map { list -> list.sortedWith(compareByDescending<LeadEntity> { it.updatedAt }.thenByDescending { it.id }) }

    override suspend fun getAllLeadsOnce(): List<LeadEntity> =
        store.value.sortedWith(compareByDescending<LeadEntity> { it.updatedAt }.thenByDescending { it.id })

    override suspend fun findById(id: Long): LeadEntity? = store.value.firstOrNull { it.id == id }

    override suspend fun findActiveFollowUpByPhone(normalizedPhone: String): LeadEntity? =
        store.value.filter { it.normalizedPhone == normalizedPhone && it.followUpDate != null }
            .maxByOrNull { it.updatedAt }

    override suspend fun insertLead(lead: LeadEntity): Long {
        val id = if (lead.id == 0L) nextId++ else lead.id
        val withId = lead.copy(id = id)
        store.value = store.value.filterNot { it.id == id } + withId
        return id
    }

    override suspend fun insertAll(leads: List<LeadEntity>) {
        leads.forEach { insertLead(it) }
    }

    override suspend fun updateLead(lead: LeadEntity) {
        store.value = store.value.map { if (it.id == lead.id) lead else it }
    }

    override suspend fun updateCourseNameForLeads(oldCourseName: String, newCourseName: String) {
        store.value = store.value.map { if (it.course == oldCourseName) it.copy(course = newCourseName) else it }
    }

    override suspend fun deleteLeadById(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllLeads() {
        store.value = emptyList()
    }
}

private class InMemoryCourseDao : CourseDao {
    private val store = MutableStateFlow<List<CourseEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAllCourses(): Flow<List<CourseEntity>> =
        store.map { list -> list.sortedBy { it.name.lowercase() } }

    override suspend fun getAllCoursesOnce(): List<CourseEntity> =
        store.value.sortedBy { it.name.lowercase() }

    override suspend fun countCourses(): Int = store.value.size

    override suspend fun insertCourse(course: CourseEntity): Long {
        if (store.value.any { it.name.equals(course.name, ignoreCase = true) }) return -1L
        val id = if (course.id == 0L) nextId++ else course.id
        store.value = store.value + course.copy(id = id)
        return id
    }

    override suspend fun insertAll(courses: List<CourseEntity>) {
        courses.forEach { insertCourse(it) }
    }

    override suspend fun findById(id: Long): CourseEntity? = store.value.firstOrNull { it.id == id }

    override suspend fun updateCourse(id: Long, newName: String, newPrice: String) {
        store.value = store.value.map { if (it.id == id) it.copy(name = newName, price = newPrice) else it }
    }

    override suspend fun deleteCourseById(id: Long) {
        store.value = store.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllCourses() {
        store.value = emptyList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SalesDiaryBusinessLogicTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var leadDao: InMemoryLeadDao
    private lateinit var courseDao: InMemoryCourseDao
    private lateinit var clock: FakeClock
    private lateinit var repository: SalesDiaryRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        leadDao = InMemoryLeadDao()
        courseDao = InMemoryCourseDao()
        clock = FakeClock(today = "2026-11-24")
        repository = SalesDiaryRepository(leadDao, courseDao, clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `interested lead schedules exactly 2 days later and enters Due Today then Overdue`() = runTest {
        val viewModel = SalesDiaryViewModel(repository, clock)
        advanceUntilIdle()

        val saved = repository.saveLead(
            editingLeadId = null,
            studentName = "Aarav Sharma",
            phoneNumber = "9876543210",
            course = "Digital Marketing",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "Interested in joining, wants fee details.",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )
        assertEquals("2026-11-26", saved.followUpDate)

        // Nov 24: should be in Upcoming
        var state = viewModel.uiState.first { it.allLeads.size == 1 }
        assertEquals(0, state.reminders.overdue.size)
        assertEquals(0, state.reminders.dueToday.size)
        assertEquals(1, state.reminders.upcoming.size)

        // Advance to Nov 26: automatically appears in Due Today
        clock.today = "2026-11-26"
        viewModel.refreshTodayDate()
        advanceUntilIdle()
        state = viewModel.uiState.first { it.todayIso == "2026-11-26" }
        assertEquals(0, state.reminders.overdue.size)
        assertEquals(1, state.reminders.dueToday.size)
        assertEquals(1, state.stats.dueTodayCount)

        // Advance to Nov 27: automatically becomes Overdue and stays visible
        clock.today = "2026-11-27"
        viewModel.refreshTodayDate()
        advanceUntilIdle()
        state = viewModel.uiState.first { it.todayIso == "2026-11-27" }
        assertEquals(1, state.reminders.overdue.size)
        assertEquals(0, state.reminders.dueToday.size)
    }

    @Test
    fun `changing status from Interested to Not Interested removes reminder`() = runTest {
        val saved = repository.saveLead(
            editingLeadId = null,
            studentName = "Priya",
            phoneNumber = "9123456789",
            course = "Data Science",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "Fee query",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )
        assertEquals("2026-11-26", saved.followUpDate)

        val updated = repository.saveLead(
            editingLeadId = saved.id,
            studentName = saved.studentName,
            phoneNumber = saved.phoneNumber,
            course = saved.course,
            callDateIso = "2026-11-24",
            status = CallStatus.NOT_INTERESTED,
            notes = "Declined",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )
        assertNull(updated.followUpDate)
        assertFalse(updated.hasActiveFollowUp)
    }

    @Test
    fun `after-call Interested action reschedules 2 days from today and clears Due Today`() = runTest {
        val saved = repository.saveLead(
            editingLeadId = null,
            studentName = "Neha",
            phoneNumber = "9876543210",
            course = "Digital Marketing",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "Wants brochure",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )

        clock.today = "2026-11-26"
        val rescheduled = repository.applyAfterCallAction(saved.id, CallStatus.INTERESTED)
        assertEquals("2026-11-26", rescheduled?.callDate)
        assertEquals("2026-11-28", rescheduled?.followUpDate)
    }

    @Test
    fun `call back custom date & time and optional Not Connected retry work accurately`() = runTest {
        val cb = repository.saveLead(
            editingLeadId = null,
            studentName = "Rohan",
            phoneNumber = "9988776655",
            course = "MBA",
            callDateIso = "2026-11-24",
            status = CallStatus.CALL_BACK,
            notes = "Call at 6 PM",
            customFollowUpDateIso = "2026-11-29",
            customFollowUpTime = "18:00",
            scheduleRetryFollowUp = false,
        )
        assertEquals("2026-11-29", cb.followUpDate)
        assertEquals("18:00", cb.followUpTime)

        val ncNoRetry = repository.saveLead(
            editingLeadId = null,
            studentName = "Vikram",
            phoneNumber = "9000011111",
            course = "MBA",
            callDateIso = "2026-11-24",
            status = CallStatus.NOT_CONNECTED,
            notes = "No answer",
            customFollowUpDateIso = "2026-11-25",
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )
        assertNull(ncNoRetry.followUpDate)

        val ncWithRetry = repository.saveLead(
            editingLeadId = ncNoRetry.id,
            studentName = "Vikram",
            phoneNumber = "9000011111",
            course = "MBA",
            callDateIso = "2026-11-24",
            status = CallStatus.NOT_CONNECTED,
            notes = "Try tomorrow",
            customFollowUpDateIso = "2026-11-25",
            customFollowUpTime = "11:30",
            scheduleRetryFollowUp = true,
        )
        assertEquals("2026-11-25", ncWithRetry.followUpDate)
        assertEquals("11:30", ncWithRetry.followUpTime)
    }

    @Test
    fun `deduplication prevents same phone appearing twice for active follow-up`() = runTest {
        repository.saveLead(
            editingLeadId = null,
            studentName = "Aarav",
            phoneNumber = "98765-43210",
            course = "Digital Marketing",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "Initial inquiry",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )
        repository.saveLead(
            editingLeadId = null,
            studentName = "Aarav Sharma",
            phoneNumber = "9876543210",
            course = "Digital Marketing",
            callDateIso = "2026-11-24",
            status = CallStatus.CALL_BACK,
            notes = "Callback requested",
            customFollowUpDateIso = "2026-11-27",
            customFollowUpTime = "15:00",
            scheduleRetryFollowUp = false,
        )

        val leads = leadDao.getAllLeadsOnce()
        assertEquals(1, leads.size)
        assertEquals("2026-11-27", leads.first().followUpDate)
    }

    @Test
    fun `no stock courses seeded and courses support adding and editing name and price`() = runTest {
        val viewModel = SalesDiaryViewModel(repository, clock)
        advanceUntilIdle()

        // No stock courses exist initially
        assertTrue(courseDao.getAllCoursesOnce().isEmpty())
        assertEquals("", viewModel.uiState.value.form.course)

        // Add custom course with price
        assertTrue(repository.addCourse("Cloud Engineering", "25,000"))
        val created = courseDao.getAllCoursesOnce().single()
        assertEquals("Cloud Engineering", created.name)
        assertEquals("25,000", created.price)

        // Save a lead under that course
        repository.saveLead(
            editingLeadId = null,
            studentName = "Meera",
            phoneNumber = "9811122233",
            course = "Cloud Engineering",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )

        // Edit both course name and price later
        assertTrue(repository.updateCourse(created.id, "Cloud & DevOps", "29,500"))
        val updatedCourse = courseDao.getAllCoursesOnce().single()
        assertEquals("Cloud & DevOps", updatedCourse.name)
        assertEquals("29,500", updatedCourse.price)

        // Existing lead's course name stays in sync
        assertEquals("Cloud & DevOps", leadDao.getAllLeadsOnce().single().course)
    }

    @Test
    fun `export CSV and JSON backup-restore preserve all leads and custom courses with prices`() = runTest {
        repository.addCourse("Cloud Engineering", "32,000")
        repository.saveLead(
            editingLeadId = null,
            studentName = "Meera Iyer",
            phoneNumber = "9811122233",
            course = "Cloud Engineering",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "Weekend batch, wants fee details",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )

        val csv = repository.exportLeadsCsv()
        assertTrue(csv.contains("Student Name,Phone Number,Interested Course"))
        assertTrue(csv.contains("\"Weekend batch, wants fee details\""))

        val json = repository.exportBackupJson()
        leadDao.deleteAllLeads()
        courseDao.deleteAllCourses()
        assertEquals(0, leadDao.getAllLeadsOnce().size)

        val result = repository.restoreFromBackupJson(json)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().leadsRestored)
        assertEquals("Meera Iyer", leadDao.getAllLeadsOnce().first().studentName)
        assertEquals("2026-11-26", leadDao.getAllLeadsOnce().first().followUpDate)
        val restoredCourse = courseDao.getAllCoursesOnce().single()
        assertEquals("Cloud Engineering", restoredCourse.name)
        assertEquals("32,000", restoredCourse.price)

        // Verify local date utilities across month/leap-year boundaries
        assertEquals("2026-12-01", LocalDateHelper.addDays("2026-11-29", 2))
        assertEquals("2024-03-01", LocalDateHelper.addDays("2024-02-28", 2))
        assertEquals(-2, LocalDateHelper.daysBetween("2026-11-26", "2026-11-24"))
    }
}
