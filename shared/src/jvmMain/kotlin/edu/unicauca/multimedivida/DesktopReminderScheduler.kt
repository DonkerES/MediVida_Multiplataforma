package edu.unicauca.multimedivida.data

import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.awt.EventQueue
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

private val desktopScheduler by lazy { DesktopReminderScheduler() }
private val desktopTray by lazy { DesktopNotificationTray() }
private val isWindowsDesktop = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

actual fun platformReminderScheduler(): ReminderScheduler = desktopScheduler

private class DesktopReminderScheduler : ReminderScheduler {
    private val lock = Any()
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "multimedivida-reminders").apply { isDaemon = true }
    }
    private val pending = mutableMapOf<String, ScheduledFuture<*>>()
    private val snoozed = mutableMapOf<String, ReminderPlan>()

    init {
        if (!isWindowsDesktop) {
            runCatching { desktopTray.initialize() }
                .onFailure { System.err.println("No se pudo inicializar la bandeja de MediVida: ${it.message}") }
        }
    }

    override fun rebuild(repository: HealthRepository) {
        val snapshot = repository.snapshot()
        val plans = ReminderPlanner.plans(snapshot, LocalDateTime.now()).associateBy { it.id }.toMutableMap()
        synchronized(lock) {
            pending.values.forEach { it.cancel(false) }
            pending.clear()
            if (snapshot.profile.notifications) snoozed.values.forEach { plans[it.id] = it }
            plans.values.forEach(::schedule)
        }
    }

    private fun schedule(plan: ReminderPlan) {
        val delay = (plan.atEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        pending[plan.id] = executor.schedule({
            postDesktopNotification(plan)
            synchronized(lock) {
                pending.remove(plan.id)
                snoozed.remove(plan.recordId)
            }
            runCatching { onReminderFired(plan.recordId) }
        }, delay, TimeUnit.MILLISECONDS)
    }

    override fun cancel() = synchronized(lock) {
        pending.values.forEach { it.cancel(false) }
        pending.clear()
        snoozed.clear()
    }

    override fun snooze(repository: HealthRepository, recordId: String, occurrence: String) {
        val snapshot = repository.snapshot()
        if (!snapshot.profile.notifications) return
        val record = snapshot.records.firstOrNull { it.id == recordId && !it.archived } ?: return
        val at = LocalDateTime.now().plusMinutes(10)
        val plan = ReminderPlan(
            id = "$recordId:snooze",
            recordId = recordId,
            occurrence = occurrence,
            title = if (record.kind == Kind.MEDICINE) "Recordatorio de medicamento" else "Recordatorio de cita",
            message = listOf(record.title, record.detail).filter(String::isNotBlank).joinToString(" · "),
            atEpochMillis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        synchronized(lock) { snoozed[recordId] = plan }
        synchronized(lock) {
            pending.remove(plan.id)?.cancel(false)
            schedule(plan)
        }
    }

    override fun onReminderFired(recordId: String) {
        val repository = persistentHealthRepository()
        rebuild(repository)
    }
}

private fun postDesktopNotification(plan: ReminderPlan) {
    val desktopPlan = if (plan.title == "Hora de tu medicamento") {
        val medication = plan.message.substringBefore(" · ")
        val detail = plan.message.substringAfter(" · ", "")
        plan.copy(message = "Es momento de tomar $medication." + if (detail.isBlank()) "" else " $detail")
    } else plan
    runCatching {
        if (isWindowsDesktop) {
            System.err.println("Recordatorio pendiente sin notificación: los avisos no están disponibles en Desktop para Windows.")
        } else {
            desktopTray.notify(desktopPlan)
        }
    }.onFailure { System.err.println("No se pudo mostrar la notificación de MediVida: ${it.message}") }
}

private class DesktopNotificationTray {
    private val icon: TrayIcon? by lazy {
        check(SystemTray.isSupported()) { "Este sistema no reporta soporte para la bandeja de notificaciones." }
        val image = DesktopReminderScheduler::class.java.getResourceAsStream("/multimedivida.png")?.use { ImageIO.read(it) }
            ?: BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB).also {
                System.err.println("No se encontró el icono de notificación de MediVida; se usará uno vacío.")
            }
        TrayIcon(image, "MediVida").apply {
            isImageAutoSize = true
            SystemTray.getSystemTray().add(this)
            if (EventQueue.isDispatchThread()) EventQueue.invokeLater { }
            else EventQueue.invokeAndWait { }
        }
    }

    fun initialize() {
        icon
    }

    fun notify(plan: ReminderPlan) {
        val trayIcon = runCatching { icon }.getOrElse {
            System.err.println("No se pudo entregar el recordatorio de MediVida: ${it.message}")
            return
        } ?: return
        trayIcon.displayMessage(plan.title, plan.message, TrayIcon.MessageType.INFO)
    }
}
