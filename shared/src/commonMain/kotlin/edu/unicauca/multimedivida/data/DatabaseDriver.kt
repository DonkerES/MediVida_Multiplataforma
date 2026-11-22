package edu.unicauca.multimedivida.data

import app.cash.sqldelight.db.SqlDriver

expect fun createHealthDriver(): SqlDriver
expect fun configureHealthDatabase(platformContext: Any)
