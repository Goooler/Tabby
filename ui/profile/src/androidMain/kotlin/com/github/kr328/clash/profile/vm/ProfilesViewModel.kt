package com.github.kr328.clash.profile.vm

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common.unknown
import com.github.kr328.clash.core.model.Profile
import com.github.kr328.clash.glue.remote.Remote
import com.github.kr328.clash.glue.util.withProfile
import com.github.kr328.clash.profile.Res
import com.github.kr328.clash.profile.active_unsaved_tips
import com.github.kr328.clash.profile.toast_profile_updated_complete
import com.github.kr328.clash.profile.toast_profile_updated_failed
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

internal class ProfilesViewModel(private val application: Application) : ViewModel() {
  private var elapsedJob: Job? = null
  private var fetchJob: Job? = null

  val uiState: StateFlow<UiState>
    field = MutableStateFlow(UiState())

  val eventState: SharedFlow<EventState>
    field = MutableSharedFlow(extraBufferCapacity = 64)

  init {
    viewModelScope.launch {
      Remote.broadcasts.event.collect { event ->
        when (event) {
          ServiceRecreated,
          Started,
          ProfileChanged,
          ProfileLoaded -> fetch()
          is Stopped -> Unit
          is ProfileUpdateCompleted -> {
            event.uuid?.let { uuid -> showProfileUpdateCompleted(uuid) }
          }
          is ProfileUpdateFailed -> {
            event.uuid?.let { uuid -> showProfileUpdateFailed(uuid, event.reason) }
          }
        }
      }
    }
  }

  fun resume() {
    startElapsedTicker()
    fetch()
  }

  fun pause() {
    elapsedJob?.cancel()
    elapsedJob = null
  }

  fun onOpenCreate() {
    eventState.tryEmit(EventState.OpenCreate)
  }

  fun onActivate(profile: Profile) {
    viewModelScope.launch {
      if (profile.imported) {
        withProfile { setActive(profile) }
      } else {
        eventState.tryEmit(
          EventState.ShowEditableMessage(
            getString(Res.string.active_unsaved_tips),
            profile.uuid,
          ),
        )
      }
    }
  }

  fun onUpdateAll() {
    if (uiState.value.allUpdating) return

    viewModelScope.launch {
      uiState.update { it.copy(allUpdating = true) }
      try {
        withProfile {
          queryAll().forEach { profile ->
            if (profile.imported && profile.type != File) {
              update(profile.uuid)
            }
          }
        }
      } finally {
        uiState.update { it.copy(allUpdating = false) }
      }
    }
  }

  fun onUpdate(profile: Profile) {
    viewModelScope.launch { withProfile { update(profile.uuid) } }
  }

  fun onEdit(profile: Profile) {
    eventState.tryEmit(EventState.OpenEdit(profile.uuid))
  }

  fun onDuplicate(profile: Profile) {
    viewModelScope.launch {
      val uuid = withProfile { clone(profile.uuid) }
      eventState.tryEmit(EventState.OpenEdit(uuid))
    }
  }

  fun onDelete(profile: Profile) {
    viewModelScope.launch { withProfile { delete(profile.uuid) } }
  }

  fun fetch() {
    fetchJob?.cancel()
    fetchJob = viewModelScope.launch {
      val profiles = withProfile { queryAll() }
      val hasUpdatableProfile =
        withContext(Dispatchers.Default) { profiles.any { it.imported && it.type != File } }

      uiState.update { it.copy(profiles = profiles, hasUpdatableProfile = hasUpdatableProfile) }
    }
  }

  private fun startElapsedTicker() {
    if (elapsedJob?.isActive == true) return

    elapsedJob = viewModelScope.launch {
      while (isActive) {
        delay(1.minutes)
        uiState.update { it.copy(currentTime = System.currentTimeMillis()) }
      }
    }
  }

  private suspend fun showProfileUpdateCompleted(uuid: Uuid) {
    val name = withProfile { queryByUUID(uuid)?.name.orEmpty() }
    eventState.tryEmit(
      EventState.ShowMessage(getString(Res.string.toast_profile_updated_complete, name)),
    )
  }

  private suspend fun showProfileUpdateFailed(uuid: Uuid, reason: String?) {
    val name = withProfile { queryByUUID(uuid)?.name.orEmpty() }
    val displayReason = reason?.takeUnless { it.isBlank() } ?: getString(CommonRes.string.unknown)
    eventState.tryEmit(
      EventState.ShowEditableMessage(
        getString(Res.string.toast_profile_updated_failed, name, displayReason),
        uuid,
      ),
    )
  }

  data class UiState(
    val profiles: List<Profile> = emptyList(),
    val allUpdating: Boolean = false,
    val hasUpdatableProfile: Boolean = false,
    val currentTime: Long = System.currentTimeMillis(),
  )

  sealed interface EventState {
    data object OpenCreate : EventState

    data class OpenEdit(val uuid: Uuid) : EventState

    data class ShowMessage(val message: String) : EventState

    data class ShowEditableMessage(val message: String, val uuid: Uuid) : EventState
  }
}
