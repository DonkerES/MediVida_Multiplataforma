package edu.unicauca.multimedivida

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.unicauca.multimedivida.data.Kind

@Composable
internal fun Hero(title: String, subtitle: String, colors: List<Color> = HomeGradient, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(colors)).padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(subtitle, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodyMedium)
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun Empty(message: String) {
    Panel { Text(message, color = MutedText) }
}

@Composable
internal fun EmptyAction(message: String, label: String, onClick: () -> Unit) {
    Panel {
        Text(message, color = MutedText)
        TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(label) }
    }
}

internal fun kindLabel(kind: Kind) = when (kind) {
    Kind.MEDICINE -> "Medicamento"
    Kind.APPOINTMENT -> "Cita médica"
    Kind.MEAL -> "Comida"
    Kind.RESTRICTION -> "Restricción alimentaria"
}
