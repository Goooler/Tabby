package com.github.kr328.clash.settings.vm

import android.app.Application
import android.os.Build
import androidx.lifecycle.ViewModel
import com.github.kr328.clash.core.model.AccessControlMode
import com.github.kr328.clash.glue.remote.Remote
import com.github.kr328.clash.glue.store.UiStore
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

internal class NetworkSettingsViewModel(
  application: Application,
  private val uiStore: UiStore,
) : ViewModel() {
  private val serviceStore = ServiceStore(application)

  val clashRunning: StateFlow<Boolean> = Remote.broadcasts.clashRunningFlow

  val uiState: StateFlow<UiState>
    field =
      MutableStateFlow(
        UiState(
          hasSystemProxyOption = Build.VERSION.SDK_INT >= 29,
          enableVpn = uiStore.enableVpn,
          bypassPrivateNetwork = serviceStore.bypassPrivateNetwork,
          dnsHijacking = serviceStore.dnsHijacking,
          allowBypass = serviceStore.allowBypass,
          allowIpv6 = serviceStore.allowIpv6,
          systemProxy = serviceStore.systemProxy,
          tunStackMode = serviceStore.tunStackMode,
          accessControlMode = serviceStore.accessControlMode,
        ),
      )

  fun updateEnableVpn(value: Boolean) {
    uiStore.enableVpn = value
    uiState.update { it.copy(enableVpn = value) }
  }

  fun updateBypassPrivateNetwork(value: Boolean) {
    serviceStore.bypassPrivateNetwork = value
    uiState.update { it.copy(bypassPrivateNetwork = value) }
  }

  fun updateDnsHijacking(value: Boolean) {
    serviceStore.dnsHijacking = value
    uiState.update { it.copy(dnsHijacking = value) }
  }

  fun updateAllowBypass(value: Boolean) {
    serviceStore.allowBypass = value
    uiState.update { it.copy(allowBypass = value) }
  }

  fun updateAllowIpv6(value: Boolean) {
    serviceStore.allowIpv6 = value
    uiState.update { it.copy(allowIpv6 = value) }
  }

  fun updateSystemProxy(value: Boolean) {
    serviceStore.systemProxy = value
    uiState.update { it.copy(systemProxy = value) }
  }

  fun updateTunStackMode(value: String) {
    serviceStore.tunStackMode = value
    uiState.update { it.copy(tunStackMode = value) }
  }

  fun updateAccessControlMode(value: AccessControlMode) {
    serviceStore.accessControlMode = value
    uiState.update { it.copy(accessControlMode = value) }
  }

  data class UiState(
    val hasSystemProxyOption: Boolean,
    val enableVpn: Boolean,
    val bypassPrivateNetwork: Boolean,
    val dnsHijacking: Boolean,
    val allowBypass: Boolean,
    val allowIpv6: Boolean,
    val systemProxy: Boolean,
    val tunStackMode: String,
    val accessControlMode: AccessControlMode,
  )
}
