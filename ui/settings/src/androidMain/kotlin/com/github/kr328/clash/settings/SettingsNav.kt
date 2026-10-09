package com.github.kr328.clash.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.github.kr328.clash.settings.ui.AccessControlScreen
import com.github.kr328.clash.settings.ui.AppSettingsScreen
import com.github.kr328.clash.settings.ui.MetaFeatureSettingsScreen
import com.github.kr328.clash.settings.ui.NetworkSettingsScreen
import com.github.kr328.clash.settings.ui.OverrideSettingsScreen
import com.github.kr328.clash.settings.ui.SettingsScreen
import com.github.kr328.clash.ui.nav.TabbyNavDisplay
import com.github.kr328.clash.ui.nav.addIfNotLast
import kotlinx.serialization.Serializable

sealed interface SettingsRoute : NavKey {
  @Serializable data object Root : SettingsRoute
}

@Serializable private data object AppSettings : SettingsRoute

@Serializable private data object NetworkSettings : SettingsRoute

@Serializable private data object OverrideSettings : SettingsRoute

@Serializable private data object MetaFeatureSettings : SettingsRoute

@Serializable private data object AccessControl : SettingsRoute

fun EntryProviderScope<NavKey>.settingsEntries() {
  entry<SettingsRoute.Root> {
    val backStack = rememberNavBackStack(SettingsRoute.Root)
    TabbyNavDisplay(
      backStack = backStack,
      entryProvider =
        entryProvider {
          entry<SettingsRoute.Root> {
            SettingsScreen(
              onOpenAppSettings = { backStack.addIfNotLast(AppSettings) },
              onOpenNetworkSettings = { backStack.addIfNotLast(NetworkSettings) },
              onOpenOverrideSettings = { backStack.addIfNotLast(OverrideSettings) },
              onOpenMetaFeatureSettings = { backStack.addIfNotLast(MetaFeatureSettings) },
            )
          }
          entry<AppSettings> { AppSettingsScreen() }
          entry<NetworkSettings> {
            NetworkSettingsScreen(
              onStartAccessControlList = { backStack.addIfNotLast(AccessControl) },
            )
          }
          entry<OverrideSettings> {
            OverrideSettingsScreen(onResetCompleted = { backStack.removeLastOrNull() })
          }
          entry<MetaFeatureSettings> {
            MetaFeatureSettingsScreen(onResetCompleted = { backStack.removeLastOrNull() })
          }
          entry<AccessControl> { AccessControlScreen() }
        },
    )
  }
}
