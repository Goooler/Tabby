package com.github.kr328.clash.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

private const val DATABASE_NAME = "profiles"

fun openDatabase(): Database {
  val directory = File(System.getProperty("user.home"), ".tabby")
  check(directory.exists() || directory.mkdirs()) { "Unable to create ${directory.absolutePath}" }
  val databaseFile = File(directory, DATABASE_NAME)
  return Room.databaseBuilder<Database>(name = databaseFile.absolutePath)
    .setDriver(BundledSQLiteDriver())
    .build()
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
