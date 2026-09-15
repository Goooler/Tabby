package com.github.kr328.clash.core.database

import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database as RoomDatabaseAnnotation
import androidx.room3.RoomDatabase as RoomDatabaseBase
import androidx.room3.RoomDatabaseConstructor
import kotlin.uuid.Uuid

@RoomDatabaseAnnotation(
  version = 2,
  entities = [Imported::class, Pending::class, Selection::class],
  exportSchema = true,
)
@ConstructedBy(DatabaseConstructor::class)
@ColumnTypeConverters(RoomTypeConverters::class)
abstract class Database : RoomDatabaseBase() {
  abstract fun importedDao(): ImportedDao

  abstract fun pendingDao(): PendingDao

  abstract fun selectionProxyDao(): SelectionDao
}

@Suppress("KotlinNoActualForExpect")
expect object DatabaseConstructor : RoomDatabaseConstructor<Database> {
  override fun initialize(): Database
}

object DatabaseProvider {
  private var database: Database? = null

  fun initialize(database: Database) {
    check(this.database == null) { "DatabaseProvider is already initialized" }
    this.database = database
  }

  internal fun requireDatabase(): Database =
    checkNotNull(database) { "DatabaseProvider is not initialized" }
}

object RoomTypeConverters {
  @ColumnTypeConverter fun fromUUID(uuid: Uuid): String = uuid.toString()

  @ColumnTypeConverter fun toUUID(uuid: String): Uuid = Uuid.parse(uuid)
}
