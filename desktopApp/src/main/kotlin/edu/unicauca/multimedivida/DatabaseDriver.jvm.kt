package edu.unicauca.multimedivida.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import edu.unicauca.multimedivida.db.HealthDatabase
import java.io.File

actual fun configureHealthDatabase(platformContext: Any) = Unit

actual fun createHealthDriver(): SqlDriver {
    val directory = File(System.getProperty("user.home"), ".multimedivida")
    check(directory.isDirectory || directory.mkdirs()) { "No se pudo crear el directorio de datos: ${directory.absolutePath}" }
    val databaseFile = File(directory, "multimedivida.db")
    return JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}", schema = HealthDatabase.Schema)
}
