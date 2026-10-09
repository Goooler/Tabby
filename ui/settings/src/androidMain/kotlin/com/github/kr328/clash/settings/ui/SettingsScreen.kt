package com.github.kr328.clash.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common.settings
import com.github.kr328.clash.settings.Res
import com.github.kr328.clash.settings.app
import com.github.kr328.clash.settings.meta_features
import com.github.kr328.clash.settings.network
import com.github.kr328.clash.settings.override
import com.github.kr328.clash.ui.component.Spacer
import com.github.kr328.clash.ui.component.TabbyScaffold
import com.github.kr328.clash.ui.icon.BaselineDns
import com.github.kr328.clash.ui.icon.BaselineExtension
import com.github.kr328.clash.ui.icon.BaselineMihomo
import com.github.kr328.clash.ui.icon.BaselineSettings
import com.github.kr328.clash.ui.icon.TabbyIcons
import com.github.kr328.clash.ui.theme.PreviewTabby
import com.github.kr328.clash.ui.theme.TabbyThemeWrapper
import com.github.kr328.clash.ui.theme.tabbyDimens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsScreen(
  modifier: Modifier = Modifier,
  onOpenAppSettings: () -> Unit,
  onOpenNetworkSettings: () -> Unit,
  onOpenOverrideSettings: () -> Unit,
  onOpenMetaFeatureSettings: () -> Unit,
) {
  TabbyScaffold(title = stringResource(CommonRes.string.settings), modifier = modifier) {
    innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
    ) {
      SettingsEntryItem(
        icon = TabbyIcons.BaselineSettings,
        titleRes = Res.string.app,
        onClick = onOpenAppSettings,
      )

      SettingsEntryItem(
        icon = TabbyIcons.BaselineDns,
        titleRes = Res.string.network,
        onClick = onOpenNetworkSettings,
      )

      SettingsEntryItem(
        icon = TabbyIcons.BaselineExtension,
        titleRes = Res.string.override,
        onClick = onOpenOverrideSettings,
      )

      SettingsEntryItem(
        icon = TabbyIcons.BaselineMihomo,
        titleRes = Res.string.meta_features,
        onClick = onOpenMetaFeatureSettings,
      )
    }
  }
}

@Composable
private fun SettingsEntryItem(
  icon: ImageVector,
  titleRes: StringResource,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val dimens = tabbyDimens
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = dimens.itemMinHeight)
        .clickable(onClick = onClick)
        .padding(
          top = dimens.itemPaddingVertical,
          bottom = dimens.itemPaddingVertical,
          end = dimens.settingsItemEndPadding,
        ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Spacer(dimens.itemHeaderMargin)
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(dimens.itemHeaderComponentSize),
    )
    Spacer(dimens.itemHeaderMargin)
    Text(text = stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
  }
}

@PreviewWrapper(TabbyThemeWrapper::class)
@PreviewTabby
@Composable
private fun SettingsScreenPreview() {
  SettingsScreen(
    onOpenAppSettings = {},
    onOpenNetworkSettings = {},
    onOpenOverrideSettings = {},
    onOpenMetaFeatureSettings = {},
  )
}
