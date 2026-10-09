package com.github.kr328.clash.profile.vm

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.glue.remote.Remote
import com.github.kr328.clash.glue.util.withClash
import com.github.kr328.clash.profile.Res
import com.github.kr328.clash.profile.format_update_provider_failure
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

internal class ProvidersViewModel(private val application: Application) : ViewModel() {
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
          ProfileLoaded -> fetch()
          else -> Unit
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

  fun onUpdateAll() {
    uiState.value.providers.forEach { state ->
      if (state.updating || state.provider.vehicleType == Inline) return@forEach
      onUpdate(state.provider)
    }
  }

  fun onUpdate(provider: Provider) {
    updateProviderState(provider) { it.copy(updating = true) }

    viewModelScope.launch {
      try {
        withClash { updateProvider(provider.type, provider.name) }
        updateProviderState(provider) {
          it.copy(updating = false, updatedAt = System.currentTimeMillis())
        }
      } catch (e: Exception) {
        Logger.e("Update provider ${provider.name} failed: ${e.message}", e)
        updateProviderState(provider) { it.copy(updating = false) }
        val errorMessage = e.localizedMessage ?: e.message ?: e.toString()
        eventState.tryEmit(
          EventState.ShowMessage(
            getString(
              Res.string.format_update_provider_failure,
              provider.name,
              errorMessage,
            ),
          ),
        )
      }
    }
  }

  private fun providerKey(provider: Provider): String {
    return "${provider.type}-${provider.name}"
  }

  private fun updateProviderState(
    provider: Provider,
    transform: (UiState.ProviderItemState) -> UiState.ProviderItemState,
  ) {
    val key = providerKey(provider)

    uiState.update { current ->
      current.copy(
        providers =
          current.providers.map { state ->
            if (providerKey(state.provider) == key) transform(state) else state
          },
      )
    }
  }

  fun fetch() {
    fetchJob?.cancel()
    fetchJob = viewModelScope.launch {
      val providers = withClash { queryProviders().sorted() }
      uiState.update { current ->
        val existingMap = current.providers.associateBy { providerKey(it.provider) }
        val newStates = providers.map { provider ->
          val key = providerKey(provider)
          existingMap[key]?.let { existing ->
            existing.copy(
              provider = provider,
              updatedAt =
                if (existing.updating) existing.updatedAt
                else maxOf(existing.updatedAt, provider.updatedAt),
            )
          }
            ?: UiState.ProviderItemState(
              provider = provider,
              updatedAt = provider.updatedAt,
              updating = false,
            )
        }
        current.copy(providers = newStates)
      }
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

  data class UiState(
    val providers: List<ProviderItemState> = emptyList(),
    val currentTime: Long = System.currentTimeMillis(),
  ) {
    data class ProviderItemState(val provider: Provider, val updatedAt: Long, val updating: Boolean)
  }

  sealed interface EventState {
    data class ShowMessage(val message: String) : EventState
  }
}
