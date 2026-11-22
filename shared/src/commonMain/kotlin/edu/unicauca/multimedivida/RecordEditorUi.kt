package edu.unicauca.multimedivida

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import edu.unicauca.multimedivida.data.HealthRecord
import edu.unicauca.multimedivida.data.Kind
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordEditor(kind: Kind, existing: HealthRecord?, busy: Boolean, close: () -> Unit, vm: HealthViewModel) {
    val base = remember(existing?.id, kind) { existing ?: HealthRecord(kind = kind, title = "") }
    val initialTimes = remember(existing?.id, kind) {
        base.times.split(',').map { it.trim() }.filter { it.matches(Regex("\\d{2}:\\d{2}")) }.ifEmpty { listOf("08:00") }
    }
    val id by rememberSaveable(existing?.id, kind) { mutableStateOf(base.id) }
    var title by rememberSaveable(existing?.id, kind) { mutableStateOf(base.title) }
    var detail by rememberSaveable(existing?.id, kind) { mutableStateOf(base.detail) }
    var timeSlots by rememberSaveable(existing?.id, kind) { mutableStateOf(initialTimes) }
    var start by rememberSaveable(existing?.id, kind) { mutableStateOf(base.start) }
    var end by rememberSaveable(existing?.id, kind) { mutableStateOf(base.end) }
    var place by rememberSaveable(existing?.id, kind) { mutableStateOf(base.place) }
    var level by rememberSaveable(existing?.id, kind) { mutableStateOf(if (base.level == "Prohibido") "Restringido" else base.level) }
    var foodItems by rememberSaveable(existing?.id, kind) { mutableStateOf(base.foodItems) }
    var stockRemaining by rememberSaveable(existing?.id, kind) { mutableStateOf(base.stockRemaining?.toString().orEmpty()) }
    var unitsPerDose by rememberSaveable(existing?.id, kind) { mutableStateOf(base.unitsPerDose.toString()) }
    var stockAlertAt by rememberSaveable(existing?.id, kind) { mutableStateOf(base.stockAlertAt.toString()) }
    var trackInventory by rememberSaveable(existing?.id, kind) { mutableStateOf(base.stockRemaining != null) }
    var editingTimeIndex by rememberSaveable(existing?.id, kind) { mutableStateOf<Int?>(null) }
    var duplicateTime by remember { mutableStateOf(false) }
    val reminderChoices = remember(base.reminderMinutes, base.dayBefore) {
        AppointmentReminderChoice.forCurrent(base.reminderMinutes, base.dayBefore)
    }
    var reminder by rememberSaveable(existing?.id, kind) {
        mutableStateOf(AppointmentReminderChoice.currentId(base.reminderMinutes, base.dayBefore))
    }
    var archive by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { if (!busy) close() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 900.dp)) {
        val isWide = maxWidth >= 620.dp
        Column(
            Modifier.fillMaxWidth().widthIn(max = 820.dp).align(Alignment.TopCenter).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("${if (existing == null) "Agregar" else "Editar"} ${kindLabel(kind).lowercase()}", style = MaterialTheme.typography.headlineSmall)
            Text("Completa la información para organizar tu seguimiento.", color = MutedText, style = MaterialTheme.typography.bodySmall)
            if (kind == Kind.RESTRICTION) {
                Panel {
                    Text("Categoría", style = MaterialTheme.typography.titleMedium, color = DarkText, fontWeight = FontWeight.SemiBold)
                    Text("Categorías sugeridas", color = MutedText, style = MaterialTheme.typography.labelMedium)
                    listOf("Verduras y legumbres", "Frutas", "Proteínas", "Cereales integrales", "Lácteos", "Grasas", "Azúcares y dulces", "Otros").chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { category -> FilterChip(selected = title == category, onClick = { title = category }, label = { Text(category) }) }
                        }
                    }
                    Field(title, { title = it }, "Nombre de la categoría")
                    Field(detail, { detail = it }, "Recomendación profesional", false)
                    Field(foodItems, { foodItems = it }, "Alimentos de esta categoría, separados por coma", false)
                    listOf("Permitido", "Restringido").forEach { label ->
                        Row(Modifier.fillMaxWidth().clickable { level = label }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(level == label, { level = label })
                            Text(label)
                        }
                    }
                }
            } else {
                Panel {
                    Text("Información", style = MaterialTheme.typography.titleMedium, color = DarkText, fontWeight = FontWeight.SemiBold)
                    if (isWide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Field(title, { title = it }, if (kind == Kind.APPOINTMENT) "Especialidad / médico" else "Nombre", modifier = Modifier.weight(1f))
                            if (kind == Kind.APPOINTMENT) Field(place, { place = it }, "Lugar / consultorio", modifier = Modifier.weight(1f))
                        }
                    } else {
                        Field(title, { title = it }, if (kind == Kind.APPOINTMENT) "Especialidad / médico" else "Nombre")
                        if (kind == Kind.APPOINTMENT) Field(place, { place = it }, "Lugar / consultorio")
                    }
                    Field(detail, { detail = it }, when (kind) {
                        Kind.MEDICINE -> "Descripción e indicaciones"
                        Kind.APPOINTMENT -> "Motivo o notas"
                        else -> "Indicaciones de la comida"
                    }, false)
                }
                Panel {
                    Text("Horario", style = MaterialTheme.typography.titleMedium, color = DarkText, fontWeight = FontWeight.SemiBold)
                    if (isWide && kind != Kind.APPOINTMENT) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DateField(start, { start = it }, "Fecha inicial", modifier = Modifier.weight(1f))
                            DateField(end, { end = it }, "Fecha final (opcional)", true, modifier = Modifier.weight(1f))
                        }
                    } else {
                        DateField(start, { start = it }, if (kind == Kind.APPOINTMENT) "Fecha de la cita" else "Fecha inicial")
                        if (kind != Kind.APPOINTMENT) DateField(end, { end = it }, "Fecha final (opcional)", true)
                    }
                    RecordTimesField(kind, timeSlots, { timeSlots = it }, onSelectTime = { editingTimeIndex = it })
                    if (duplicateTime) Text("Cada horario debe ser distinto.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (kind == Kind.MEDICINE) {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Inventario", style = MaterialTheme.typography.titleMedium, color = DarkText, fontWeight = FontWeight.SemiBold)
                                Text("Controla existencias y alertas de reposición.", color = MutedText, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(trackInventory, { enabled ->
                                trackInventory = enabled
                                if (!enabled) stockRemaining = ""
                            })
                        }
                        if (trackInventory) {
                            if (isWide) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Field(stockRemaining, { stockRemaining = it }, "Unidades disponibles", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                                    Field(unitsPerDose, { unitsPerDose = it }, "Unidades por dosis", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                                    Field(stockAlertAt, { stockAlertAt = it }, "Alerta cuando queden", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                                }
                            } else {
                                Field(stockRemaining, { stockRemaining = it }, "Unidades disponibles", keyboardType = KeyboardType.Number)
                                Field(unitsPerDose, { unitsPerDose = it }, "Unidades por dosis", keyboardType = KeyboardType.Number)
                                Field(stockAlertAt, { stockAlertAt = it }, "Alerta cuando queden", keyboardType = KeyboardType.Number)
                            }
                        }
                    }
                }
                if (kind == Kind.APPOINTMENT) {
                    Panel {
                        Text("Aviso previo", style = MaterialTheme.typography.titleMedium, color = DarkText, fontWeight = FontWeight.SemiBold)
                        reminderChoices.forEach { option ->
                            Row(Modifier.fillMaxWidth().clickable { reminder = option.id }, verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(reminder == option.id, { reminder = option.id })
                                Text(option.label)
                            }
                        }
                    }
                }
            }
            Button(
                enabled = !busy,
                onClick = {
                    val selectedReminder = reminderChoices.first { it.id == reminder }
                    vm.save(
                        HealthRecord(
                            id, kind, title.trim(), detail.trim(), timeSlots.joinToString(","), start, end, place.trim(), level,
                            if (kind == Kind.APPOINTMENT) selectedReminder.minutes else 0,
                            kind == Kind.APPOINTMENT && selectedReminder.dayBefore, base.archived,
                            if (kind == Kind.MEDICINE && trackInventory) stockRemaining.toIntOrNull() ?: -1 else null,
                            if (kind == Kind.MEDICINE) unitsPerDose.toIntOrNull() ?: -1 else 1,
                            if (kind == Kind.MEDICINE) stockAlertAt.toIntOrNull() ?: -1 else 5,
                            if (kind == Kind.MEDICINE && trackInventory && stockRemaining.isNotBlank()) {
                                maxOf(base.stockTotal ?: base.stockRemaining ?: 0, stockRemaining.toIntOrNull() ?: -1)
                            } else null,
                            if (kind == Kind.RESTRICTION) foodItems.trim() else base.foodItems
                        ),
                        close
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Guardando…" else "Guardar") }

            if (existing != null) TextButton(enabled = !busy, onClick = { archive = true }) { Text("Archivar registro", color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !busy, onClick = close) { Text("Cancelar") }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
    editingTimeIndex?.let { index ->
        TimePickerDialog(
            initial = timeSlots.getOrNull(index) ?: "08:00",
            dismiss = { editingTimeIndex = null },
            confirm = { selected ->
                val collision = timeSlots.indices.any { it != index && timeSlots[it] == selected }
                duplicateTime = collision
                if (!collision) timeSlots = timeSlots.toMutableList().also { it[index] = selected }
                editingTimeIndex = null
            }
        )
    }
    if (archive) AlertDialog(
        onDismissRequest = { archive = false },
        title = { Text("Archivar ${kindLabel(kind).lowercase()}") },
        text = { Text("Dejará de aparecer en la agenda y no generará más recordatorios. El historial se conserva.") },
        confirmButton = { Button(enabled = !busy, onClick = { vm.delete(id) { archive = false; close() } }) { Text("Archivar") } },
        dismissButton = { TextButton(onClick = { archive = false }) { Text("Cancelar") } }
    )
}

@Composable
internal fun Field(
    value: String,
    change: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value,
        change,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

@Composable
private fun RecordTimesField(kind: Kind, times: List<String>, onChange: (List<String>) -> Unit, onSelectTime: (Int) -> Unit) {
    val title = when (kind) {
        Kind.MEDICINE -> "Horarios de administración"
        Kind.APPOINTMENT -> "Hora de la cita"
        else -> "Horarios de comida"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (kind != Kind.APPOINTMENT) Text("${times.size} horario(s)", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        if (kind != Kind.APPOINTMENT) {
            TextButton(
                enabled = times.size < 24,
                onClick = {
                    val interval = if (kind == Kind.MEDICINE) 8L else 4L
                    val first = runCatching { LocalTime.parse(times.last()) }.getOrDefault(LocalTime.of(8, 0))
                    val next = (1..24).asSequence()
                        .map { first.plusHours(interval * it).format(DateTimeFormatter.ofPattern("HH:mm")) }
                        .firstOrNull { it !in times }
                    if (next != null) onChange(times + next)
                }
            ) { Icon(Icons.Default.Add, contentDescription = null); Text("Agregar hora") }
        }
    }
    times.forEachIndexed { index, time ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSelectTime(index) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(if (kind == Kind.APPOINTMENT) "Hora" else "${if (kind == Kind.MEDICINE) "Dosis" else "Comida"} ${index + 1}")
                    Text(formatTimeForDisplay(time), color = MutedText, style = MaterialTheme.typography.bodySmall)
                }
                Text(time, color = MedicationAccent, fontWeight = FontWeight.Bold)
            }
            if (kind != Kind.APPOINTMENT && times.size > 1) {
                IconButton(onClick = { onChange(times.toMutableList().also { it.removeAt(index) }) }) {
                    Icon(Icons.Default.Close, contentDescription = "Quitar horario", tint = MutedText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: String, dismiss: () -> Unit, confirm: (String) -> Unit) {
    val parsed = runCatching { LocalTime.parse(initial) }.getOrDefault(LocalTime.of(8, 0))
    val state = rememberTimePickerState(initialHour = parsed.hour, initialMinute = parsed.minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Selecciona la hora") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { confirm(storedTimeForPicker(state.hour, state.minute)) }) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } }
    )
}

internal fun storedTimeForPicker(hour: Int, minute: Int): String {
    require(hour in 0..23 && minute in 0..59) { "La hora seleccionada no es válida." }
    return "%02d:%02d".format(Locale.ROOT, hour, minute)
}

private fun formatTimeForDisplay(value: String): String = runCatching {
    LocalTime.parse(value).format(DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es")))
}.getOrDefault(value)

private data class AppointmentReminderChoice(val id: String, val label: String, val minutes: Int, val dayBefore: Boolean) {
    companion object {
        private val standard = listOf(
            AppointmentReminderChoice("AT_TIME", "Solo a la hora", 0, false),
            AppointmentReminderChoice("ONE_HOUR", "1 hora antes", 60, false),
            AppointmentReminderChoice("ONE_DAY", "1 día antes", 0, true),
            AppointmentReminderChoice("ONE_DAY_AND_HOUR", "1 día y 1 hora antes", 60, true)
        )

        fun currentId(minutes: Int, dayBefore: Boolean) = standard.firstOrNull {
            it.minutes == minutes && it.dayBefore == dayBefore
        }?.id ?: "CURRENT"

        fun forCurrent(minutes: Int, dayBefore: Boolean): List<AppointmentReminderChoice> {
            val current = standard.firstOrNull { it.minutes == minutes && it.dayBefore == dayBefore }
            if (current != null) return standard
            val currentParts = buildList {
                if (dayBefore) add("1 día antes")
                if (minutes > 0) add("${formatReminderOffset(minutes)} antes")
            }
            return listOf(AppointmentReminderChoice("CURRENT", "Mantener aviso actual (${currentParts.joinToString(", ")})", minutes, dayBefore)) + standard
        }

        private fun formatReminderOffset(minutes: Int): String = when {
            minutes % 1440 == 0 -> "${minutes / 1440} día(s)"
            minutes % 60 == 0 -> "${minutes / 60} hora(s)"
            else -> "$minutes min"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(value: String, change: (String) -> Unit, label: String, optional: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember(value, label) { mutableStateOf(false) }
    val initialMillis = remember(value) {
        runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (value.isBlank()) label else formatDateForDisplay(value), maxLines = 1)
        }
        if (optional && value.isNotBlank()) IconButton(onClick = { change("") }) { Icon(Icons.Default.Close, "Quitar fecha final") }
    }
    if (open) DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    change(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                open = false
            }) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

private fun formatDateForDisplay(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es")))
}.getOrDefault(value)
