package edu.unicauca.multimedivida.data

import app.cash.sqldelight.db.SqlDriver
import edu.unicauca.multimedivida.db.HealthDatabase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime

/** Local, persistent store shared by the Android and Desktop front ends. */
class SqlDelightHealthRepository internal constructor(private val driverFactory: () -> SqlDriver) : HealthRepository {
    constructor(driver: SqlDriver) : this({ driver })
    private val db by lazy { HealthDatabase(driverFactory()) }
    private val q get() = db.healthQueries
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val sync = MutableStateFlow("Guardado local · SQLDelight")
    private var active = true
    private val owner = "local"

    override fun user(): String? = if (active) owner else null
    fun activate() { active = true; changes.tryEmit(Unit) }
    override fun logout() { active = false; changes.tryEmit(Unit) }
    private fun requireActive() { check(active) { "Abre el modo local para consultar tus datos." } }

    override fun snapshot(): Snapshot {
        requireActive()
        val records = q.selectRecords().executeAsList().map { r ->
            HealthRecord(r.id, Kind.valueOf(r.kind), r.title, r.detail, r.times, r.start, r.end, r.place,
                r.level, r.reminderMinutes.toInt(), r.dayBefore != 0L, r.archived != 0L,
                r.stockRemaining?.toInt(), r.unitsPerDose.toInt(), r.stockAlertAt.toInt(), r.stockTotal?.toInt(), r.foodItems)
        }
        val completions = q.selectCompletions().executeAsList().map { c ->
            Completion(c.recordId, c.occurrence, Status.valueOf(c.status), c.at)
        }
        val storedProfile = q.selectProfile().executeAsOneOrNull()?.let { r ->
            Profile(r.name, r.age, r.weight, r.blood, r.allergies, r.contact, r.phone, r.notifications != 0L)
        } ?: Profile()
        val p = if (storedProfile.name.trim().equals("Modo", ignoreCase = true) ||
            storedProfile.name.trim().equals("Modo local", ignoreCase = true) ||
            storedProfile.name.trim().equals("Modo de prueba", ignoreCase = true)
        ) storedProfile.copy(name = "") else storedProfile
        return Snapshot(records, completions, p)
    }

    override fun save(record: HealthRecord) {
        requireActive(); Schedule.validate(record)
        val r = record.copy(times = Schedule.times(record.times).joinToString(",") { it.toString() })
        val old = q.selectRecords().executeAsList().firstOrNull { it.id == r.id }
        require(old == null || (old.archived == 0L && old.kind == r.kind.name)) { "Este registro está archivado." }
        q.upsertRecord(r.id, r.kind.name, r.title, r.detail, r.times, r.start, r.end, r.place, r.level,
            r.reminderMinutes.toLong(), if (r.dayBefore) 1L else 0L, if (r.archived) 1L else 0L,
            r.stockRemaining?.toLong(), r.unitsPerDose.toLong(), r.stockAlertAt.toLong(), r.stockTotal?.toLong(), r.foodItems)
        changes.tryEmit(Unit)
    }

    override fun delete(recordId: String) {
        requireActive()
        require(q.selectRecords().executeAsList().any { it.id == recordId }) { "Registro no disponible." }
        q.archiveRecord(recordId)
        changes.tryEmit(Unit)
    }

    override fun complete(recordId: String, occurrence: String, status: Status) {
        requireActive()
        val snap = snapshot(); val record = snap.records.firstOrNull { it.id == recordId && !it.archived } ?: error("Registro no disponible.")
        val time = LocalDateTime.parse(occurrence)
        require(!time.isAfter(LocalDateTime.now())) { "La hora de esta tarea todavía no llegó." }
        require(Schedule.onDate(listOf(record), time.toLocalDate()).any { it.key == occurrence }) { "Horario no válido." }
        var inserted = false
        db.transaction {
            if (q.selectCompletions().executeAsList().any { it.recordId == recordId && it.occurrence == occurrence }) return@transaction
            q.insertCompletion(recordId, occurrence, status.name, System.currentTimeMillis())
            if (status == Status.TAKEN && record.kind == Kind.MEDICINE && record.stockRemaining != null) {
                val updated = record.copy(stockRemaining = Schedule.stockAfterDose(record))
                q.upsertRecord(updated.id, updated.kind.name, updated.title, updated.detail, updated.times, updated.start,
                    updated.end, updated.place, updated.level, updated.reminderMinutes.toLong(), if (updated.dayBefore) 1L else 0L,
                    if (updated.archived) 1L else 0L, updated.stockRemaining?.toLong(), updated.unitsPerDose.toLong(),
                    updated.stockAlertAt.toLong(), updated.stockTotal?.toLong(), updated.foodItems)
            }
            inserted = true
        }
        if (inserted) changes.tryEmit(Unit)
    }

    override fun saveProfile(profile: Profile) {
        requireActive()
        require(profile.name.trim().length in 2..100) { "Escribe tu nombre." }
        require(profile.age.isBlank() || (profile.age.toIntOrNull() ?: -1) in 0..130) { "Edad no válida." }
        require(profile.weight.isBlank() || (profile.weight.toDoubleOrNull() ?: -1.0) in 0.1..500.0) { "Peso no válido." }
        require(profile.allergies.length <= 1000 && profile.contact.length <= 100 && profile.phone.length <= 30) { "Texto demasiado largo." }
        val p = profile.copy(name = profile.name.trim())
        q.upsertProfile(p.name, p.age, p.weight, p.blood, p.allergies, p.contact, p.phone, if (p.notifications) 1L else 0L)
        changes.tryEmit(Unit)
    }

    override fun register(name: String, email: String, password: String) = error("El registro con cuenta requiere un servicio de autenticación configurado.")
    override fun login(email: String, password: String) = error("El inicio con cuenta requiere un servicio de autenticación configurado.")
    override fun resetPassword(email: String) = error("El restablecimiento requiere un servicio de autenticación configurado.")
    override fun changePassword(current: String, replacement: String) = error("El cambio de contraseña requiere un servicio de autenticación configurado.")
}

private val localRepository: SqlDelightHealthRepository by lazy { SqlDelightHealthRepository { createHealthDriver() } }
fun persistentHealthRepository(): SqlDelightHealthRepository = localRepository
