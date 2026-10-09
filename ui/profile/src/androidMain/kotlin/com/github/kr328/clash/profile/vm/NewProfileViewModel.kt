package com.github.kr328.clash.profile.vm

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.new_profile
import com.github.kr328.clash.common.unknown
import com.github.kr328.clash.core.model.Profile
import com.github.kr328.clash.glue.util.withProfile
import com.github.kr328.clash.profile.Res
import com.github.kr328.clash.profile.import_from_qr_exception
import com.github.kr328.clash.profile.import_from_qr_no_permission
import com.github.kr328.clash.profile.model.ProfileProvider
import io.github.g00fy2.quickie.QRResult
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

internal class NewProfileViewModel(private val application: Application) : ViewModel() {
  val uiState: StateFlow<UiState>
    field = MutableStateFlow(UiState())

  val eventState: SharedFlow<EventState>
    field = MutableSharedFlow(extraBufferCapacity = 64)

  init {
    loadProviders()
  }

  fun onCreate(provider: ProfileProvider) {
    when (provider) {
      QR -> eventState.tryEmit(EventState.LaunchQRScanner)
      is External -> eventState.tryEmit(EventState.LaunchExternalProvider(provider.intent))
      File -> createProfile(File)
      Url -> createProfile(Url)
    }
  }

  fun onDetail(provider: ProfileProvider.External) {
    val packageName = provider.intent.component?.packageName ?: return
    val uri = Uri.fromParts("package", packageName, null)
    eventState.tryEmit(EventState.OpenAppSettings(uri))
  }

  fun onExternalProviderResult(uri: Uri, name: String?) {
    viewModelScope.launch {
      try {
        val profileName = getString(CommonRes.string.new_profile)
        val uuid = withProfile { create(External, name ?: profileName, uri.toString()) }
        eventState.tryEmit(EventState.LaunchProperties(uuid))
      } catch (e: Exception) {
        Logger.e("Create external profile failed: ${e.message}", e)
        eventState.tryEmit(EventState.ShowMessage(e.message ?: getString(CommonRes.string.unknown)))
      }
    }
  }

  fun onQRResult(result: QRResult) {
    viewModelScope.launch {
      when (result) {
        is QRSuccess -> {
          val url = result.content.rawValue ?: result.content.rawBytes?.let { String(it) }.orEmpty()
          try {
            val uuid = withProfile {
              create(type = Url, name = getString(CommonRes.string.new_profile), url)
            }
            eventState.tryEmit(EventState.LaunchProperties(uuid))
          } catch (e: Exception) {
            Logger.e("Create QR profile failed: ${e.message}", e)
            eventState.tryEmit(
              EventState.ShowMessage(e.message ?: getString(CommonRes.string.unknown)),
            )
          }
        }
        QRUserCanceled -> Unit
        QRMissingPermission -> {
          eventState.tryEmit(
            EventState.ShowMessage(getString(Res.string.import_from_qr_no_permission)),
          )
        }
        is QRError -> {
          eventState.tryEmit(EventState.ShowMessage(getString(Res.string.import_from_qr_exception)))
        }
      }
    }
  }

  private fun createProfile(type: Profile.Type) {
    viewModelScope.launch {
      try {
        val name = getString(CommonRes.string.new_profile)
        val uuid = withProfile { create(type, name) }
        eventState.tryEmit(EventState.LaunchProperties(uuid))
      } catch (e: Exception) {
        Logger.e("Create profile failed: ${e.message}", e)
        eventState.tryEmit(EventState.ShowMessage(e.message ?: getString(CommonRes.string.unknown)))
      }
    }
  }

  private fun loadProviders() {
    viewModelScope.launch {
      val providers =
        withContext(Dispatchers.IO) {
          val externalProviders =
            application.packageManager
              .queryIntentActivities(Intent(Intents.ACTION_PROVIDE_URL), 0)
              .map {
                val activity = it.activityInfo
                val name = activity.applicationInfo.loadLabel(application.packageManager)
                val summary = activity.loadLabel(application.packageManager)
                val icon = activity.loadIcon(application.packageManager)
                val intent =
                  Intent(Intents.ACTION_PROVIDE_URL)
                    .setComponent(ComponentName(activity.packageName, activity.name))
                ProfileProvider.External(name.toString(), summary.toString(), icon, intent)
              }

          listOf(
            ProfileProvider.File,
            ProfileProvider.Url,
            ProfileProvider.QR,
          ) + externalProviders
        }
      uiState.update { it.copy(providers = providers) }
    }
  }

  data class UiState(val providers: List<ProfileProvider> = emptyList())

  sealed interface EventState {
    data object LaunchQRScanner : EventState

    data class LaunchExternalProvider(val intent: Intent) : EventState

    data class LaunchProperties(val uuid: Uuid) : EventState

    data class OpenAppSettings(val uri: Uri) : EventState

    data class ShowMessage(val message: String) : EventState

    data object Finish : EventState
  }
}
