package com.github.kr328.clash.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

private const val DATABASE_NAME = "profiles"

fun openDatabase(): Database {
  val directory = Path(System.getProperty("user.home"), ".tabby").createDirectories()
  val databaseFile = directory.resolve(DATABASE_NAME)
  return Room.databaseBuilder<Database>(name = databaseFile.toAbsolutePath().toString())
    .setDriver(BundledSQLiteDriver())
    .build()
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
