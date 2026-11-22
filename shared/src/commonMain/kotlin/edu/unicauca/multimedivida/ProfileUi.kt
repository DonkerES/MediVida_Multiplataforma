package edu.unicauca.multimedivida

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.unicauca.multimedivida.data.Profile

@Composable
internal fun ProfileScreen(
    profile: Profile,
    busy: Boolean,
    vm: HealthViewModel,
    edit: () -> Unit,
    about: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val isDesktop = remember { getPlatform().name.startsWith("Java") }
    var contactError by rememberSaveable { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Hero("Mi perfil", "Tu información de salud") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(54.dp).background(Color.White.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(
                            profile.name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifBlank { "MV" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Column {
                        Text(profile.name.ifBlank { "Completa tu perfil" }, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Perfil local", color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Panel {
                    ProfileSectionTitle(Icons.Default.Person, "Datos personales", HomeAccent)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileMetric("EDAD", profile.age.ifBlank { "Sin registrar" }, Modifier.weight(1f), HomeAccent)
                        ProfileMetric("PESO", if (profile.weight.isBlank()) "Sin registrar" else "${profile.weight} kg", Modifier.weight(1f), MedicationAccent)
                    }
                    ProfileMetric("GRUPO SANGUÍNEO", profile.blood.ifBlank { "Sin registrar" }, Modifier.fillMaxWidth(), AppointmentAccent)
                    Column(
                        Modifier.fillMaxWidth().background(Color(0xFFFFF5E8), RoundedCornerShape(10.dp)).padding(11.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("ALERGIAS", color = Color(0xFF9B5A0A), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(profile.allergies.ifBlank { "Ninguna registrada" }, color = DarkText, style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = edit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Editar perfil")
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Panel {
                    ProfileSectionTitle(Icons.Default.ContactPhone, "Contacto de emergencia", AppointmentAccent)
                    Text(profile.contact.ifBlank { "Sin contacto registrado" }, color = DarkText, fontWeight = FontWeight.SemiBold)
                    Text(profile.phone.ifBlank { "Teléfono de emergencia" }, color = MutedText, style = MaterialTheme.typography.bodySmall)
                    Button(
                        enabled = profile.phone.isNotBlank(),
                        onClick = {
                            val phoneUri = profile.phone.filter { it.isDigit() || it == '+' }
                            contactError = runCatching { uriHandler.openUri("tel:$phoneUri") }.isFailure
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppointmentAccent)
                    ) {
                        Icon(Icons.Default.Call, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Contacto: ${profile.phone.ifBlank { "Sin número" }}")
                    }
                    if (contactError) {
                        Text("No hay una aplicación disponible para abrir este número.", color = MutedText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Panel {
                    ProfileSectionTitle(Icons.Default.Alarm, "Alarmas y recordatorios", NutritionAccent)
                    if (isDesktop) {
                        Text("Los avisos no están disponibles en la versión Desktop.", color = MutedText, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Alarmas activadas", color = DarkText, fontWeight = FontWeight.SemiBold)
                                Text(if (profile.notifications) "Medicamentos, comidas y citas" else "No recibirás avisos", color = MutedText, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(profile.notifications, { vm.profile(profile.copy(notifications = it)) {} }, enabled = !busy)
                        }
                        ProfileStatus("Notificaciones del sistema", if (profile.notifications) "Activadas" else "Desactivadas", profile.notifications)
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Panel {
                    ProfileSectionTitle(Icons.Default.Person, "Almacenamiento", HomeAccent)
                    Text("Tu perfil y tus datos de salud se guardan localmente en este dispositivo.")
                    TextButton(onClick = about) { Text("Descripción de MediVida") }
                }
            }
        }
    }

}

@Composable
private fun ProfileSectionTitle(icon: ImageVector, title: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).background(tint.copy(alpha = .12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(title, color = DarkText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier, tint: Color) {
    Column(modifier.background(tint.copy(alpha = .07f), RoundedCornerShape(10.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text(value, color = DarkText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileStatus(label: String, status: String, enabled: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (enabled) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (enabled) Color(0xFF159564) else Color(0xFFD18119), modifier = Modifier.size(18.dp))
        Text(label, Modifier.weight(1f).padding(start = 8.dp), color = DarkText, style = MaterialTheme.typography.bodySmall)
        Text(status, color = if (enabled) Color(0xFF159564) else Color(0xFFAD6812), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditor(profile: Profile, busy: Boolean, close: () -> Unit, vm: HealthViewModel, initialSetup: Boolean = false) {
    var name by rememberSaveable { mutableStateOf(profile.name) }
    var age by rememberSaveable { mutableStateOf(profile.age) }
    var weight by rememberSaveable { mutableStateOf(profile.weight) }
    var blood by rememberSaveable { mutableStateOf(profile.blood) }
    var allergies by rememberSaveable { mutableStateOf(profile.allergies) }
    var contact by rememberSaveable { mutableStateOf(profile.contact) }
    var phone by rememberSaveable { mutableStateOf(profile.phone) }

    ModalBottomSheet(onDismissRequest = { if (!busy) close() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (initialSetup) "Configura tu perfil" else "Editar perfil", style = MaterialTheme.typography.headlineSmall)
            Field(name, { name = it }, "Nombre completo")
            Field(age, { age = it }, "Edad")
            Field(weight, { weight = it }, "Peso (kg)")
            Field(blood, { blood = it.uppercase() }, "Tipo de sangre (A+, O-, etc.)")
            Field(allergies, { allergies = it }, "Alergias conocidas", false)
            Field(contact, { contact = it }, "Contacto de emergencia")
            Field(phone, { phone = it }, "Teléfono de emergencia")
            Button(
                enabled = !busy,
                onClick = { vm.profile(profile.copy(name = name.trim(), age = age, weight = weight, blood = blood, allergies = allergies, contact = contact, phone = phone), close) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (initialSetup) "Guardar y continuar" else "Guardar perfil") }
            TextButton(enabled = !busy, onClick = close) { Text(if (initialSetup) "Configurar después" else "Cancelar") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
