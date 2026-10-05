package com.github.kr328.clash.service

import android.content.Context
import co.touchlab.kermit.Logger
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.database.Selection
import com.github.kr328.clash.core.database.SelectionDao
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.core.model.ProviderList
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.Traffic
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.model.UiConfiguration
import com.github.kr328.clash.service.remote.IClashManager
import com.github.kr328.clash.service.remote.ILogObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.sendOverrideChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClashManager(private val context: Context) :
  IClashManager, CoroutineScope by CoroutineScope(Dispatchers.IO) {
  private val store = ServiceStore(context)
  private var logReceiver: ReceiveChannel<LogMessage>? = null

  override fun queryTunnelState(): TunnelState {
    return Clash.queryTunnelState()
  }

  override fun queryTrafficTotal(): Traffic {
    return Clash.queryTrafficTotal()
  }

  override fun queryProxyGroupNames(excludeNotSelectable: Boolean): List<String> {
    return Clash.queryGroupNames(excludeNotSelectable)
  }

  override fun queryProxyGroup(name: String, proxySort: ProxySort): ProxyGroup {
    return Clash.queryGroup(name, proxySort)
  }

  override fun queryConfiguration(): UiConfiguration {
    return Clash.queryConfiguration()
  }

  override fun queryProviders(): ProviderList {
    return ProviderList(Clash.queryProviders())
  }

  override fun queryOverride(slot: Clash.OverrideSlot): ConfigurationOverride {
    return Clash.queryOverride(slot)
  }

  override fun patchSelector(group: String, name: String): Boolean {
    return Clash.patchSelector(group, name).also {
      val current = store.activeProfile ?: return@also

      launch {
        try {
          if (it) {
            SelectionDao().setSelected(Selection(current, group, name))
          } else {
            SelectionDao().removeSelected(current, group)
          }
        } catch (e: Exception) {
          Logger.w("Persist selector failed", e)
        }
      }
    }
  }

  override fun patchOverride(slot: Clash.OverrideSlot, configuration: ConfigurationOverride) {
    Clash.patchOverride(slot, configuration)

    context.sendOverrideChanged()
  }

  override fun clearOverride(slot: Clash.OverrideSlot) {
    Clash.clearOverride(slot)
  }

  override suspend fun healthCheck(group: String) {
    return Clash.healthCheck(group).await()
  }

  override suspend fun healthCheckProxy(group: String, name: String) {
    return Clash.healthCheckProxy(group, name).await()
  }

  override suspend fun updateProvider(type: Provider.Type, name: String) {
    return Clash.updateProvider(type, name).await()
  }

  override fun setLogObserver(observer: ILogObserver?) {
    synchronized(this) {
      logReceiver?.apply {
        cancel()

        Clash.forceGc()
      }

      if (observer != null) {
        logReceiver =
          Clash.subscribeLogcat().also { c ->
            launch {
              try {
                while (isActive) {
                  observer.newItem(c.receive())
                }
              } catch (e: CancellationException) {
                // intended behavior
                // ignore
              } catch (e: Exception) {
                Logger.w("UI crashed", e)
              } finally {
                withContext(NonCancellable) {
                  c.cancel()

                  Clash.forceGc()
                }
              }
            }
          }
      }
    }
  }
}
