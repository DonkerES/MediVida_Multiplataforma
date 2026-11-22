package edu.unicauca.multimedivida

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import javax.imageio.ImageIO

fun main() {
    application {
        val appIcon = remember {
            runCatching {
                Thread.currentThread().contextClassLoader.getResourceAsStream("multimedivida.png")?.use(ImageIO::read)
                    ?.toComposeImageBitmap()?.let(::BitmapPainter)
            }.getOrNull()
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "MultiMediVida",
            icon = appIcon,
        ) {
            App()
        }
    }
}
