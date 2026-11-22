package edu.unicauca.multimedivida.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import edu.unicauca.multimedivida.db.HealthDatabase

private lateinit var appContext: Context

actual fun configureHealthDatabase(platformContext: Any) {
    appContext = (platformContext as Context).applicationContext
}

actual fun createHealthDriver(): SqlDriver {
    check(::appContext.isInitialized) { "Configura la base de datos desde Android antes de abrir la app." }
    return AndroidSqliteDriver(HealthDatabase.Schema, appContext, "multimedivida.db")
}
