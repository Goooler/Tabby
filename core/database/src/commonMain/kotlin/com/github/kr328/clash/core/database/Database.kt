package com.github.kr328.clash.service.data

import android.content.Context
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database as DB
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.execSQL
import com.github.kr328.clash.common.util.application
import kotlin.uuid.Uuid

@DB(
  version = 2,
  entities = [Imported::class, Pending::class, Selection::class],
  exportSchema = false,
)
@ColumnTypeConverters(RoomTypeConverters::class)
abstract class Database : RoomDatabase() {
  abstract fun importedDao(): ImportedDao

  abstract fun pendingDao(): PendingDao

  abstract fun selectionProxyDao(): SelectionDao

  companion object {
    val database: Database by lazy { open(application) }

    private val MIGRATION_1_2 =
      Migration(1, 2) { db ->
        db.execSQL("ALTER TABLE imported ADD COLUMN ageSecretKey TEXT")
        db.execSQL("ALTER TABLE pending ADD COLUMN ageSecretKey TEXT")
      }

    private fun open(context: Context): Database {
      return Room.databaseBuilder(context.applicationContext, Database::class.java, "profiles")
        .addMigrations(MIGRATION_1_2)
        .build()
    }
  }
}

// TODO: https://issuetracker.google.com/issues/525093264
object RoomTypeConverters {
  @ColumnTypeConverter
  fun fromUUID(uuid: Uuid): String {
    return uuid.toString()
  }

  @ColumnTypeConverter
  fun toUUID(uuid: String): Uuid {
    return Uuid.parse(uuid)
  }
}
