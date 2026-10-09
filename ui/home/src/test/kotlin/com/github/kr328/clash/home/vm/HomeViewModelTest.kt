package com.github.kr328.clash.home.vm

import android.content.Intent
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.github.kr328.clash.core.model.Traffic
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.glue.remote.Broadcasts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class HomeViewModelTest : KoinComponent {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private lateinit var dependencies: TestHomeDependencies
  private lateinit var viewModel: HomeViewModel

  @Before
  fun setUp() {
    dependencies = TestHomeDependencies()
    startKoin {
      modules(
        module {
          single<HomeViewModel.Dependencies> { dependencies }
          single { HomeViewModel(get()) }
        },
      )
    }
    viewModel = get()
  }

  @After
  fun tearDown() {
    stopKoin()
  }

  @Test
  fun uiState_whenServiceStartsBeforeProfileLoaded_thenHideMode() = runTest {
    dependencies.apply {
      clashRunning.value = true
      profileLoaded.value = false
      mode = TunnelState.Mode.Rule
    }

    dependencies.eventsFlow.emit(Broadcasts.Event.Started)

    assertThat(viewModel.uiState.value.mode).isNull()
    assertThat(dependencies.queryModeCalls).isEqualTo(0)

    dependencies.mode = TunnelState.Mode.Global
    dependencies.profileLoaded.value = true

    assertThat(viewModel.uiState.value.mode).isEqualTo("Global Mode")
    assertThat(dependencies.queryModeCalls).isEqualTo(1)
  }

  @Test
  fun uiState_whenProfileLoadedStateIsAlreadyTrue_thenShowMode() = runTest {
    dependencies.apply {
      clashRunning.value = true
      profileLoaded.value = true
      mode = TunnelState.Mode.Global
    }

    viewModel.fetch()

    assertThat(viewModel.uiState.value.mode).isEqualTo("Global Mode")
  }

  @Test
  fun uiState_whenProfileLoadedEventIsReceived_thenRefreshMode() = runTest {
    dependencies.apply {
      clashRunning.value = true
      profileLoaded.value = true
      mode = TunnelState.Mode.Rule
    }

    viewModel.fetch()
    assertThat(viewModel.uiState.value.mode).isEqualTo("Rule Mode")

    dependencies.mode = TunnelState.Mode.Global
    dependencies.eventsFlow.emit(Broadcasts.Event.ProfileLoaded)

    assertThat(viewModel.uiState.value.mode).isEqualTo("Global Mode")
  }

  @Test
  fun toggleStatus_whenToggled_thenTransitionStateIsCorrect() = runTest {
    // Initial state: not running, not transitioning
    assertThat(viewModel.clashRunning.value).isEqualTo(false)
    assertThat(viewModel.uiState.value.isTransitioning).isEqualTo(false)

    // Act: Click toggle to start
    viewModel.toggleStatus()

    // Assert: transitions to true
    assertThat(viewModel.uiState.value.isTransitioning).isEqualTo(true)

    // Act: clash service starts
    dependencies.clashRunning.value = true

    // Assert: transitions to false
    assertThat(viewModel.uiState.value.isTransitioning).isEqualTo(false)

    // Act: Click toggle to stop
    viewModel.toggleStatus()

    // Assert: transitions to true
    assertThat(viewModel.uiState.value.isTransitioning).isEqualTo(true)

    // Act: clash service stops
    dependencies.clashRunning.value = false

    // Assert: transitions to false
    assertThat(viewModel.uiState.value.isTransitioning).isEqualTo(false)
  }

  @Test
  fun toggleStatus_whenToggledAndScreenRefreshes_thenTransitionStateIsRetained() = runTest {
    // Act: Click toggle to start
    viewModel.toggleStatus()

    // Assert: transitions to true
    assertThat(viewModel.uiState.value.isTransitioning).isEqualTo(true)

    // Act: fetch is triggered again (e.g. screen resumes)
    viewModel.fetch()

    // Assert: transition state is retained, not cleared
    assertThat(viewModel.uiState.value.isTransitioning).isEqualTo(true)

    // Act: clash service starts
    dependencies.clashRunning.value = true

    // Assert: transitions to false
    assertThat(viewModel.uiState.value.isTransitioning).isEqualTo(false)
  }

  @Test
  fun trafficPolling_whenResumedAndPaused_startsAndStops() = runTest {
    dependencies.clashRunning.value = true

    viewModel.resume()
    viewModel.pause()
  }

  private class TestHomeDependencies : HomeViewModel.Dependencies {
    override val clashRunning = MutableStateFlow(false)
    override val profileLoaded = MutableStateFlow(false)
    val eventsFlow = MutableSharedFlow<Broadcasts.Event>(extraBufferCapacity = 16)
    override val events: Flow<Broadcasts.Event> = eventsFlow

    var mode: TunnelState.Mode = TunnelState.Mode.Rule
    var queryModeCalls = 0

    override suspend fun queryActiveProfileName(): String = "Profile"

    override suspend fun hasImportedActiveProfile(): Boolean = true

    override suspend fun queryMode(): TunnelState.Mode {
      queryModeCalls += 1
      return mode
    }

    override suspend fun queryHasProviders(): Boolean = true

    override suspend fun queryTrafficTotal(): Traffic = Traffic(0)

    override fun modeText(mode: TunnelState.Mode): String {
      return when (mode) {
        TunnelState.Mode.Direct -> "Direct Mode"
        TunnelState.Mode.Global -> "Global Mode"
        TunnelState.Mode.Rule -> "Rule Mode"
      }
    }

    override fun unableToStartVpnText(): String = "Unable to start VPN"

    override fun startClashService(): Intent? = null

    override fun stopClashService() = Unit
  }
}
