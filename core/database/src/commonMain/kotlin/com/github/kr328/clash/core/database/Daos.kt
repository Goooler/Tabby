package com.github.kr328.clash.service.data

fun ImportedDao(): ImportedDao {
  return Database.database.importedDao()
}

fun PendingDao(): PendingDao {
  return Database.database.pendingDao()
}

fun SelectionDao(): SelectionDao {
  return Database.database.selectionProxyDao()
}
