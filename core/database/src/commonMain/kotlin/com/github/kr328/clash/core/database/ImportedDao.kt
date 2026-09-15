package com.github.kr328.clash.service.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import kotlin.uuid.Uuid

@Dao
interface ImportedDao {
  @Query("SELECT * FROM imported WHERE uuid = :uuid") suspend fun queryByUUID(uuid: Uuid): Imported?

  @Query("SELECT uuid FROM imported ORDER BY createdAt") suspend fun queryAllUUIDs(): List<Uuid>

  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(imported: Imported): Long

  @Update(onConflict = OnConflictStrategy.ABORT) suspend fun update(imported: Imported)

  @Query("DELETE FROM imported WHERE uuid = :uuid") suspend fun remove(uuid: Uuid)

  @Query("SELECT EXISTS(SELECT 1 FROM imported WHERE uuid = :uuid)")
  suspend fun exists(uuid: Uuid): Boolean
}
