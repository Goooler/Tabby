package com.github.kr328.clash.service.clash.module

import android.app.Service
import co.touchlab.kermit.Logger
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.getSerializableCompat
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.database.ImportedDao
import com.github.kr328.clash.core.database.SelectionDao
import com.github.kr328.clash.service.StatusProvider
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.sendProfileLoaded
import kotlin.uuid.Uuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select

class ConfigurationModule(service: Service) : Module<ConfigurationModule.LoadException>(service) {
  data class LoadException(val message: String)

  private val store = ServiceStore(service)
  private val reload = Channel<Unit>(Channel.CONFLATED)

  override suspend fun run() {
    val broadcasts = receiveBroadcast {
      addAction(Intents.ACTION_PROFILE_CHANGED)
      addAction(Intents.ACTION_OVERRIDE_CHANGED)
    }

    var loaded: Uuid? = null

    reload.trySend(Unit)

    while (true) {
      val changed: Uuid? = select {
        broadcasts.onReceive {
          if (it.action == Intents.ACTION_PROFILE_CHANGED)
            it.getSerializableCompat(Intents.EXTRA_UUID)
          else null
        }
        reload.onReceive { null }
      }

      try {
        val current = store.activeProfile ?: throw NullPointerException("No profile selected")

        if (current == loaded && changed != null && changed != loaded) continue

        loaded = current

        val active =
          ImportedDao().queryByUUID(current) ?: throw NullPointerException("No profile selected")

        Clash.load(
          service.importedDir.resolve(active.uuid.toString()),
          active.ageSecretKey?.takeIf { it.isNotBlank() },
        )

        val remove =
          SelectionDao()
            .querySelections(active.uuid)
            .filterNot { Clash.patchSelector(it.proxy, it.selected) }
            .map { it.proxy }

        SelectionDao().removeSelections(active.uuid, remove)

        StatusProvider.currentProfile = active.name

        service.sendProfileLoaded(current)

        Logger.d("Profile ${active.name} loaded")
      } catch (e: Exception) {
        Logger.e("Load profile failed: ${e.message}", e)
        return enqueueEvent(LoadException(e.message ?: "Unknown"))
      }
    }
  }
}
