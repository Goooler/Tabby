package com.github.kr328.clash.profile.ui

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.new_profile
import com.github.kr328.clash.profile.model.ProfileProvider
import com.github.kr328.clash.profile.vm.NewProfileViewModel
import com.github.kr328.clash.ui.component.SizeSpacer
import com.github.kr328.clash.ui.component.Spacer
import com.github.kr328.clash.ui.component.TabbyScaffold
import com.github.kr328.clash.ui.icon.BaselineExtension
import com.github.kr328.clash.ui.icon.TabbyIcons
import com.github.kr328.clash.ui.theme.PreviewTabby
import com.github.kr328.clash.ui.theme.TabbyThemeWrapper
import com.github.kr328.clash.ui.theme.tabbyDimens
import io.github.g00fy2.quickie.ScanQRCode
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun NewProfileScreen(
  modifier: Modifier = Modifier,
  viewModel: NewProfileViewModel = koinViewModel(),
  onProperties: (Uuid) -> Unit,
  onFinish: () -> Unit,
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  val qrLauncher =
    rememberLauncherForActivityResult(ScanQRCode()) { result -> viewModel.onQRResult(result) }

  val externalProviderLauncher =
    rememberLauncherForActivityResult(StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK) {
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val name = result.data?.getStringExtra(Intents.EXTRA_NAME)
        viewModel.onExternalProviderResult(uri, name)
      }
    }

  LaunchedEffect(viewModel) {
    viewModel.eventState.collect { event ->
      when (event) {
        Finish -> onFinish()
        LaunchQRScanner -> qrLauncher.launch(null)
        is LaunchExternalProvider -> {
          externalProviderLauncher.launch(event.intent)
        }
        is LaunchProperties -> onProperties(event.uuid)
        is OpenAppSettings -> {
          context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(event.uri),
          )
        }
        is ShowMessage -> {
          snackbarHostState.showSnackbar(message = event.message)
        }
      }
    }
  }

  NewProfileContent(
    modifier = modifier,
    snackbarHostState = snackbarHostState,
    providers = uiState.providers,
    onCreate = viewModel::onCreate,
    onDetail = viewModel::onDetail,
  )
}

@Composable
private fun NewProfileContent(
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState,
  providers: List<ProfileProvider>,
  onCreate: (ProfileProvider) -> Unit,
  onDetail: (ProfileProvider.External) -> Unit,
) {
  TabbyScaffold(
    title = stringResource(CommonRes.string.new_profile),
    modifier = modifier,
    snackbarHostState = snackbarHostState,
  ) { innerPadding ->
    LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      items(items = providers) { provider ->
        ProfileProviderItem(
          provider = provider,
          onClick = { onCreate(provider) },
          onLongClick = { if (provider is ProfileProvider.External) onDetail(provider) },
        )
      }
    }
  }
}

@Composable
private fun ProfileProviderItem(
  provider: ProfileProvider,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val density = LocalDensity.current
  val dimens = tabbyDimens
  val itemPaddingVertical = dimens.itemPaddingVertical
  val headerSize = dimens.itemHeaderComponentSize
  val headerMargin = dimens.itemHeaderMargin
  val textMargin = dimens.itemTextMargin
  val iconSizePx = with(density) { headerSize.toPx().roundToInt() }
  val iconVector = provider.icon as? ImageVector
  val iconDrawable = provider.icon as? Drawable
  val iconPainter =
    if (iconVector != null) {
      rememberVectorPainter(iconVector)
    } else {
      remember(iconDrawable, iconSizePx) {
        iconDrawable
          ?.toBitmap(width = iconSizePx, height = iconSizePx)
          ?.asImageBitmap()
          ?.let(::BitmapPainter)
      }
    }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(vertical = itemPaddingVertical),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Spacer(headerMargin)
    if (iconPainter != null) {
      Icon(painter = iconPainter, contentDescription = null, modifier = Modifier.size(headerSize))
    } else {
      SizeSpacer(headerSize)
    }
    Spacer(headerMargin)
    Column {
      val nameText =
        when (val name = provider.name) {
          is StringResource -> stringResource(name)
          is String -> name
          else -> ""
        }
      val summaryText =
        when (val summary = provider.summary) {
          is StringResource -> stringResource(summary)
          is String -> summary
          else -> ""
        }
      Text(text = nameText, style = MaterialTheme.typography.bodyLarge)
      Text(
        text = summaryText,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = textMargin),
      )
    }
  }
}

@PreviewWrapper(TabbyThemeWrapper::class)
@PreviewTabby
@Composable
private fun NewProfileContentPreview() {
  val providers =
    listOf(
      ProfileProvider.File,
      ProfileProvider.Url,
      ProfileProvider.QR,
      ProfileProvider.External(
        name = "External Provider",
        summary = "Import from external app",
        icon = TabbyIcons.BaselineExtension,
        intent = Intent(),
      ),
    )

  NewProfileContent(
    snackbarHostState = SnackbarHostState(),
    providers = providers,
    onCreate = {},
    onDetail = {},
  )
}
