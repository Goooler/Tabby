package com.github.kr328.clash.home.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.R as CommonR
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common.loading
import com.github.kr328.clash.common.logs
import com.github.kr328.clash.common.not_selected
import com.github.kr328.clash.common.profiles
import com.github.kr328.clash.common.providers
import com.github.kr328.clash.common.proxy
import com.github.kr328.clash.common.running
import com.github.kr328.clash.common.settings
import com.github.kr328.clash.common.stopped
import com.github.kr328.clash.common.tabby
import com.github.kr328.clash.common.tap_to_start
import com.github.kr328.clash.home.Res
import com.github.kr328.clash.home.format_profile_activated
import com.github.kr328.clash.home.format_traffic_forwarded
import com.github.kr328.clash.home.help
import com.github.kr328.clash.home.no_profile_selected
import com.github.kr328.clash.home.profile
import com.github.kr328.clash.home.vm.HomeViewModel
import com.github.kr328.clash.ui.component.Spacer
import com.github.kr328.clash.ui.component.TabbyScaffold
import com.github.kr328.clash.ui.icon.BaselineApps
import com.github.kr328.clash.ui.icon.BaselineAssignment
import com.github.kr328.clash.ui.icon.BaselineHelpCenter
import com.github.kr328.clash.ui.icon.BaselineSettings
import com.github.kr328.clash.ui.icon.BaselineSwapVerticalCircle
import com.github.kr328.clash.ui.icon.BaselineSync
import com.github.kr328.clash.ui.icon.BaselineViewList
import com.github.kr328.clash.ui.icon.OutlineCheckCircle
import com.github.kr328.clash.ui.icon.OutlineNotInterested
import com.github.kr328.clash.ui.icon.TabbyIcons
import com.github.kr328.clash.ui.theme.PreviewTabby
import com.github.kr328.clash.ui.theme.TabbyDarkSurface
import com.github.kr328.clash.ui.theme.TabbyLightStopped
import com.github.kr328.clash.ui.theme.TabbyOnPrimary
import com.github.kr328.clash.ui.theme.TabbyThemeWrapper
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun HomeScreen(
  modifier: Modifier = Modifier,
  viewModel: HomeViewModel = koinViewModel<HomeViewModel>(),
  onOpenProxy: () -> Unit,
  onOpenProfiles: () -> Unit,
  onOpenProviders: () -> Unit,
  onOpenLogs: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenHelp: () -> Unit,
) {
  val clashRunning by viewModel.clashRunning.collectAsStateWithLifecycle()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  val noProfileText = stringResource(Res.string.no_profile_selected)
  val profilesActionText = stringResource(CommonRes.string.profiles)

  val vpnLauncher =
    rememberLauncherForActivityResult(StartActivityForResult()) { result ->
      when (result.resultCode) {
        Activity.RESULT_OK -> viewModel.onVpnPermissionGranted()
        else -> viewModel.onVpnPermissionDenied()
      }
    }

  LaunchedEffect(viewModel) {
    viewModel.eventState.collect { event ->
      when (event) {
        is RequestVpnPermission -> vpnLauncher.launch(event.intent)
        ShowNoProfileMessage -> {
          val result =
            snackbarHostState.showSnackbar(
              message = noProfileText,
              actionLabel = profilesActionText,
              duration = SnackbarDuration.Long,
            )

          if (result == SnackbarResult.ActionPerformed) onOpenProfiles()
        }
        is ShowMessage -> {
          snackbarHostState.showSnackbar(message = event.message)
        }
      }
    }
  }

  LifecycleStartEffect(viewModel) {
    viewModel.resume()
    onStopOrDispose { viewModel.pause() }
  }

  HomeContent(
    modifier = modifier,
    snackbarHostState = snackbarHostState,
    clashRunning = clashRunning,
    forwarded = uiState.forwarded,
    mode = uiState.mode,
    profileName = uiState.profileName,
    hasProviders = uiState.hasProviders,
    isTransitioning = uiState.isTransitioning,
    onToggleStatus = viewModel::toggleStatus,
    onOpenProxy = onOpenProxy,
    onOpenProfiles = onOpenProfiles,
    onOpenProviders = onOpenProviders,
    onOpenLogs = onOpenLogs,
    onOpenSettings = onOpenSettings,
    onOpenHelp = onOpenHelp,
  )
}

@Composable
private fun HomeContent(
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState,
  clashRunning: Boolean,
  forwarded: String?,
  mode: String?,
  profileName: String?,
  hasProviders: Boolean,
  isTransitioning: Boolean,
  onToggleStatus: () -> Unit,
  onOpenProxy: () -> Unit,
  onOpenProfiles: () -> Unit,
  onOpenProviders: () -> Unit,
  onOpenLogs: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenHelp: () -> Unit,
) {
  val darkTheme = isSystemInDarkTheme()
  val stoppedColor = if (darkTheme) TabbyDarkSurface else TabbyLightStopped

  TabbyScaffold(
    title = "",
    modifier = modifier,
    topBar = {},
    snackbarHostState = snackbarHostState,
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = 30.dp)
          .verticalScroll(rememberScrollState()),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().height(90.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Spacer(10.dp)
        Image(
          painter = painterResource(CommonR.drawable.ic_tabby_foreground),
          contentDescription = null,
          modifier = Modifier.size(75.dp),
        )
        Spacer(10.dp)
        Text(
          text = stringResource(CommonRes.string.tabby),
          style = MaterialTheme.typography.headlineLarge,
        )
      }

      HomeActionCard(
        modifier = Modifier.padding(vertical = cardMarginVertical),
        icon =
          when {
            isTransitioning -> TabbyIcons.BaselineSync
            clashRunning -> TabbyIcons.OutlineCheckCircle
            else -> TabbyIcons.OutlineNotInterested
          },
        text =
          when {
            isTransitioning -> stringResource(CommonRes.string.loading)
            clashRunning -> stringResource(CommonRes.string.running)
            else -> stringResource(CommonRes.string.stopped)
          },
        subtext =
          when {
            isTransitioning -> null
            clashRunning && forwarded != null ->
              stringResource(Res.string.format_traffic_forwarded, forwarded)
            else -> stringResource(CommonRes.string.tap_to_start)
          },
        backgroundColor =
          when {
            isTransitioning -> stoppedColor
            clashRunning -> MaterialTheme.colorScheme.primary
            else -> stoppedColor
          },
        contentColor = TabbyOnPrimary,
        onClick = onToggleStatus,
        enabled = !isTransitioning,
      )

      AnimatedVisibility(visible = clashRunning) {
        HomeActionCard(
          modifier = Modifier.padding(vertical = cardMarginVertical),
          icon = TabbyIcons.BaselineApps,
          text = stringResource(CommonRes.string.proxy),
          subtext = mode,
          backgroundColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.onSurface,
          onClick = onOpenProxy,
        )
      }

      HomeActionCard(
        modifier = Modifier.padding(vertical = cardMarginVertical),
        icon = TabbyIcons.BaselineViewList,
        text = stringResource(Res.string.profile),
        subtext =
          if (profileName != null) stringResource(Res.string.format_profile_activated, profileName)
          else stringResource(CommonRes.string.not_selected),
        backgroundColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        onClick = onOpenProfiles,
      )

      AnimatedVisibility(visible = clashRunning && hasProviders) {
        HomeActionLabel(
          modifier = Modifier.padding(vertical = labelMarginVertical),
          icon = TabbyIcons.BaselineSwapVerticalCircle,
          text = stringResource(CommonRes.string.providers),
          onClick = onOpenProviders,
        )
      }

      HomeActionLabel(
        modifier = Modifier.padding(vertical = labelMarginVertical),
        icon = TabbyIcons.BaselineAssignment,
        text = stringResource(CommonRes.string.logs),
        onClick = onOpenLogs,
      )
      HomeActionLabel(
        modifier = Modifier.padding(vertical = labelMarginVertical),
        icon = TabbyIcons.BaselineSettings,
        text = stringResource(CommonRes.string.settings),
        onClick = onOpenSettings,
      )
      HomeActionLabel(
        modifier = Modifier.padding(vertical = labelMarginVertical),
        icon = TabbyIcons.BaselineHelpCenter,
        text = stringResource(Res.string.help),
        onClick = onOpenHelp,
      )
    }
  }
}

@Composable
private fun HomeActionCard(
  icon: ImageVector,
  text: String,
  subtext: String?,
  backgroundColor: Color,
  contentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Card(
    modifier = modifier.fillMaxWidth().heightIn(min = 85.dp),
    onClick = onClick,
    enabled = enabled,
    colors =
      CardDefaults.cardColors(
        containerColor = backgroundColor,
        contentColor = contentColor,
        disabledContainerColor = backgroundColor,
        disabledContentColor = contentColor,
      ),
    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
  ) {
    Row(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = actionItemPaddingHorizontal, vertical = actionItemPaddingVertical),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(actionIconSize),
        tint = contentColor,
      )
      Spacer(actionItemPaddingHorizontal)
      Column {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = contentColor)
        if (subtext != null) {
          Spacer(5.dp)
          Text(text = subtext, style = MaterialTheme.typography.bodyMedium, color = contentColor)
        }
      }
    }
  }
}

@Composable
private fun HomeActionLabel(
  icon: ImageVector,
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 60.dp)
        .clickable(onClick = onClick)
        .padding(vertical = actionItemPaddingVertical),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Spacer(actionItemPaddingHorizontal)
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(actionIconSize))
    Spacer(actionItemPaddingHorizontal)
    Text(text = text, style = MaterialTheme.typography.bodyLarge)
  }
}

private val cardMarginVertical = 5.dp
private val labelMarginVertical = 2.dp
private val actionItemPaddingHorizontal = 20.dp
private val actionItemPaddingVertical = 15.dp
private val actionIconSize = 30.dp

@PreviewWrapper(TabbyThemeWrapper::class)
@PreviewTabby
@Composable
private fun HomeContentRunningPreview() {
  HomeContent(
    snackbarHostState = SnackbarHostState(),
    clashRunning = true,
    forwarded = "1.23 GB",
    mode = "Rule",
    profileName = "My Profile",
    hasProviders = true,
    isTransitioning = false,
    onToggleStatus = {},
    onOpenProxy = {},
    onOpenProfiles = {},
    onOpenProviders = {},
    onOpenLogs = {},
    onOpenSettings = {},
    onOpenHelp = {},
  )
}

@PreviewWrapper(TabbyThemeWrapper::class)
@PreviewTabby
@Composable
private fun HomeContentStoppedPreview() {
  HomeContent(
    snackbarHostState = SnackbarHostState(),
    clashRunning = false,
    forwarded = null,
    mode = null,
    profileName = null,
    hasProviders = false,
    isTransitioning = false,
    onToggleStatus = {},
    onOpenProxy = {},
    onOpenProfiles = {},
    onOpenProviders = {},
    onOpenLogs = {},
    onOpenSettings = {},
    onOpenHelp = {},
  )
}
