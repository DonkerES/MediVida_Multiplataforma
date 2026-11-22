package edu.unicauca.multimedivida

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

import edu.unicauca.multimedivida.data.HealthRecord
import edu.unicauca.multimedivida.data.Kind
import edu.unicauca.multimedivida.data.Schedule
import edu.unicauca.multimedivida.data.Snapshot
import edu.unicauca.multimedivida.data.Status
import edu.unicauca.multimedivida.data.Task
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun adherenceColor(percent: Int): Color = when {
    percent >= 100 -> Color(0xFF16A765)
    percent > 70 -> Color(0xFFF2C230)
    percent > 50 -> Color(0xFFF28C28)
    else -> Color(0xFFE5484D)
}

@Composable
internal fun TodayScreen(data: Snapshot, now: LocalDateTime, busy: Boolean, vm: HealthViewModel, navigate: (String) -> Unit, addRecord: () -> Unit) {
    val allToday = Schedule.onDate(data.records, now.toLocalDate())
    val medicationTasks = allToday.filter { it.record.kind == Kind.MEDICINE }
    val unfinishedMedicationTasks = medicationTasks.filter { task -> data.completions.none { it.recordId == task.record.id && it.occurrence == task.key } }
    val adherence = Schedule.adherence(medicationTasks, data.completions, now)
    val adherenceText = adherence?.let { "$it%" } ?: "—"
    val adherenceTint = adherence?.let(::adherenceColor) ?: MutedText
    val medicines = data.records.filter { it.kind == Kind.MEDICINE && !it.archived }
    val upcomingAppointments = data.records.filter { it.kind == Kind.APPOINTMENT && !it.archived }.mapNotNull { record ->
        runCatching { record to LocalDate.parse(record.start).atTime(Schedule.times(record.times).single()) }.getOrNull()
    }.filter { it.second >= now }.sortedBy { it.second }
    val nextAppointment = upcomingAppointments.firstOrNull()
    val upcomingAppointmentCount = upcomingAppointments.size
    val stockWarnings = medicines.filter { medicine ->
        medicine.stockRemaining != null && medicine.stockRemaining <= medicine.stockAlertAt
    }.sortedBy { it.stockRemaining }
    var refillTarget by remember { mutableStateOf<HealthRecord?>(null) }
    val firstName = data.profile.name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    val todayLabel = now.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale.forLanguageTag("es")))
    val greeting = if (firstName.isBlank()) greeting(now.hour) else "${greeting(now.hour)}, $firstName 👋"

    val isDesktop = remember { getPlatform().name.startsWith("Java") }
    if (isDesktop) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val medicationColumns = when {
                maxWidth >= 1220.dp -> 3
                maxWidth >= 760.dp -> 2
                else -> 1
            }
            val infoColumns = when {
                maxWidth >= 1120.dp -> 3
                maxWidth >= 720.dp -> 2
                else -> 1
            }
            val medicationCardWidth = ((maxWidth - 32.dp - 16.dp * (medicationColumns - 1)) / medicationColumns).coerceAtMost(520.dp)
            val infoCardWidth = ((maxWidth - 32.dp - 16.dp * (infoColumns - 1)) / infoColumns).coerceAtMost(520.dp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                DesktopDashboardHeader(greeting, todayLabel, unfinishedMedicationTasks.size, upcomingAppointmentCount, adherenceText, addRecord)
                MedicationGridToday(medicationTasks, data, now, busy, vm, medicationCardWidth)
                DesktopInfoCards(
                    medicines, medicationTasks, data, now, adherence, adherenceText, adherenceTint,
                    nextAppointment, stockWarnings, onRefill = { refillTarget = it }, navigate = navigate,
                    cardWidth = infoCardWidth
                )
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DashboardHeader(greeting, todayLabel, unfinishedMedicationTasks.size, upcomingAppointmentCount, adherenceText, addRecord)
            AdherenceToday(medicines, medicationTasks, data, now, adherence, adherenceText, adherenceTint, navigate)
            MedicationToday(medicationTasks, data, now, busy, vm)
            NextAppointment(nextAppointment, now, navigate)
            StockWarnings(stockWarnings, medicines, onRefill = { refillTarget = it }, navigate = navigate)
        }
    }
    refillTarget?.let { medicine ->
        StockRefillDialog(medicine, busy, onDismiss = { refillTarget = null }) { added ->
            vm.save(medicine.copy(stockRemaining = (medicine.stockRemaining ?: 0) + added, stockTotal = (medicine.stockTotal ?: medicine.stockRemaining ?: 0) + added)) { refillTarget = null }
        }
    }
}

@Composable
private fun DesktopDashboardHeader(greeting: String, date: String, pending: Int, appointments: Int, adherence: String, addRecord: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.horizontalGradient(HomeGradient)).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(date.replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall)
                Text(greeting, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            androidx.compose.material3.OutlinedButton(onClick = addRecord) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Agregar registro")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryBox("$pending", "dosis pendientes", Modifier.weight(1f))
            SummaryBox("$appointments", "citas próximas", Modifier.weight(1f))
            SummaryBox(adherence, "adherencia", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MedicationGridToday(tasks: List<Task>, data: Snapshot, now: LocalDateTime, busy: Boolean, vm: HealthViewModel, cardWidth: Dp) {
    SectionHeading("Medicamentos de hoy", "") { }
    if (tasks.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Empty("No hay medicamentos programados para hoy.") }
        return
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        tasks.forEach { task ->
            MedicationDoseCard(task, data, now, busy, vm, Modifier.width(cardWidth))
        }
    }
}

@Composable
private fun DesktopInfoCards(
    medicines: List<HealthRecord>, tasks: List<Task>, data: Snapshot, now: LocalDateTime,
    adherence: Int?, adherenceText: String, adherenceTint: Color,
    next: Pair<HealthRecord, LocalDateTime>?, stockWarnings: List<HealthRecord>,
    onRefill: (HealthRecord) -> Unit, navigate: (String) -> Unit, cardWidth: Dp
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AdherenceToday(medicines, tasks, data, now, adherence, adherenceText, adherenceTint, navigate, Modifier.width(cardWidth).heightIn(min = 230.dp), desktopCard = true)
        NextAppointment(next, now, navigate, Modifier.width(cardWidth).heightIn(min = 230.dp), desktopCard = true)
        StockWarnings(stockWarnings, medicines, onRefill, navigate, Modifier.width(cardWidth).heightIn(min = 230.dp), desktopCard = true)
    }
}

@Composable
private fun HomeInfoCard(modifier: Modifier, title: String, action: String = "", onAction: () -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = DarkText, fontWeight = FontWeight.Bold)
                if (action.isNotBlank()) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(action, color = PrimaryBlue, style = MaterialTheme.typography.labelMedium)
                }
            }
            content()
        }
    }
}

@Composable
private fun DashboardHeader(greeting: String, date: String, pending: Int, appointments: Int, adherence: String, addRecord: () -> Unit) {
    Hero(greeting, date) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            androidx.compose.material3.OutlinedButton(onClick = addRecord) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Agregar registro")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryBox("$pending", "dosis pendientes", Modifier.weight(1f))
            SummaryBox("$appointments", "citas próximas", Modifier.weight(1f))
            SummaryBox(adherence, "adherencia", Modifier.weight(1f))
        }
    }
}

@Composable
private fun AdherenceToday(
    medicines: List<HealthRecord>,
    tasks: List<Task>,
    data: Snapshot,
    now: LocalDateTime,
    adherence: Int?,
    adherenceText: String,
    adherenceTint: Color,
    navigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    desktopCard: Boolean = false
) {
    if (desktopCard) {
        HomeInfoCard(modifier, "Adherencia de hoy", "Ver medicación →", { navigate("Medicación") }) {
            AdherenceBody(medicines, tasks, data, now, adherence, adherenceText, adherenceTint)
        }
    } else {
        Column(modifier) {
            SectionHeading("Adherencia de hoy", "Ver medicación →") { navigate("Medicación") }
            Box(Modifier.padding(horizontal = 16.dp)) {
                Panel { AdherenceBody(medicines, tasks, data, now, adherence, adherenceText, adherenceTint) }
            }
        }
    }
}

@Composable
private fun AdherenceBody(medicines: List<HealthRecord>, tasks: List<Task>, data: Snapshot, now: LocalDateTime, adherence: Int?, adherenceText: String, adherenceTint: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ProgressCircle((adherence ?: 0) / 100f, adherenceTint, Color(0xFFE4EBF2), adherenceText, "HOY", MutedText)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val tracked = medicines.map { med -> med to tasks.filter { it.record.id == med.id && !it.dateTime.isAfter(now) } }.filter { it.second.isNotEmpty() }
            if (tracked.isEmpty()) Text("No hay dosis vencidas para medir hoy.", color = MutedText)
            tracked.forEach { (medicine, doses) ->
                val taken = doses.count { dose -> data.completions.any { it.recordId == dose.record.id && it.occurrence == dose.key && it.status == Status.TAKEN } }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(medicine.title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("$taken/${doses.size}", style = MaterialTheme.typography.labelSmall, color = MutedText)
                    }
                    val tint = adherenceColor(taken * 100 / doses.size)
                    LinearProgressIndicator(progress = { taken.toFloat() / doses.size }, modifier = Modifier.fillMaxWidth().height(5.dp), color = tint, trackColor = tint.copy(alpha = .14f))
                }
            }
        }
    }
}

@Composable
private fun MedicationToday(tasks: List<Task>, data: Snapshot, now: LocalDateTime, busy: Boolean, vm: HealthViewModel) {
    SectionHeading("Medicamentos de hoy", "") { }
    if (tasks.isEmpty()) {
        Box(Modifier.padding(horizontal = 16.dp)) { Empty("No hay medicamentos programados para hoy.") }
        return
    }
    tasks.forEach { task -> MedicationDoseCard(task, data, now, busy, vm, Modifier.fillMaxWidth()) }
}

@Composable
private fun MedicationDoseCard(task: Task, data: Snapshot, now: LocalDateTime, busy: Boolean, vm: HealthViewModel, modifier: Modifier) {
    val completion = data.completions.firstOrNull { it.recordId == task.record.id && it.occurrence == task.key }
    val status = when {
        completion?.status == Status.TAKEN -> "Tomada"
        completion?.status == Status.SKIPPED -> "Omitida"
        task.dateTime.isAfter(now) -> "Próxima"
        Duration.between(task.dateTime, now).toMinutes() >= 60 -> "Atrasada"
        else -> "Pendiente"
    }
    val statusColor = when (status) {
        "Tomada" -> Color(0xFF159564)
        "Atrasada" -> Color(0xFFD97706)
        "Omitida" -> MutedText
        else -> MedicationAccent
    }
    Box(modifier) {
        Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(42.dp).background(MedicationAccent.copy(alpha = .1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Medication, null, tint = MedicationAccent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(task.record.title, Modifier.weight(1f), color = DarkText, fontWeight = FontWeight.Bold)
                        Text(status.uppercase(), color = statusColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    if (task.record.detail.isNotBlank()) Text(task.record.detail, color = MutedText, style = MaterialTheme.typography.bodySmall)
                    Text("${task.dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${task.record.unitsPerDose} unidad(es) por dosis", color = MedicationAccent, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (completion == null) {
                Button(
                    enabled = !busy && !task.dateTime.isAfter(now),
                    onClick = { vm.complete(task, Status.TAKEN) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (task.dateTime.isAfter(now)) "Próxima dosis" else "Marcar como tomada") }
            } else if (completion.status == Status.TAKEN) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF159564), modifier = Modifier.size(18.dp))
                    Text("Registrada en tu historial", Modifier.padding(start = 6.dp), color = Color(0xFF159564), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun NextAppointment(
    next: Pair<HealthRecord, LocalDateTime>?, now: LocalDateTime, navigate: (String) -> Unit,
    modifier: Modifier = Modifier, desktopCard: Boolean = false
) {
    if (desktopCard) {
        HomeInfoCard(modifier, "Próxima cita", "Ver citas →", { navigate("Citas") }) { AppointmentContents(next, now) }
    } else {
        Column(modifier) {
            SectionHeading("Próxima cita", "Ver citas →") { navigate("Citas") }
            Box(Modifier.padding(horizontal = 16.dp)) {
                if (next == null) Empty("No tienes citas próximas registradas.") else Panel { AppointmentContents(next, now) }
            }
        }
    }
}

@Composable
private fun AppointmentContents(next: Pair<HealthRecord, LocalDateTime>?, now: LocalDateTime) {
    if (next == null) {
        Text("No tienes citas próximas registradas.", color = MutedText)
    } else {
        val (record, dateTime) = next
        Row(Modifier.fillMaxWidth().background(Color(0xFFEFFFF5), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.size(width = 54.dp, height = 58.dp).background(AppointmentAccent, RoundedCornerShape(10.dp)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(dateTime.dayOfMonth.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(dateTime.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.title, color = DarkText, fontWeight = FontWeight.Bold)
                if (record.place.isNotBlank()) Text(record.place, color = MutedText, style = MaterialTheme.typography.bodySmall)
                Text("${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))} · ${appointmentCountdown(now, dateTime)}", color = AppointmentAccent, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StockWarnings(
    warnings: List<HealthRecord>, medicines: List<HealthRecord>, onRefill: (HealthRecord) -> Unit, navigate: (String) -> Unit,
    modifier: Modifier = Modifier, desktopCard: Boolean = false
) {
    if (desktopCard) {
        HomeInfoCard(modifier, "Avisos de stock", "Gestionar →", { navigate("Medicación") }) {
            StockWarningContents(warnings, medicines, onRefill, nestedPanels = false)
        }
    } else {
        Column(modifier) {
            SectionHeading("Avisos de stock", "Gestionar →") { navigate("Medicación") }
            if (warnings.isEmpty()) {
                Box(Modifier.padding(horizontal = 16.dp)) { Empty(if (medicines.any { it.stockRemaining != null }) "No hay medicamentos por debajo de su umbral." else "Activa el inventario en un medicamento para controlar existencias.") }
            } else StockWarningContents(warnings, medicines, onRefill, nestedPanels = true)
        }
    }
}

@Composable
private fun StockWarningContents(warnings: List<HealthRecord>, medicines: List<HealthRecord>, onRefill: (HealthRecord) -> Unit, nestedPanels: Boolean) {
    if (warnings.isEmpty()) {
        Text(if (medicines.any { it.stockRemaining != null }) "No hay medicamentos por debajo de su umbral." else "Activa el inventario en un medicamento para controlar existencias.", color = MutedText)
    } else warnings.forEach { medicine ->
        val warning: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().background(Color(0xFFFFF6E8), RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFB86A00))
                Column(Modifier.weight(1f)) {
                    Text(medicine.title, color = Color(0xFF8A4B00), fontWeight = FontWeight.SemiBold)
                    Text("Quedan ${medicine.stockRemaining} unidades", color = Color(0xFF8A4B00), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onRefill(medicine) }) { Text("Reponer", color = Color(0xFF9A5600)) }
            }
        }
        if (nestedPanels) Box(Modifier.padding(horizontal = 16.dp)) { Panel { warning() } } else warning()
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = DarkText, fontWeight = FontWeight.Bold)
        if (action.isNotBlank()) TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(action, color = PrimaryBlue, style = MaterialTheme.typography.labelMedium) }
    }
}

private fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

private fun appointmentCountdown(now: LocalDateTime, appointment: LocalDateTime): String = when (val days = Duration.between(now, appointment).toDays()) {
    0L -> "hoy"
    1L -> "en 1 día"
    else -> "en $days días"
}

@Composable
private fun StockRefillDialog(record: HealthRecord, busy: Boolean, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var amount by remember(record.id) { mutableStateOf("") }
    val added = amount.toIntOrNull()
    val available = record.stockRemaining ?: 0
    val total = record.stockTotal ?: available
    val valid = added != null && added > 0 && available + added <= 100000 && total + added <= 100000
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reponer ${record.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Disponibles ahora: $available")
                OutlinedTextField(
                    amount,
                    { amount = it.filter(Char::isDigit).take(6) },
                    label = { Text("Unidades que agregas") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = { Button(enabled = !busy && valid, onClick = { added?.let(onSave) }) { Text(if (busy) "Guardando…" else "Guardar stock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
internal fun TaskCard(task: Task, data: Snapshot, now: LocalDateTime, busy: Boolean, vm: HealthViewModel) {
    val completed = data.completions.firstOrNull { it.recordId == task.record.id && it.occurrence == task.key }
    val accent = accentFor(task.record.kind)
    var confirm by remember { mutableStateOf<Status?>(null) }
    Panel {
        Text("${task.dateTime.toLocalTime()} · ${kindLabel(task.record.kind)}", color = accent, fontWeight = FontWeight.Bold)
        Text(task.record.title, style = MaterialTheme.typography.titleLarge, color = DarkText)
        if (task.record.detail.isNotBlank()) Text(task.record.detail)
        if (task.record.kind == Kind.MEAL) {
            val restrictions = data.records.filter { it.kind == Kind.RESTRICTION && !it.archived && it.level in listOf("Restringido", "Prohibido") }
            if (restrictions.isNotEmpty()) Text("Evitar: ${restrictions.joinToString { it.title }}", color = Color(0xFFB3261E))
        }
        if (task.record.place.isNotBlank()) Text(task.record.place)
        if (completed != null) Text(if (completed.status == Status.TAKEN) "✓ Realizada" else "Omitida", fontWeight = FontWeight.Bold)
        else if (task.dateTime.isAfter(now)) Text("Programada", color = MutedText)
        else {
            Text("Pendiente de confirmar · ${task.dateTime.toLocalTime()}", color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { confirm = Status.TAKEN }) { Text(if (task.record.kind == Kind.MEDICINE) "Tomar ahora" else "Realizada") }
                TextButton(enabled = !busy, onClick = { confirm = Status.SKIPPED }) { Text("Omitir") }
            }
            TextButton(enabled = !busy, onClick = { vm.snooze(task) }) { Text("Recordar en 10 min") }
        }
    }
    confirm?.let { status ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (status == Status.TAKEN) "Confirmar registro" else "Confirmar omisión") },
            text = { Text("${task.record.title} · ${task.dateTime.toLocalTime()}\nConfirma solo si corresponde a lo que hiciste; quedará guardado en el historial.") },
            confirmButton = { Button(onClick = { vm.complete(task, status); confirm = null }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } }
        )
    }
}
