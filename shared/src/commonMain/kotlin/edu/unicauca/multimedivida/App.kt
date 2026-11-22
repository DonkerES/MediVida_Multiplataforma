package edu.unicauca.multimedivida

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.unicauca.multimedivida.data.Kind
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
fun App(vm: HealthViewModel = viewModel { HealthViewModel() }) {
    val state by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf("Inicio") }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var addMenu by rememberSaveable { mutableStateOf(false) }

    MaterialTheme {
        if (state.user == null && state.loading) {
            AuthBackground {
                HealthLogo()
                Spacer(Modifier.height(20.dp))
                Text("MediVida", fontSize = 32.sp, color = Color.White)
                Spacer(Modifier.height(18.dp))
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text("Cargando tu perfil local", color = Color.White)
            }
        } else if (state.user == null) {
            AuthBackground {
                HealthLogo()
                Spacer(Modifier.height(20.dp))
                Text("MediVida", fontSize = 32.sp, color = Color.White)
                Spacer(Modifier.height(24.dp))
                AuthCard {
                    Text("Perfil local", style = MaterialTheme.typography.headlineSmall)
                    Text("Tus datos se guardan en este dispositivo.", color = MutedText)
                    Spacer(Modifier.height(12.dp))
                    Button(enabled = !state.loading, onClick = vm::refresh, modifier = Modifier.fillMaxWidth()) {
                        Text("Reintentar")
                    }
                }
            }
        } else {
            var now by remember { mutableStateOf(LocalDateTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = LocalDateTime.now()
                    delay(30_000)
                }
            }
            val data = state.snapshot
            LaunchedEffect(state.user, data.profile.name) {
                if (data.profile.name.isBlank() && form == null) form = "PROFILE_SETUP"
            }
            Scaffold(
                containerColor = BackgroundColor,
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        listOf(
                            "Inicio" to Icons.Default.Home,
                            "Medicación" to Icons.Default.Medication,
                            "Citas" to Icons.Default.CalendarMonth,
                            "Nutrición" to Icons.Default.Restaurant,
                            "Perfil" to Icons.Default.Person
                        ).forEach { (name, icon) ->
                            val accent = when (name) {
                                "Citas" -> AppointmentAccent
                                "Nutrición" -> NutritionAccent
                                "Medicación" -> MedicationAccent
                                else -> HomeAccent
                            }
                            NavigationBarItem(
                                selected = tab == name,
                                onClick = { tab = name },
                                icon = { Icon(icon, name) },
                                label = { Text(name, fontSize = 10.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accent,
                                    selectedTextColor = accent,
                                    indicatorColor = accent.copy(alpha = .12f)
                                )
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (tab != "Perfil" && tab != "Acerca de") {
                        FloatingActionButton(
                            onClick = { addMenu = true },
                            containerColor = PrimaryBlue,
                            contentColor = Color.White
                        ) {
                            Icon(Icons.Default.Add, "Agregar registro")
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(state.sync, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MutedText)

                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            "Inicio" -> TodayScreen(data, now, state.loading, vm, navigate = { tab = it }, addRecord = { addMenu = true })
                            "Medicación" -> RecordsScreen(data, { editId = it.id; form = it.kind.name }, now, state.loading, vm)
                            "Citas" -> Appointments(data, { editId = it.id; form = it.kind.name }) { addMenu = true }
                            "Nutrición" -> Nutrition(data, { editId = it.id; form = it.kind.name }) { addMenu = true }
                            "Perfil" -> ProfileScreen(data.profile, state.loading, vm, edit = { form = "PROFILE" }, about = { tab = "Acerca de" })
                            "Acerca de" -> AboutScreen(onBack = { tab = "Perfil" })
                        }
                    }
                }
            }

            if (addMenu) {
                AlertDialog(
                    onDismissRequest = { addMenu = false },
                    title = { Text("¿Qué quieres agregar?") },
                    text = {
                        Column {
                            Kind.entries.forEach { kind ->
                                TextButton(onClick = { editId = null; form = kind.name; addMenu = false }) {
                                    Text(kindLabel(kind))
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { addMenu = false }) { Text("Cerrar") } }
                )
            }

            form?.let { route ->
                if (route == "PROFILE" || route == "PROFILE_SETUP") {
                    ProfileEditor(data.profile, state.loading, { form = null }, vm, initialSetup = route == "PROFILE_SETUP")
                } else {
                    RecordEditor(Kind.valueOf(route), data.records.firstOrNull { it.id == editId }, state.loading, { form = null; editId = null }, vm)
                }
            }
        }

        state.error?.let {
            AlertDialog(
                onDismissRequest = vm::clearError,
                title = { Text("Revisa los datos") },
                text = { Text(it) },
                confirmButton = { TextButton(onClick = vm::clearError) { Text("Entendido") } }
            )
        }
        state.message?.let {
            AlertDialog(
                onDismissRequest = vm::clearMessage,
                title = { Text("MediVida") },
                text = { Text(it) },
                confirmButton = { TextButton(onClick = vm::clearMessage) { Text("Entendido") } }
            )
        }
    }
}
