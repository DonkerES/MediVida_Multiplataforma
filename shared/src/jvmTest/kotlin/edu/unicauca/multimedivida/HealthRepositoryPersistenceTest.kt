package edu.unicauca.multimedivida

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import edu.unicauca.multimedivida.data.HealthRecord
import edu.unicauca.multimedivida.data.Kind
import edu.unicauca.multimedivida.data.Profile
import edu.unicauca.multimedivida.data.SqlDelightHealthRepository
import edu.unicauca.multimedivida.data.Status
import edu.unicauca.multimedivida.data.createHealthDriver
import edu.unicauca.multimedivida.db.HealthDatabase
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthRepositoryPersistenceTest {
    @Test
    fun recordsProfileAndCompletionsSurviveDriverReopen() {
        val file = File.createTempFile("multimedivida", ".db").also { it.delete() }
        try {
            val firstDriver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = HealthDatabase.Schema)
            val firstRepository = SqlDelightHealthRepository(firstDriver).apply { activate() }
            val appointmentTime = LocalDateTime.now().minusHours(1).withSecond(0).withNano(0)
            val record = HealthRecord(
                kind = Kind.MEAL,
                title = "Desayuno",
                times = appointmentTime.toLocalTime().toString(),
                start = appointmentTime.toLocalDate().toString(),
                foodItems = "Avena"
            )
            val medicine = HealthRecord(
                kind = Kind.MEDICINE,
                title = "Vitamina",
                detail = "1 cápsula",
                times = appointmentTime.toLocalTime().toString(),
                start = appointmentTime.toLocalDate().toString(),
                stockRemaining = 10
            )
            val appointment = HealthRecord(
                kind = Kind.APPOINTMENT,
                title = "Control médico",
                times = "23:59",
                start = LocalDate.now().toString(),
                place = "Consulta"
            )
            firstRepository.save(record)
            firstRepository.save(record.copy(title = "Desayuno actualizado"))
            firstRepository.save(medicine)
            firstRepository.save(appointment)
            firstRepository.save(appointment.copy(place = "Consultorio 2"))
            firstRepository.saveProfile(Profile(name = "Paciente", weight = "68"))
            val occurrence = appointmentTime.toString()
            firstRepository.complete(record.id, occurrence, Status.TAKEN)
            firstRepository.complete(medicine.id, occurrence, Status.TAKEN)
            firstRepository.complete(medicine.id, occurrence, Status.TAKEN)
            firstDriver.close()

            val secondDriver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = HealthDatabase.Schema)
            val secondRepository = SqlDelightHealthRepository(secondDriver).apply { activate() }
            val restored = secondRepository.snapshot()
            assertEquals(3, restored.records.size)
            assertEquals("Desayuno actualizado", restored.records.single { it.id == record.id }.title)
            assertEquals("Avena", restored.records.single { it.id == record.id }.foodItems)
            assertEquals(9, restored.records.single { it.id == medicine.id }.stockRemaining)
            assertEquals("Consultorio 2", restored.records.single { it.id == appointment.id }.place)
            assertEquals("Paciente", restored.profile.name)
            assertEquals(2, restored.completions.size)

            secondRepository.delete(record.id)
            secondRepository.delete(appointment.id)
            assertTrue(secondRepository.snapshot().records.single { it.id == record.id }.archived)
            assertTrue(secondRepository.snapshot().records.single { it.id == appointment.id }.archived)
            assertFalse(secondRepository.snapshot().completions.isEmpty())
            secondDriver.close()
        } finally {
            file.delete()
        }
    }

    @Test
    fun desktopDriverUsesPersistentUserDirectoryAndReopensSchema() {
        val userHome = kotlin.io.path.createTempDirectory("multimedivida-home").toFile()
        val previousHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", userHome.absolutePath)
            createHealthDriver().close()
            val databaseFile = File(userHome, ".multimedivida/multimedivida.db")
            assertTrue(databaseFile.isFile)
            assertTrue(databaseFile.length() > 0L)
            createHealthDriver().close()
            assertTrue(databaseFile.isFile)
        } finally {
            if (previousHome == null) System.clearProperty("user.home") else System.setProperty("user.home", previousHome)
            File(userHome, ".multimedivida/multimedivida.db").delete()
            File(userHome, ".multimedivida").delete()
            userHome.delete()
        }
    }
}
