package edu.unicauca.multimedivida.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class ReminderPlan(
    val id: String,
    val recordId: String,
    val occurrence: String,
    val title: String,
    val message: String,
    val atEpochMillis: Long
)

/** Builds the next notification occurrence from persisted records using the device's local time zone. */
object ReminderPlanner {
    fun plans(snapshot: Snapshot, now: LocalDateTime): List<ReminderPlan> =
        if (snapshot.profile.notifications) plans(snapshot.records, now) else emptyList()

    fun plans(records: List<HealthRecord>, now: LocalDateTime): List<ReminderPlan> = buildList {
        records.asSequence().filterNot { it.archived }.forEach { record ->
            when (record.kind) {
                Kind.MEDICINE -> Schedule.next(record, now)?.let { task ->
                    add(
                        ReminderPlan(
                            id = "${record.id}:${task.key}",
                            recordId = record.id,
                            occurrence = task.key,
                            title = "Hora de tu medicamento",
                            message = listOf(record.title, record.detail).filter(String::isNotBlank).joinToString(" · "),
                            atEpochMillis = task.epoch
                        )
                    )
                }
                Kind.APPOINTMENT -> {
                    val appointment = runCatching {
                        LocalDate.parse(record.start).atTime(Schedule.times(record.times).single())
                    }.getOrNull() ?: return@forEach
                    val triggers = buildList {
                        if (record.dayBefore) add(appointment.minusDays(1))
                        if (record.reminderMinutes > 0) add(appointment.minusMinutes(record.reminderMinutes.toLong()))
                        if (!record.dayBefore && record.reminderMinutes == 0) add(appointment)
                    }.filter { it.isAfter(now) }.distinct().sorted()
                    triggers.forEach { trigger ->
                        val occurrence = appointment.toString()
                        add(
                            ReminderPlan(
                                id = "${record.id}:$trigger",
                                recordId = record.id,
                                occurrence = occurrence,
                                title = "Próxima cita",
                                message = buildString {
                                    append(record.title)
                                    if (record.place.isNotBlank()) append(" · ${record.place}")
                                    append(" · ${appointment}")
                                },
                                atEpochMillis = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            )
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}

interface ReminderScheduler {
    /** Replaces the platform's pending reminders with the next occurrences from the repository. */
    fun rebuild(repository: HealthRepository)
    fun cancel()
    fun snooze(repository: HealthRepository, recordId: String, occurrence: String)
    fun onReminderFired(recordId: String)
}

expect fun platformReminderScheduler(): ReminderScheduler
