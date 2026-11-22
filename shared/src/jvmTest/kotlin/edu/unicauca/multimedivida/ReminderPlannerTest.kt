package edu.unicauca.multimedivida

import edu.unicauca.multimedivida.data.HealthRecord
import edu.unicauca.multimedivida.data.Kind
import edu.unicauca.multimedivida.data.ReminderPlanner
import edu.unicauca.multimedivida.data.Snapshot
import edu.unicauca.multimedivida.data.Profile
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderPlannerTest {
    @Test
    fun disabledProfileDoesNotCreateReminderPlans() {
        val now = LocalDateTime.of(2026, 6, 2, 7, 0)
        val record = HealthRecord(
            id = "medicine",
            kind = Kind.MEDICINE,
            title = "Medicamento",
            detail = "1 dosis",
            times = "08:00",
            start = now.toLocalDate().toString()
        )
        assertTrue(ReminderPlanner.plans(Snapshot(records = listOf(record), profile = Profile(notifications = false)), now).isEmpty())
    }

    @Test
    fun medicineReminderUsesNextStoredTimeAndSkipsArchivedRecords() {
        val now = LocalDateTime.of(2026, 11, 19, 10, 0)
        val medicine = HealthRecord(
            id = "medicine",
            kind = Kind.MEDICINE,
            title = "Acetaminofem",
            detail = "1 tableta",
            times = "08:00,11:30",
            start = "2026-11-19"
        )
        val plans = ReminderPlanner.plans(listOf(medicine, medicine.copy(id = "archived", archived = true)), now)

        assertEquals(1, plans.size)
        assertEquals("medicine", plans.single().recordId)
        assertEquals("2026-11-19T11:30", plans.single().occurrence)
        assertTrue(plans.single().message.contains("1 tableta"))
    }

    @Test
    fun medicineRemindersRespectTreatmentStartAndEndDates() {
        val now = LocalDateTime.of(2026, 11, 19, 10, 0)
        val base = HealthRecord(
            id = "bounded",
            kind = Kind.MEDICINE,
            title = "Tratamiento",
            detail = "1 dosis",
            times = "11:30",
            start = "2026-11-20",
            end = "2026-11-21"
        )

        val beforeStart = ReminderPlanner.plans(listOf(base), now)
        val afterEnd = ReminderPlanner.plans(listOf(base.copy(start = "2026-11-01", end = "2026-11-18")), now)

        assertEquals("2026-11-20T11:30", beforeStart.single().occurrence)
        assertTrue(afterEnd.isEmpty())
    }

    @Test
    fun appointmentPlannerCreatesSelectedAdvanceReminders() {
        val now = LocalDateTime.of(2026, 11, 19, 9, 0)
        val appointment = HealthRecord(
            id = "appointment",
            kind = Kind.APPOINTMENT,
            title = "Control médico",
            times = "11:00",
            start = "2026-11-20",
            reminderMinutes = 60,
            dayBefore = true
        )

        val plans = ReminderPlanner.plans(listOf(appointment), now).sortedBy { it.atEpochMillis }

        assertEquals(2, plans.size)
        assertTrue(plans.first().message.contains("Control médico"))
        assertEquals("appointment", plans.first().recordId)
    }

    @Test
    fun visualTimePickerStores24HourValueWithoutTextEntry() {
        assertEquals("00:05", storedTimeForPicker(0, 5))
        assertEquals("13:30", storedTimeForPicker(13, 30))
        assertEquals("23:59", storedTimeForPicker(23, 59))
    }
}
