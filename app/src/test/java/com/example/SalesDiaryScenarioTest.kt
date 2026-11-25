package com.example

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.CallStatus
import com.example.data.repository.SalesDiaryRepository
import com.example.util.ClockProvider
import com.example.util.DialerHelper
import com.example.util.LocalDateHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class MutableClock(
    var currentDateIso: String = "2026-11-24",
    var currentMillis: Long = 1_795_500_000_000L,
) : ClockProvider {
    override fun todayIso(): String = currentDateIso
    override fun nowMillis(): Long = currentMillis
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SalesDiaryScenarioTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var clock: MutableClock
    private lateinit var repository: SalesDiaryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableClock(currentDateIso = "2026-11-24")
        repository = SalesDiaryRepository(
            leadDao = db.leadDao(),
            courseDao = db.courseDao(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `scenario 1 - interested lead schedules follow-up exactly 2 days later and activates on due date`() = runTest {
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
        assertTrue(saved.hasActiveFollowUp)

        // Month boundary check (Nov 29 + 2 days = Dec 1)
        assertEquals("2026-12-01", LocalDateHelper.addDays("2026-11-29", 2))
    }

    @Test
    fun `scenario 2 - changing lead from Interested to Not Interested removes reminder`() = runTest {
        val saved = repository.saveLead(
            editingLeadId = null,
            studentName = "Priya Nair",
            phoneNumber = "9123456789",
            course = "Data Science",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "Asked for syllabus",
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
            notes = "Joined elsewhere",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )

        assertNull(updated.followUpDate)
        assertFalse(updated.hasActiveFollowUp)
    }

    @Test
    fun `scenario 3 - call back lead preserves custom date and time`() = runTest {
        val saved = repository.saveLead(
            editingLeadId = null,
            studentName = "Rohan Verma",
            phoneNumber = "9988776655",
            course = "Full Stack Development",
            callDateIso = "2026-11-24",
            status = CallStatus.CALL_BACK,
            notes = "Call after 5 PM",
            customFollowUpDateIso = "2026-11-28",
            customFollowUpTime = "17:30",
            scheduleRetryFollowUp = false,
        )

        assertEquals("2026-11-28", saved.followUpDate)
        assertEquals("17:30", saved.followUpTime)
        assertTrue(saved.hasActiveFollowUp)
    }

    @Test
    fun `scenario 4 - direct call button builds ACTION_DIAL tel intent without auto calling`() {
        val intent = DialerHelper.buildDialIntent("9876543210")
        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel:9876543210", intent.data?.toString())
    }

    @Test
    fun `scenario 5 - updating reminder to Interested reschedules 2 days from current local date`() = runTest {
        val saved = repository.saveLead(
            editingLeadId = null,
            studentName = "Neha Gupta",
            phoneNumber = "9876543210",
            course = "Digital Marketing",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "Wants fee details",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )
        assertEquals("2026-11-26", saved.followUpDate)

        // Advance clock to follow-up due date (Nov 26)
        clock.currentDateIso = "2026-11-26"
        clock.currentMillis += 86_400_000L * 2

        val rescheduled = repository.applyAfterCallAction(
            leadId = saved.id,
            newStatus = CallStatus.INTERESTED,
        )

        assertNotNull(rescheduled)
        assertEquals("2026-11-26", rescheduled!!.callDate)
        assertEquals("2026-11-28", rescheduled.followUpDate)
    }

    @Test
    fun `scenario 6 & 7 - persistent local SQLite storage survives close and reopen offline`() = runTest {
        val dbFileName = "test_sales_diary_persist.db"
        context.deleteDatabase(dbFileName)

        val firstDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFileName)
            .allowMainThreadQueries()
            .build()
        val firstRepo = SalesDiaryRepository(firstDb.leadDao(), firstDb.courseDao(), clock)
        firstRepo.saveLead(
            editingLeadId = null,
            studentName = "Kiran Patel",
            phoneNumber = "9001122334",
            course = "MBA",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "Offline test lead",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )
        firstDb.close()

        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbFileName)
            .allowMainThreadQueries()
            .build()
        val persistedLeads = reopenedDb.leadDao().getAllLeadsOnce()
        reopenedDb.close()
        context.deleteDatabase(dbFileName)

        assertEquals(1, persistedLeads.size)
        assertEquals("Kiran Patel", persistedLeads.first().studentName)
        assertEquals("2026-11-26", persistedLeads.first().followUpDate)
    }

    @Test
    fun `deduplication - student never appears twice for active follow-up`() = runTest {
        repository.saveLead(
            editingLeadId = null,
            studentName = "Aarav",
            phoneNumber = "98765-43210",
            course = "Digital Marketing",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "First call",
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
            notes = "Updated call back",
            customFollowUpDateIso = "2026-11-27",
            customFollowUpTime = "11:00",
            scheduleRetryFollowUp = false,
        )

        val allLeads = repository.leadsFlow.first()
        assertEquals(1, allLeads.size)
        assertEquals("Aarav Sharma", allLeads.first().studentName)
        assertEquals("2026-11-27", allLeads.first().followUpDate)
    }

    @Test
    fun `scenario 8 - export CSV, backup JSON, wipe, and restore JSON accurately`() = runTest {
        repository.addCourse("Cloud Engineering", "30,000")

        repository.saveLead(
            editingLeadId = null,
            studentName = "Meera Iyer",
            phoneNumber = "9811122233",
            course = "Cloud Engineering",
            callDateIso = "2026-11-24",
            status = CallStatus.INTERESTED,
            notes = "Wants weekend batch, scholarship info",
            customFollowUpDateIso = null,
            customFollowUpTime = null,
            scheduleRetryFollowUp = false,
        )

        val csv = repository.exportLeadsCsv()
        assertTrue(csv.contains("Student Name,Phone Number,Interested Course"))
        assertTrue(csv.contains("\"Wants weekend batch, scholarship info\""))

        val jsonBackup = repository.exportBackupJson()
        assertTrue(jsonBackup.contains("Meera Iyer"))
        assertTrue(jsonBackup.contains("Cloud Engineering"))

        // Wipe database completely
        db.leadDao().deleteAllLeads()
        db.courseDao().deleteAllCourses()
        assertEquals(0, db.leadDao().getAllLeadsOnce().size)

        // Restore from JSON backup
        val restoreResult = repository.restoreFromBackupJson(jsonBackup)
        assertTrue(restoreResult.isSuccess)
        assertEquals(1, restoreResult.getOrThrow().leadsRestored)

        val restoredLeads = db.leadDao().getAllLeadsOnce()
        val restoredCourses = db.courseDao().getAllCoursesOnce()
        assertEquals(1, restoredLeads.size)
        assertEquals("Meera Iyer", restoredLeads.first().studentName)
        assertEquals("2026-11-26", restoredLeads.first().followUpDate)
        assertTrue(restoredCourses.any { it.name == "Cloud Engineering" && it.price == "30,000" })
    }
}
