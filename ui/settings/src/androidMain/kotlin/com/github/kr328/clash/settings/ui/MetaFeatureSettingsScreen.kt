package com.github.kr328.clash.settings.ui

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common.copied
import com.github.kr328.clash.common.ok
import com.github.kr328.clash.common.reset
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.settings.Res
import com.github.kr328.clash.settings.age_key_category
import com.github.kr328.clash.settings.age_key_copy
import com.github.kr328.clash.settings.age_key_generate
import com.github.kr328.clash.settings.age_key_generate_summary
import com.github.kr328.clash.settings.age_key_to_public
import com.github.kr328.clash.settings.age_key_type_hybrid
import com.github.kr328.clash.settings.age_key_type_x25519
import com.github.kr328.clash.settings.age_public_key
import com.github.kr328.clash.settings.age_public_key_error
import com.github.kr328.clash.settings.age_secret_key
import com.github.kr328.clash.settings.age_secret_key_error
import com.github.kr328.clash.settings.always
import com.github.kr328.clash.settings.dont_modify
import com.github.kr328.clash.settings.error
import com.github.kr328.clash.settings.find_process_mode
import com.github.kr328.clash.settings.force_dns_mapping
import com.github.kr328.clash.settings.force_domain
import com.github.kr328.clash.settings.general
import com.github.kr328.clash.settings.geodata_mode
import com.github.kr328.clash.settings.geofile_import_failed
import com.github.kr328.clash.settings.geofile_imported
import com.github.kr328.clash.settings.geofile_unknown_db_format
import com.github.kr328.clash.settings.geofile_unknown_db_format_message
import com.github.kr328.clash.settings.geox_files
import com.github.kr328.clash.settings.import_asn_file
import com.github.kr328.clash.settings.import_country_file
import com.github.kr328.clash.settings.import_geoip_file
import com.github.kr328.clash.settings.import_geosite_file
import com.github.kr328.clash.settings.meta_features
import com.github.kr328.clash.settings.off
import com.github.kr328.clash.settings.override_destination
import com.github.kr328.clash.settings.parse_pure_ip
import com.github.kr328.clash.settings.press_to_import
import com.github.kr328.clash.settings.skip_domain
import com.github.kr328.clash.settings.skip_dst_address
import com.github.kr328.clash.settings.skip_src_address
import com.github.kr328.clash.settings.sniff_http_override_destination
import com.github.kr328.clash.settings.sniff_http_ports
import com.github.kr328.clash.settings.sniff_quic_override_destination
import com.github.kr328.clash.settings.sniff_quic_ports
import com.github.kr328.clash.settings.sniff_tls_override_destination
import com.github.kr328.clash.settings.sniff_tls_ports
import com.github.kr328.clash.settings.sniffer_setting
import com.github.kr328.clash.settings.strategy
import com.github.kr328.clash.settings.strict
import com.github.kr328.clash.settings.tcp_concurrent
import com.github.kr328.clash.settings.unified_delay
import com.github.kr328.clash.settings.vm.MetaFeatureSettingsViewModel
import com.github.kr328.clash.settings.vm.MetaFeatureSettingsViewModel.ImportType
import com.github.kr328.clash.ui.component.TabbyScaffold
import com.github.kr328.clash.ui.icon.BaselineReplay
import com.github.kr328.clash.ui.icon.TabbyIcons
import com.github.kr328.clash.ui.nav.TabbyNavDisplay
import com.github.kr328.clash.ui.nav.addIfNotLast
import com.github.kr328.clash.ui.nav.rememberNavBackStackBuilder
import com.github.kr328.clash.ui.theme.PreviewTabby
import com.github.kr328.clash.ui.theme.TabbyThemeWrapper
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.preferenceCategory
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private sealed interface MetaFeatureSettingsRoute : NavKey {
  @Serializable data object Main : MetaFeatureSettingsRoute
}

@Composable
internal fun MetaFeatureSettingsScreen(
  modifier: Modifier = Modifier,
  viewModel: MetaFeatureSettingsViewModel = koinViewModel<MetaFeatureSettingsViewModel>(),
  onResetCompleted: () -> Unit,
) {
  LifecycleStartEffect(viewModel) {
    onStopOrDispose {
      viewModel.persist()
    }
  }

  val backStack = rememberNavBackStackBuilder { add(MetaFeatureSettingsRoute.Main) }
  var currentEditableTextListOnApply by remember {
    mutableStateOf<((List<String>?) -> Unit)?>(null)
  }

  TabbyNavDisplay(
    backStack = backStack,
    entryProvider =
      entryProvider {
        entry<MetaFeatureSettingsRoute.Main> {
          val scope = rememberCoroutineScope()
          val configuration by viewModel.configuration.collectAsStateWithLifecycle()
          val importResult by viewModel.importResult.collectAsStateWithLifecycle()
          val snackbarHostState = remember { SnackbarHostState() }
          val importedText = stringResource(Res.string.geofile_imported)
          val importFailedText = stringResource(Res.string.geofile_import_failed)
          var pendingImportType by remember { mutableStateOf<ImportType?>(null) }
          var showUnsupportedFormatDialog by remember { mutableStateOf(false) }
          var validExtensionsSummary by remember { mutableStateOf("") }
          var showResetConfirmDialog by remember { mutableStateOf(false) }
          var showAgeKeyHelper by remember { mutableStateOf(false) }
          var ageKeyHelperHybrid by remember { mutableStateOf(false) }

          LaunchedEffect(importResult) {
            when (val result = importResult) {
              Idle,
              InProgress -> Unit

              is Success -> {
                snackbarHostState.showSnackbar(message = importedText.format(result.displayName))
              }

              is UnsupportedFormat -> {
                validExtensionsSummary = result.summary
                showUnsupportedFormatDialog = true
              }

              Failed -> snackbarHostState.showSnackbar(message = importFailedText)
            }
          }

          val importLauncher =
            rememberLauncherForActivityResult(GetContent()) { uri ->
              val type = pendingImportType ?: return@rememberLauncherForActivityResult
              pendingImportType = null
              viewModel.importGeoFile(uri, type)
            }

          MetaFeatureSettingsContent(
            configuration = configuration,
            actions = viewModel,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
            showResetConfirmDialog = showResetConfirmDialog,
            onShowResetConfirmDialogChange = { showResetConfirmDialog = it },
            onResetConfirmed = {
              viewModel.resetOverride()
              onResetCompleted()
            },
            onImportGeoIp = {
              pendingImportType = GeoIp
              importLauncher.launch("*/*")
            },
            onImportGeoSite = {
              pendingImportType = GeoSite
              importLauncher.launch("*/*")
            },
            onImportCountry = {
              pendingImportType = Country
              importLauncher.launch("*/*")
            },
            onImportASN = {
              pendingImportType = ASN
              importLauncher.launch("*/*")
            },
            onOpenEditableTextList = { title, initialValues, onApply ->
              currentEditableTextListOnApply = onApply
              scope.launch {
                backStack.addIfNotLast(EditableTextList(getString(title), initialValues?.toSet()))
              }
            },
            onAgeKeyHelperRequested = { hybrid ->
              ageKeyHelperHybrid = hybrid
              showAgeKeyHelper = true
            },
          )

          if (showUnsupportedFormatDialog) {
            AlertDialog(
              onDismissRequest = { showUnsupportedFormatDialog = false },
              title = { Text(stringResource(Res.string.geofile_unknown_db_format)) },
              text = {
                Text(
                  stringResource(
                    Res.string.geofile_unknown_db_format_message,
                    validExtensionsSummary,
                  ),
                )
              },
              confirmButton = {
                TextButton(onClick = { showUnsupportedFormatDialog = false }) {
                  Text(text = stringResource(CommonRes.string.ok))
                }
              },
            )
          }

          if (showAgeKeyHelper) {
            AgeKeyHelperDialog(
              hybrid = ageKeyHelperHybrid,
              onDismiss = { showAgeKeyHelper = false },
              onShowMessage = { message -> snackbarHostState.showSnackbar(message = message) },
            )
          }
        }
        editableTextSetScreenEntry(
          onDismiss = {
            currentEditableTextListOnApply = null
            backStack.removeLastOrNull()
          },
          onApply = { newValues ->
            currentEditableTextListOnApply?.invoke(newValues?.toList())
            currentEditableTextListOnApply = null
            backStack.removeLastOrNull()
          },
        )
      },
  )
}

@Composable
private fun MetaFeatureSettingsContent(
  configuration: ConfigurationOverride,
  actions: MetaFeatureSettingsActions,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
  showResetConfirmDialog: Boolean,
  onShowResetConfirmDialogChange: (Boolean) -> Unit,
  onResetConfirmed: () -> Unit,
  onImportGeoIp: () -> Unit,
  onImportGeoSite: () -> Unit,
  onImportCountry: () -> Unit,
  onImportASN: () -> Unit,
  onOpenEditableTextList: (StringResource, List<String>?, (List<String>?) -> Unit) -> Unit,
  onAgeKeyHelperRequested: (Boolean) -> Unit,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  TabbyScaffold(
    title = stringResource(Res.string.meta_features),
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    snackbarHostState = snackbarHostState,
    scrollBehavior = scrollBehavior,
    actions = {
      IconButton(onClick = { onShowResetConfirmDialogChange(true) }) {
        Icon(
          imageVector = TabbyIcons.BaselineReplay,
          contentDescription = stringResource(CommonRes.string.reset),
        )
      }
    },
  ) { innerPadding ->
    ProvidePreferenceLocals {
      LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
        metaAgeKeyItems(onAgeKeyHelperRequested = onAgeKeyHelperRequested)
        metaBasicPreferenceItems(configuration, actions)
        metaSnifferPreferenceItems(configuration, actions, onOpenEditableTextList)
        metaGeoFileItems(
          onImportGeoIp = onImportGeoIp,
          onImportGeoSite = onImportGeoSite,
          onImportCountry = onImportCountry,
          onImportASN = onImportASN,
        )
      }
    }

    if (showResetConfirmDialog) {
      ResetOverrideSettingsDialog(
        onConfirm = {
          onShowResetConfirmDialogChange(false)
          onResetConfirmed()
        },
        onDismiss = { onShowResetConfirmDialogChange(false) },
      )
    }
  }
}

private fun LazyListScope.metaAgeKeyItems(onAgeKeyHelperRequested: (Boolean) -> Unit) {
  preferenceCategory(
    key = "cat_age_key",
    title = { Text(stringResource(Res.string.age_key_category)) },
  )
  preference(
    key = "ageKeyX25519",
    title = { Text(stringResource(Res.string.age_key_type_x25519)) },
    summary = { Text(stringResource(Res.string.age_key_generate_summary)) },
    onClick = { onAgeKeyHelperRequested(false) },
  )
  preference(
    key = "ageKeyHybrid",
    title = { Text(stringResource(Res.string.age_key_type_hybrid)) },
    summary = { Text(stringResource(Res.string.age_key_generate_summary)) },
    onClick = { onAgeKeyHelperRequested(true) },
  )
}

private fun LazyListScope.metaBasicPreferenceItems(
  configuration: ConfigurationOverride,
  actions: MetaFeatureSettingsActions,
) {
  preferenceCategory(key = "cat_general", title = { Text(stringResource(Res.string.general)) })
  listPreference(
    key = "unifiedDelay",
    value = configuration.unifiedDelay,
    onValueChange = actions::updateUnifiedDelay,
    values = booleanOptions,
    title = { Text(stringResource(Res.string.unified_delay)) },
    summary = { Text(stringResource(configuration.unifiedDelay.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "geodataMode",
    value = configuration.geodataMode,
    onValueChange = actions::updateGeodataMode,
    values = booleanOptions,
    title = { Text(stringResource(Res.string.geodata_mode)) },
    summary = { Text(stringResource(configuration.geodataMode.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "tcpConcurrent",
    value = configuration.tcpConcurrent,
    onValueChange = actions::updateTcpConcurrent,
    values = booleanOptions,
    title = { Text(stringResource(Res.string.tcp_concurrent)) },
    summary = { Text(stringResource(configuration.tcpConcurrent.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "findProcessMode",
    value = configuration.findProcessMode,
    onValueChange = actions::updateFindProcessMode,
    values = ConfigurationOverride.FindProcessMode.entries,
    title = { Text(stringResource(Res.string.find_process_mode)) },
    summary = { Text(stringResource(configuration.findProcessMode.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
}

private fun LazyListScope.metaSnifferPreferenceItems(
  configuration: ConfigurationOverride,
  actions: MetaFeatureSettingsActions,
  onOpenEditableTextList: (StringResource, List<String>?, (List<String>?) -> Unit) -> Unit,
) {
  val enabled = configuration.sniffer.enable != false
  preferenceCategory(
    key = "cat_sniffer",
    title = { Text(stringResource(Res.string.sniffer_setting)) },
  )
  listPreference(
    key = "snifferEnable",
    value = configuration.sniffer.enable,
    onValueChange = actions::updateSnifferEnable,
    values = booleanOptions,
    title = { Text(stringResource(Res.string.strategy)) },
    summary = { Text(stringResource(configuration.sniffer.enable.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  preference(
    key = "sniffHttpPorts",
    title = { Text(stringResource(Res.string.sniff_http_ports)) },
    summary = { Text(configuration.sniffer.sniff.http.ports.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.sniff_http_ports,
        configuration.sniffer.sniff.http.ports,
        actions::updateSniffHttpPorts,
      )
    },
  )
  listPreference(
    key = "sniffHttpOverrideDestination",
    value = configuration.sniffer.sniff.http.overrideDestination,
    onValueChange = actions::updateSniffHttpOverrideDestination,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.sniff_http_override_destination)) },
    summary = {
      Text(stringResource(configuration.sniffer.sniff.http.overrideDestination.textRes))
    },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  preference(
    key = "sniffTlsPorts",
    title = { Text(stringResource(Res.string.sniff_tls_ports)) },
    summary = { Text(configuration.sniffer.sniff.tls.ports.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.sniff_tls_ports,
        configuration.sniffer.sniff.tls.ports,
        actions::updateSniffTlsPorts,
      )
    },
  )
  listPreference(
    key = "sniffTlsOverrideDestination",
    value = configuration.sniffer.sniff.tls.overrideDestination,
    onValueChange = actions::updateSniffTlsOverrideDestination,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.sniff_tls_override_destination)) },
    summary = {
      Text(stringResource(configuration.sniffer.sniff.tls.overrideDestination.textRes))
    },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  preference(
    key = "sniffQuicPorts",
    title = { Text(stringResource(Res.string.sniff_quic_ports)) },
    summary = { Text(configuration.sniffer.sniff.quic.ports.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.sniff_quic_ports,
        configuration.sniffer.sniff.quic.ports,
        actions::updateSniffQuicPorts,
      )
    },
  )
  listPreference(
    key = "sniffQuicOverrideDestination",
    value = configuration.sniffer.sniff.quic.overrideDestination,
    onValueChange = actions::updateSniffQuicOverrideDestination,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.sniff_quic_override_destination)) },
    summary = {
      Text(stringResource(configuration.sniffer.sniff.quic.overrideDestination.textRes))
    },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "forceDnsMapping",
    value = configuration.sniffer.forceDnsMapping,
    onValueChange = actions::updateForceDnsMapping,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.force_dns_mapping)) },
    summary = { Text(stringResource(configuration.sniffer.forceDnsMapping.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "parsePureIp",
    value = configuration.sniffer.parsePureIp,
    onValueChange = actions::updateParsePureIp,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.parse_pure_ip)) },
    summary = { Text(stringResource(configuration.sniffer.parsePureIp.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "overrideDestination",
    value = configuration.sniffer.overrideDestination,
    onValueChange = actions::updateOverrideDestination,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.override_destination)) },
    summary = { Text(stringResource(configuration.sniffer.overrideDestination.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  preference(
    key = "forceDomain",
    title = { Text(stringResource(Res.string.force_domain)) },
    summary = { Text(configuration.sniffer.forceDomain.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.force_domain,
        configuration.sniffer.forceDomain,
        actions::updateForceDomain,
      )
    },
  )
  preference(
    key = "skipDomain",
    title = { Text(stringResource(Res.string.skip_domain)) },
    summary = { Text(configuration.sniffer.skipDomain.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.skip_domain,
        configuration.sniffer.skipDomain,
        actions::updateSkipDomain,
      )
    },
  )
  preference(
    key = "skipSrcAddress",
    title = { Text(stringResource(Res.string.skip_src_address)) },
    summary = { Text(configuration.sniffer.skipSrcAddress.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.skip_src_address,
        configuration.sniffer.skipSrcAddress,
        actions::updateSkipSrcAddress,
      )
    },
  )
  preference(
    key = "skipDstAddress",
    title = { Text(stringResource(Res.string.skip_dst_address)) },
    summary = { Text(configuration.sniffer.skipDstAddress.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.skip_dst_address,
        configuration.sniffer.skipDstAddress,
        actions::updateSkipDstAddress,
      )
    },
  )
}

private fun LazyListScope.metaGeoFileItems(
  onImportGeoIp: () -> Unit,
  onImportGeoSite: () -> Unit,
  onImportCountry: () -> Unit,
  onImportASN: () -> Unit,
) {
  preferenceCategory(key = "cat_geox", title = { Text(stringResource(Res.string.geox_files)) })
  preference(
    key = "importGeoIp",
    title = { Text(stringResource(Res.string.import_geoip_file)) },
    summary = { Text(stringResource(Res.string.press_to_import)) },
    onClick = onImportGeoIp,
  )
  preference(
    key = "importGeoSite",
    title = { Text(stringResource(Res.string.import_geosite_file)) },
    summary = { Text(stringResource(Res.string.press_to_import)) },
    onClick = onImportGeoSite,
  )
  preference(
    key = "importCountry",
    title = { Text(stringResource(Res.string.import_country_file)) },
    summary = { Text(stringResource(Res.string.press_to_import)) },
    onClick = onImportCountry,
  )
  preference(
    key = "importASN",
    title = { Text(stringResource(Res.string.import_asn_file)) },
    summary = { Text(stringResource(Res.string.press_to_import)) },
    onClick = onImportASN,
  )
}

private val ConfigurationOverride.FindProcessMode?.textRes: StringResource
  get() =
    when (this) {
      Off -> Res.string.off
      Strict -> Res.string.strict
      Always -> Res.string.always
      null -> Res.string.dont_modify
    }

interface MetaFeatureSettingsActions {
  fun updateUnifiedDelay(value: Boolean?) = Unit

  fun updateGeodataMode(value: Boolean?) = Unit

  fun updateTcpConcurrent(value: Boolean?) = Unit

  fun updateFindProcessMode(value: ConfigurationOverride.FindProcessMode?) = Unit

  fun updateSnifferEnable(value: Boolean?) = Unit

  fun updateSniffHttpPorts(value: List<String>?) = Unit

  fun updateSniffHttpOverrideDestination(value: Boolean?) = Unit

  fun updateSniffTlsPorts(value: List<String>?) = Unit

  fun updateSniffTlsOverrideDestination(value: Boolean?) = Unit

  fun updateSniffQuicPorts(value: List<String>?) = Unit

  fun updateSniffQuicOverrideDestination(value: Boolean?) = Unit

  fun updateForceDnsMapping(value: Boolean?) = Unit

  fun updateParsePureIp(value: Boolean?) = Unit

  fun updateOverrideDestination(value: Boolean?) = Unit

  fun updateForceDomain(value: List<String>?) = Unit

  fun updateSkipDomain(value: List<String>?) = Unit

  fun updateSkipSrcAddress(value: List<String>?) = Unit

  fun updateSkipDstAddress(value: List<String>?) = Unit
}

@Composable
private fun AgeKeyHelperDialog(
  hybrid: Boolean,
  onDismiss: () -> Unit,
  onShowMessage: suspend (String) -> Unit,
) {
  var secretKey by remember { mutableStateOf("") }
  var publicKey by remember { mutableStateOf("") }
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  val copiedText = stringResource(CommonRes.string.copied)
  val genericError = stringResource(Res.string.error)
  val secretInvalid =
    remember(secretKey) {
      secretKey.isNotBlank() &&
        !runCatching { Clash.verifySecretKeys(secretKey) }.getOrDefault(false)
    }
  val publicInvalid =
    remember(publicKey) {
      publicKey.isNotBlank() &&
        !runCatching { Clash.verifyPublicKeys(publicKey) }.getOrDefault(false)
    }
  fun copy(label: String, value: String) {
    if (value.isBlank()) return
    scope.launch {
      clipboard.setClipEntry(ClipData.newPlainText(label, value).toClipEntry())
      onShowMessage(copiedText)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        stringResource(
          if (hybrid) Res.string.age_key_type_hybrid else Res.string.age_key_type_x25519,
        ),
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = secretKey,
          onValueChange = { secretKey = it },
          label = { Text(stringResource(Res.string.age_secret_key)) },
          singleLine = true,
          isError = secretInvalid,
          supportingText =
            if (secretInvalid) {
              { Text(text = stringResource(Res.string.age_secret_key_error)) }
            } else {
              null
            },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
          modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
          TextButton(
            onClick = {
              runCatching {
                if (hybrid) Clash.genHybridKeyPair() else Clash.genX25519KeyPair()
              }
                .onSuccess {
                  secretKey = it.secretKey
                  publicKey = it.publicKey
                }
                .onFailure { scope.launch { onShowMessage(genericError) } }
            },
          ) {
            Text(stringResource(Res.string.age_key_generate))
          }
          TextButton(onClick = { copy("age_secret_key", secretKey) }) {
            Text(stringResource(Res.string.age_key_copy))
          }
        }
        OutlinedTextField(
          value = publicKey,
          onValueChange = { publicKey = it },
          label = { Text(stringResource(Res.string.age_public_key)) },
          singleLine = true,
          isError = publicInvalid,
          supportingText =
            if (publicInvalid) {
              { Text(text = stringResource(Res.string.age_public_key_error)) }
            } else {
              null
            },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
          modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
          TextButton(
            onClick = {
              runCatching { Clash.toPublicKeys(secretKey).firstOrNull().orEmpty() }
                .onSuccess { publicKey = it }
                .onFailure { scope.launch { onShowMessage(genericError) } }
            },
          ) {
            Text(stringResource(Res.string.age_key_to_public))
          }
          TextButton(onClick = { copy("age_public_key", publicKey) }) {
            Text(stringResource(Res.string.age_key_copy))
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(CommonRes.string.ok)) }
    },
  )
}

@PreviewWrapper(TabbyThemeWrapper::class)
@PreviewTabby
@Composable
private fun MetaFeatureSettingsContentPreview() {
  MetaFeatureSettingsContent(
    configuration = ConfigurationOverride(),
    actions = object : MetaFeatureSettingsActions {},
    snackbarHostState = SnackbarHostState(),
    showResetConfirmDialog = false,
    onShowResetConfirmDialogChange = {},
    onResetConfirmed = {},
    onImportGeoIp = {},
    onImportGeoSite = {},
    onImportCountry = {},
    onImportASN = {},
    onOpenEditableTextList = { _, _, _ -> },
    onAgeKeyHelperRequested = {},
  )
}
