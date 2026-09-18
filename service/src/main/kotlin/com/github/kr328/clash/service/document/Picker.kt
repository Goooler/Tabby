package com.github.kr328.clash.service.document

import android.content.Context
import android.provider.DocumentsContract
import com.github.kr328.clash.common.R as CommonR
import com.github.kr328.clash.core.database.ImportedDao
import com.github.kr328.clash.core.database.Pending
import com.github.kr328.clash.core.database.PendingDao
import com.github.kr328.clash.core.model.Profile
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import java.io.FileNotFoundException
import kotlin.uuid.Uuid

class Picker(private val context: Context) {
  suspend fun list(path: Path): List<Document> {
    if (path.uuid == null) {
      return ImportedDao().queryAllUUIDs().map { pick(path.copy(uuid = it), false) }
    }

    if (path.scope == null) {
      return listOf(Path.Scope.Configuration, Path.Scope.Providers).map {
        pick(path.copy(scope = it), false)
      }
    }

    val parent = pick(path, false)

    if (parent !is FileDocument) return emptyList()

    return parent.file.list().orEmpty().map {
      pick(path.copy(relative = path.relative.orEmpty() + it), false)
    }
  }

  suspend fun pick(path: Path, writable: Boolean): Document {
    if (path.uuid == null) {
      return VirtualDocument(
        "",
        context.getString(CommonR.string.tabby),
        DocumentsContract.Document.MIME_TYPE_DIR,
        0,
        0,
        setOf(Flag.Virtual),
      )
    }

    if (writable) {
      cloneToPending(path.uuid)
    }

    val imported = ImportedDao().queryByUUID(path.uuid)
    val pending = PendingDao().queryByUUID(path.uuid)

    if (path.scope == null) {
      if (writable) throw IllegalArgumentException("invalid open mode")

      return VirtualDocument(
        id = path.uuid.toString(),
        name = pending?.name ?: imported?.name ?: throw FileNotFoundException("profile not found"),
        mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
        size = 0,
        updatedAt = 0,
        flags = setOf(Flag.Virtual),
      )
    }

    if (path.relative == null) {
      if (path.scope == Path.Scope.Configuration) {
        val type =
          pending?.type ?: imported?.type ?: throw FileNotFoundException("profile not found")

        if (writable && type != Profile.Type.File)
          throw IllegalArgumentException("invalid open mode")

        val flags: Set<Flag> = if (type == Profile.Type.Url) emptySet() else setOf(Flag.Writable)

        return FileDocument(
          file =
            when {
              pending != null -> context.pendingDir.resolve(pending.uuid.toString())
              imported != null -> context.importedDir.resolve(imported.uuid.toString())
              else -> throw FileNotFoundException("profile not found")
            }.resolve("config.yaml"),
          flags = flags,
          idOverride = Paths.CONFIGURATION_ID,
          nameOverride = context.getString(R.string.configuration_yaml),
        )
      } else {
        return FileDocument(
          file =
            when {
              pending != null -> context.pendingDir.resolve(pending.uuid.toString())
              imported != null -> context.importedDir.resolve(imported.uuid.toString())
              else -> throw FileNotFoundException("profile not found")
            }.resolve("providers"),
          idOverride = Paths.PROVIDERS_ID,
          nameOverride = context.getString(R.string.provider_files),
          flags = setOf(Flag.Virtual),
        )
      }
    }

    if (path.scope != Path.Scope.Providers) throw FileNotFoundException("invalid path")

    return FileDocument(
      file =
        when {
            pending != null -> context.pendingDir.resolve(pending.uuid.toString())
            imported != null -> context.importedDir.resolve(imported.uuid.toString())
            else -> throw FileNotFoundException("profile not found")
          }
          .resolve("providers")
          .resolve(path.relative.joinToString(separator = "/")),
      flags = setOf(Flag.Writable, Flag.Deletable),
    )
  }

  private suspend fun cloneToPending(uuid: Uuid) {
    if (PendingDao().queryByUUID(uuid) != null) return

    val imported =
      ImportedDao().queryByUUID(uuid) ?: throw FileNotFoundException("profile not found")

    PendingDao()
      .insert(
        Pending(
          imported.uuid,
          imported.name,
          imported.type,
          imported.source,
          imported.interval,
          0,
          0,
          0,
          0,
          ageSecretKey = imported.ageSecretKey,
        )
      )

    val source = context.importedDir.resolve(uuid.toString())
    val target = context.pendingDir.resolve(uuid.toString())

    target.deleteRecursively()
    source.copyRecursively(target)
  }
}
