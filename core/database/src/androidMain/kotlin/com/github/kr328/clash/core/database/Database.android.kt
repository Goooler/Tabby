package com.github.kr328.clash.core.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL

private const val DATABASE_NAME = "profiles"

private val migration1To2 =
  Migration(1, 2) { database ->
    database.execSQL("ALTER TABLE imported ADD COLUMN ageSecretKey TEXT")
    database.execSQL("ALTER TABLE pending ADD COLUMN ageSecretKey TEXT")
  }

fun initializeDatabase(context: Context) {
  DatabaseProvider.initialize(openDatabase(context))
}

fun openDatabase(context: Context): Database {
  val applicationContext = context.applicationContext
  val databaseFile = applicationContext.getDatabasePath(DATABASE_NAME)
  return Room.databaseBuilder<Database>(applicationContext, databaseFile.absolutePath)
    .addMigrations(migration1To2)
    .build()
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
