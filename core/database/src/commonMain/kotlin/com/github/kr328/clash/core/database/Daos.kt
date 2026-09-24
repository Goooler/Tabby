package com.github.kr328.clash.core.database

fun ImportedDao(): ImportedDao = DatabaseProvider.requireDatabase().importedDao()

fun PendingDao(): PendingDao = DatabaseProvider.requireDatabase().pendingDao()

fun SelectionDao(): SelectionDao = DatabaseProvider.requireDatabase().selectionProxyDao()
