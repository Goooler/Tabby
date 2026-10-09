package com.github.kr328.clash.settings.ui

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common.cancel
import com.github.kr328.clash.common.default_
import com.github.kr328.clash.common.direct_mode
import com.github.kr328.clash.common.disabled
import com.github.kr328.clash.common.format_elements
import com.github.kr328.clash.common.global_mode
import com.github.kr328.clash.common.mode
import com.github.kr328.clash.common.ok
import com.github.kr328.clash.common.reset
import com.github.kr328.clash.common.rule
import com.github.kr328.clash.common.rule_mode
import com.github.kr328.clash.common.unknown
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.settings.Res
import com.github.kr328.clash.settings.allow_lan
import com.github.kr328.clash.settings.allow_origins
import com.github.kr328.clash.settings.allow_private_network
import com.github.kr328.clash.settings.append_system_dns
import com.github.kr328.clash.settings.authentication
import com.github.kr328.clash.settings.bind_address
import com.github.kr328.clash.settings.blacklist
import com.github.kr328.clash.settings.debug
import com.github.kr328.clash.settings.default_name_server
import com.github.kr328.clash.settings.dns
import com.github.kr328.clash.settings.domain_fallback
import com.github.kr328.clash.settings.dont_modify
import com.github.kr328.clash.settings.empty
import com.github.kr328.clash.settings.enabled
import com.github.kr328.clash.settings.enhanced_mode
import com.github.kr328.clash.settings.error
import com.github.kr328.clash.settings.external_controller
import com.github.kr328.clash.settings.external_controller_tls
import com.github.kr328.clash.settings.fakeip
import com.github.kr328.clash.settings.fakeip_filter
import com.github.kr328.clash.settings.fakeip_filter_mode
import com.github.kr328.clash.settings.fallback
import com.github.kr328.clash.settings.force_enable
import com.github.kr328.clash.settings.general
import com.github.kr328.clash.settings.geoip_fallback
import com.github.kr328.clash.settings.geoip_fallback_code
import com.github.kr328.clash.settings.hosts
import com.github.kr328.clash.settings.http_port
import com.github.kr328.clash.settings.info
import com.github.kr328.clash.settings.ipcidr_fallback
import com.github.kr328.clash.settings.ipv6
import com.github.kr328.clash.settings.listen
import com.github.kr328.clash.settings.log_level
import com.github.kr328.clash.settings.mapping
import com.github.kr328.clash.settings.mixed_port
import com.github.kr328.clash.settings.name_server
import com.github.kr328.clash.settings.name_server_policy
import com.github.kr328.clash.settings.override
import com.github.kr328.clash.settings.prefer_h3
import com.github.kr328.clash.settings.raw_cn
import com.github.kr328.clash.settings.redirect_port
import com.github.kr328.clash.settings.secret
import com.github.kr328.clash.settings.silent
import com.github.kr328.clash.settings.socks_port
import com.github.kr328.clash.settings.strategy
import com.github.kr328.clash.settings.tproxy_port
import com.github.kr328.clash.settings.use_built_in
import com.github.kr328.clash.settings.use_hosts
import com.github.kr328.clash.settings.vm.OverrideSettingsViewModel
import com.github.kr328.clash.settings.warning
import com.github.kr328.clash.settings.whitelist
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
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.listPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.preferenceCategory
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private sealed interface OverrideSettingsRoute : NavKey {
  @Serializable data object Main : OverrideSettingsRoute
}

@Composable
internal fun OverrideSettingsScreen(
  modifier: Modifier = Modifier,
  viewModel: OverrideSettingsViewModel = koinViewModel<OverrideSettingsViewModel>(),
  onResetCompleted: () -> Unit,
) {
  LifecycleStartEffect(viewModel) {
    onStopOrDispose {
      viewModel.persist()
    }
  }

  val backStack = rememberNavBackStackBuilder { add(OverrideSettingsRoute.Main) }
  val scope = rememberCoroutineScope()
  var currentEditableTextMapOnApply by remember {
    mutableStateOf<((Map<String, String>?) -> Unit)?>(null)
  }
  var currentEditableTextListOnApply by remember {
    mutableStateOf<((List<String>?) -> Unit)?>(null)
  }

  TabbyNavDisplay(
    backStack = backStack,
    entryProvider =
      entryProvider {
        entry<OverrideSettingsRoute.Main> {
          val configuration by viewModel.configuration.collectAsStateWithLifecycle()
          var showResetConfirmDialog by remember { mutableStateOf(false) }

          OverrideSettingsContent(
            configuration = configuration,
            actions = viewModel,
            modifier = modifier,
            showResetConfirmDialog = showResetConfirmDialog,
            onShowResetConfirmDialogChange = { showResetConfirmDialog = it },
            onResetConfirmed = {
              viewModel.resetOverride()
              onResetCompleted()
            },
            onOpenEditableTextMap = { title, initialValues, onApply ->
              currentEditableTextMapOnApply = onApply
              scope.launch {
                backStack.addIfNotLast(EditableTextMap(getString(title), initialValues))
              }
            },
            onOpenEditableTextList = { title, initialValues, onApply ->
              currentEditableTextListOnApply = onApply
              scope.launch {
                backStack.addIfNotLast(EditableTextList(getString(title), initialValues?.toSet()))
              }
            },
          )
        }
        editableTextMapScreenEntry(
          onDismiss = {
            currentEditableTextMapOnApply = null
            backStack.removeLastOrNull()
          },
          onApply = { newValues ->
            currentEditableTextMapOnApply?.invoke(newValues)
            currentEditableTextMapOnApply = null
            backStack.removeLastOrNull()
          },
        )
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
private fun OverrideSettingsContent(
  configuration: ConfigurationOverride,
  actions: OverrideSettingsActions,
  modifier: Modifier = Modifier,
  showResetConfirmDialog: Boolean,
  onShowResetConfirmDialogChange: (Boolean) -> Unit,
  onResetConfirmed: () -> Unit,
  onOpenEditableTextMap:
    (StringResource, Map<String, String>?, (Map<String, String>?) -> Unit) -> Unit,
  onOpenEditableTextList: (StringResource, List<String>?, (List<String>?) -> Unit) -> Unit,
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  TabbyScaffold(
    title = stringResource(Res.string.override),
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
        generalPreferenceItems(
          configuration,
          actions,
          onOpenEditableTextMap,
          onOpenEditableTextList,
        )
        dnsPreferenceItems(
          configuration = configuration,
          actions = actions,
          onOpenEditableTextMap = onOpenEditableTextMap,
          onOpenEditableTextList = onOpenEditableTextList,
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

private fun LazyListScope.generalPreferenceItems(
  configuration: ConfigurationOverride,
  actions: OverrideSettingsActions,
  onOpenEditableTextMap:
    (StringResource, Map<String, String>?, (Map<String, String>?) -> Unit) -> Unit,
  onOpenEditableTextList: (StringResource, List<String>?, (List<String>?) -> Unit) -> Unit,
) {
  preferenceCategory(key = "cat_general", title = { Text(stringResource(Res.string.general)) })
  overrideEditTextPreferenceItem(
    key = "httpPort",
    title = Res.string.http_port,
    placeholder = Res.string.dont_modify,
    emptyLabel = CommonRes.string.disabled,
    value = portText(configuration.httpPort),
    onValueChange = { actions.updateHttpPort(parsePort(it)) },
    numericOnly = true,
  )
  overrideEditTextPreferenceItem(
    key = "socksPort",
    title = Res.string.socks_port,
    placeholder = Res.string.dont_modify,
    emptyLabel = CommonRes.string.disabled,
    value = portText(configuration.socksPort),
    onValueChange = { actions.updateSocksPort(parsePort(it)) },
    numericOnly = true,
  )
  overrideEditTextPreferenceItem(
    key = "redirectPort",
    title = Res.string.redirect_port,
    placeholder = Res.string.dont_modify,
    emptyLabel = CommonRes.string.disabled,
    value = portText(configuration.redirectPort),
    onValueChange = { actions.updateRedirectPort(parsePort(it)) },
    numericOnly = true,
  )
  overrideEditTextPreferenceItem(
    key = "tproxyPort",
    title = Res.string.tproxy_port,
    placeholder = Res.string.dont_modify,
    emptyLabel = CommonRes.string.disabled,
    value = portText(configuration.tproxyPort),
    onValueChange = { actions.updateTproxyPort(parsePort(it)) },
    numericOnly = true,
  )
  overrideEditTextPreferenceItem(
    key = "mixedPort",
    title = Res.string.mixed_port,
    placeholder = Res.string.dont_modify,
    emptyLabel = CommonRes.string.disabled,
    value = portText(configuration.mixedPort),
    onValueChange = { actions.updateMixedPort(parsePort(it)) },
    numericOnly = true,
  )
  preference(
    key = "authentication",
    title = { Text(stringResource(Res.string.authentication)) },
    summary = { Text(configuration.authentication.listSummary(Res.string.dont_modify)) },
    onClick = {
      onOpenEditableTextList(
        Res.string.authentication,
        configuration.authentication,
        actions::updateAuthentication,
      )
    },
  )
  listPreference(
    key = "allowLan",
    value = configuration.allowLan,
    onValueChange = actions::updateAllowLan,
    values = booleanOptions,
    title = { Text(stringResource(Res.string.allow_lan)) },
    summary = { Text(stringResource(configuration.allowLan.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "ipv6",
    value = configuration.ipv6,
    onValueChange = actions::updateIpv6,
    values = booleanOptions,
    title = { Text(stringResource(Res.string.ipv6)) },
    summary = { Text(stringResource(configuration.ipv6.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  overrideEditTextPreferenceItem(
    key = "bindAddress",
    title = Res.string.bind_address,
    placeholder = Res.string.dont_modify,
    emptyLabel = CommonRes.string.default_,
    value = configuration.bindAddress,
    onValueChange = actions::updateBindAddress,
  )
  overrideEditTextPreferenceItem(
    key = "externalController",
    title = Res.string.external_controller,
    placeholder = Res.string.dont_modify,
    emptyLabel = CommonRes.string.default_,
    value = configuration.externalController,
    onValueChange = actions::updateExternalController,
  )
  overrideEditTextPreferenceItem(
    key = "externalControllerTls",
    title = Res.string.external_controller_tls,
    placeholder = Res.string.dont_modify,
    emptyLabel = CommonRes.string.default_,
    value = configuration.externalControllerTLS,
    onValueChange = actions::updateExternalControllerTls,
  )
  preference(
    key = "allowOrigins",
    title = { Text(stringResource(Res.string.allow_origins)) },
    summary = {
      Text(configuration.externalControllerCors.allowOrigins.listSummary(Res.string.dont_modify))
    },
    onClick = {
      onOpenEditableTextList(
        Res.string.allow_origins,
        configuration.externalControllerCors.allowOrigins,
        actions::updateAllowOrigins,
      )
    },
  )
  listPreference(
    key = "allowPrivateNetwork",
    value = configuration.externalControllerCors.allowPrivateNetwork,
    onValueChange = actions::updateAllowPrivateNetwork,
    values = booleanOptions,
    title = { Text(stringResource(Res.string.allow_private_network)) },
    summary = {
      Text(stringResource(configuration.externalControllerCors.allowPrivateNetwork.textRes))
    },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  overrideEditTextPreferenceItem(
    key = "secret",
    title = Res.string.secret,
    placeholder = Res.string.dont_modify,
    emptyLabel = CommonRes.string.default_,
    value = configuration.secret,
    onValueChange = actions::updateSecret,
  )
  listPreference(
    key = "mode",
    value = configuration.mode,
    onValueChange = actions::updateMode,
    values = TunnelState.Mode.entries,
    title = { Text(stringResource(CommonRes.string.mode)) },
    summary = { Text(stringResource(configuration.mode.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "logLevel",
    value = configuration.logLevel,
    onValueChange = actions::updateLogLevel,
    values = LogMessage.Level.entries,
    title = { Text(stringResource(Res.string.log_level)) },
    summary = { Text(stringResource(configuration.logLevel.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  preference(
    key = "hosts",
    title = { Text(stringResource(Res.string.hosts)) },
    summary = { Text(configuration.hosts.summary(Res.string.dont_modify)) },
    onClick = {
      onOpenEditableTextMap(Res.string.hosts, configuration.hosts, actions::updateHosts)
    },
  )
}

private fun LazyListScope.dnsPreferenceItems(
  configuration: ConfigurationOverride,
  actions: OverrideSettingsActions,
  onOpenEditableTextMap:
    (StringResource, Map<String, String>?, (Map<String, String>?) -> Unit) -> Unit,
  onOpenEditableTextList: (StringResource, List<String>?, (List<String>?) -> Unit) -> Unit,
) {
  val enabled = configuration.dns.enable != false
  preferenceCategory(key = "cat_dns", title = { Text(stringResource(Res.string.dns)) })
  listPreference(
    key = "dnsStrategy",
    value = enabled,
    onValueChange = actions::updateDnsEnable,
    values = booleanOptions,
    title = { Text(stringResource(Res.string.strategy)) },
    summary = { Text(stringResource(configuration.dns.enable.dnsStrategyTextRes)) },
    valueToText = { AnnotatedString(stringResource(configuration.dns.enable.dnsStrategyTextRes)) },
  )
  listPreference(
    key = "dnsPreferH3",
    value = configuration.dns.preferH3,
    onValueChange = actions::updateDnsPreferH3,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.prefer_h3)) },
    summary = { Text(stringResource(configuration.dns.preferH3.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  overrideEditTextPreferenceItem(
    key = "dnsListen",
    title = Res.string.listen,
    placeholder = Res.string.dont_modify,
    emptyLabel = CommonRes.string.disabled,
    value = configuration.dns.listen,
    onValueChange = actions::updateDnsListen,
    enabled = enabled,
  )
  listPreference(
    key = "appendSystemDns",
    value = configuration.app.appendSystemDns,
    onValueChange = actions::updateAppendSystemDns,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.append_system_dns)) },
    summary = { Text(stringResource(configuration.app.appendSystemDns.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "dnsIpv6",
    value = configuration.dns.ipv6,
    onValueChange = actions::updateDnsIpv6,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.ipv6)) },
    summary = { Text(stringResource(configuration.dns.ipv6.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "dnsUseHosts",
    value = configuration.dns.useHosts,
    onValueChange = actions::updateDnsUseHosts,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.use_hosts)) },
    summary = { Text(stringResource(configuration.dns.useHosts.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "dnsEnhancedMode",
    value = configuration.dns.enhancedMode,
    onValueChange = actions::updateDnsEnhancedMode,
    values = ConfigurationOverride.DnsEnhancedMode.entries,
    enabled = enabled,
    title = { Text(stringResource(Res.string.enhanced_mode)) },
    summary = { Text(stringResource(configuration.dns.enhancedMode.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  preference(
    key = "dnsNameServer",
    title = { Text(stringResource(Res.string.name_server)) },
    summary = { Text(configuration.dns.nameServer.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.name_server,
        configuration.dns.nameServer,
        actions::updateDnsNameServer,
      )
    },
  )
  preference(
    key = "dnsFallback",
    title = { Text(stringResource(Res.string.fallback)) },
    summary = { Text(configuration.dns.fallback.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.fallback,
        configuration.dns.fallback,
        actions::updateDnsFallback,
      )
    },
  )
  preference(
    key = "dnsDefaultServer",
    title = { Text(stringResource(Res.string.default_name_server)) },
    summary = { Text(configuration.dns.defaultServer.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.default_name_server,
        configuration.dns.defaultServer,
        actions::updateDnsDefaultServer,
      )
    },
  )
  preference(
    key = "dnsFakeIpFilter",
    title = { Text(stringResource(Res.string.fakeip_filter)) },
    summary = { Text(configuration.dns.fakeIpFilter.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.fakeip_filter,
        configuration.dns.fakeIpFilter,
        actions::updateDnsFakeIpFilter,
      )
    },
  )
  listPreference(
    key = "dnsFakeIpFilterMode",
    value = configuration.dns.fakeIPFilterMode,
    onValueChange = actions::updateDnsFakeIpFilterMode,
    values = ConfigurationOverride.FilterMode.entries,
    enabled = enabled,
    title = { Text(stringResource(Res.string.fakeip_filter_mode)) },
    summary = { Text(stringResource(configuration.dns.fakeIPFilterMode.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  listPreference(
    key = "dnsGeoIpFallback",
    value = configuration.dns.fallbackFilter.geoIp,
    onValueChange = actions::updateDnsGeoIpFallback,
    values = booleanOptions,
    enabled = enabled,
    title = { Text(stringResource(Res.string.geoip_fallback)) },
    summary = { Text(stringResource(configuration.dns.fallbackFilter.geoIp.textRes)) },
    valueToText = { AnnotatedString(stringResource(it.textRes)) },
  )
  overrideEditTextPreferenceItem(
    key = "dnsGeoIpCode",
    title = Res.string.geoip_fallback_code,
    placeholder = Res.string.dont_modify,
    emptyLabel = Res.string.raw_cn,
    value = configuration.dns.fallbackFilter.geoIpCode,
    onValueChange = actions::updateDnsGeoIpCode,
    enabled = enabled,
  )
  preference(
    key = "dnsDomainFallback",
    title = { Text(stringResource(Res.string.domain_fallback)) },
    summary = { Text(configuration.dns.fallbackFilter.domain.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.domain_fallback,
        configuration.dns.fallbackFilter.domain,
        actions::updateDnsDomainFallback,
      )
    },
  )
  preference(
    key = "dnsIpcidrFallback",
    title = { Text(stringResource(Res.string.ipcidr_fallback)) },
    summary = { Text(configuration.dns.fallbackFilter.ipcidr.listSummary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextList(
        Res.string.ipcidr_fallback,
        configuration.dns.fallbackFilter.ipcidr,
        actions::updateDnsIpcidrFallback,
      )
    },
  )
  preference(
    key = "dnsNameserverPolicy",
    title = { Text(stringResource(Res.string.name_server_policy)) },
    summary = { Text(configuration.dns.nameserverPolicy.summary(Res.string.dont_modify)) },
    enabled = enabled,
    onClick = {
      onOpenEditableTextMap(
        Res.string.name_server_policy,
        configuration.dns.nameserverPolicy,
        actions::updateDnsNameserverPolicy,
      )
    },
  )
}

private fun LazyListScope.overrideEditTextPreferenceItem(
  key: String,
  title: StringResource,
  placeholder: StringResource,
  emptyLabel: StringResource,
  value: String?,
  onValueChange: (String?) -> Unit,
  enabled: Boolean = true,
  numericOnly: Boolean = false,
) {
  item(key = key, contentType = "EditTextPreference") {
    var showDialog by remember { mutableStateOf(false) }
    val summary =
      when {
        value == null -> stringResource(placeholder)
        value.isEmpty() -> stringResource(emptyLabel)
        else -> value
      }
    Preference(
      title = { Text(stringResource(title)) },
      summary = { Text(summary) },
      enabled = enabled,
      onClick = { showDialog = true },
    )
    if (showDialog) {
      var inputText by
        remember(value) {
          mutableStateOf(
            TextFieldValue(text = value.orEmpty(), selection = TextRange(value.orEmpty().length)),
          )
        }
      val focusRequester = remember { FocusRequester() }
      val keyboardController = LocalSoftwareKeyboardController.current
      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
      }
      AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text(stringResource(title)) },
        text = {
          OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = if (numericOnly) it.filterDigits() else it },
            keyboardOptions =
              if (numericOnly) {
                KeyboardOptions(keyboardType = KeyboardType.Number)
              } else {
                KeyboardOptions.Default
              },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
          )
        },
        confirmButton = {
          TextButton(
            onClick = {
              onValueChange(
                if (numericOnly) {
                  portText(parsePort(inputText.text))
                } else {
                  inputText.text
                },
              )
              showDialog = false
            },
          ) {
            Text(stringResource(CommonRes.string.ok))
          }
        },
        dismissButton = {
          Row {
            TextButton(
              onClick = {
                onValueChange(null)
                showDialog = false
              },
            ) {
              Text(stringResource(CommonRes.string.reset))
            }
            TextButton(onClick = { showDialog = false }) {
              Text(stringResource(CommonRes.string.cancel))
            }
          }
        },
      )
    }
  }
}

private fun TextFieldValue.filterDigits(): TextFieldValue {
  val filtered = text.filter(Char::isDigit)
  if (filtered == text) return this
  val start = text.take(selection.start).count(Char::isDigit)
  val end = text.take(selection.end).count(Char::isDigit)
  return copy(text = filtered, selection = TextRange(start, end))
}

@Composable
private fun Map<String, String>?.summary(placeholder: StringResource) =
  when {
    this == null -> stringResource(placeholder)
    isEmpty() -> stringResource(Res.string.empty)
    else -> stringResource(CommonRes.string.format_elements, size)
  }

internal val Boolean?.textRes: StringResource
  get() =
    when (this) {
      true -> Res.string.enabled
      false -> CommonRes.string.disabled
      null -> Res.string.dont_modify
    }

private val Boolean?.dnsStrategyTextRes: StringResource
  get() =
    when (this) {
      true -> Res.string.force_enable
      false -> Res.string.use_built_in
      null -> Res.string.dont_modify
    }

private val TunnelState.Mode?.textRes: StringResource
  get() =
    when (this) {
      Direct -> CommonRes.string.direct_mode
      Global -> CommonRes.string.global_mode
      Rule -> CommonRes.string.rule_mode
      null -> Res.string.dont_modify
    }

private val LogMessage.Level?.textRes: StringResource
  get() =
    when (this) {
      Info -> Res.string.info
      Warning -> Res.string.warning
      LogMessage.Level.Error -> Res.string.error
      Debug -> Res.string.debug
      Silent -> Res.string.silent
      Unknown -> CommonRes.string.unknown
      null -> Res.string.dont_modify
    }

private val ConfigurationOverride.DnsEnhancedMode?.textRes: StringResource
  get() =
    when (this) {
      None -> CommonRes.string.disabled
      FakeIp -> Res.string.fakeip
      Mapping -> Res.string.mapping
      null -> Res.string.dont_modify
    }

private val ConfigurationOverride.FilterMode?.textRes: StringResource
  get() =
    when (this) {
      BlackList -> Res.string.blacklist
      WhiteList -> Res.string.whitelist
      Rule -> CommonRes.string.rule
      null -> Res.string.dont_modify
    }

internal val booleanOptions: List<Boolean?> = listOf(null, true, false)

private fun portText(port: Int?): String? =
  when {
    port == null -> null
    port <= 0 -> ""
    else -> port.toString()
  }

private fun parsePort(text: String?): Int? =
  when {
    text == null -> null
    else -> text.toIntOrNull() ?: 0
  }

interface OverrideSettingsActions {
  fun updateHttpPort(value: Int?) = Unit

  fun updateSocksPort(value: Int?) = Unit

  fun updateRedirectPort(value: Int?) = Unit

  fun updateTproxyPort(value: Int?) = Unit

  fun updateMixedPort(value: Int?) = Unit

  fun updateAuthentication(value: List<String>?) = Unit

  fun updateAllowLan(value: Boolean?) = Unit

  fun updateIpv6(value: Boolean?) = Unit

  fun updateBindAddress(value: String?) = Unit

  fun updateExternalController(value: String?) = Unit

  fun updateExternalControllerTls(value: String?) = Unit

  fun updateAllowOrigins(value: List<String>?) = Unit

  fun updateAllowPrivateNetwork(value: Boolean?) = Unit

  fun updateSecret(value: String?) = Unit

  fun updateMode(value: TunnelState.Mode?) = Unit

  fun updateLogLevel(value: LogMessage.Level?) = Unit

  fun updateHosts(value: Map<String, String>?) = Unit

  fun updateDnsEnable(value: Boolean?) = Unit

  fun updateDnsPreferH3(value: Boolean?) = Unit

  fun updateDnsListen(value: String?) = Unit

  fun updateAppendSystemDns(value: Boolean?) = Unit

  fun updateDnsIpv6(value: Boolean?) = Unit

  fun updateDnsUseHosts(value: Boolean?) = Unit

  fun updateDnsEnhancedMode(value: ConfigurationOverride.DnsEnhancedMode?) = Unit

  fun updateDnsNameServer(value: List<String>?) = Unit

  fun updateDnsFallback(value: List<String>?) = Unit

  fun updateDnsDefaultServer(value: List<String>?) = Unit

  fun updateDnsFakeIpFilter(value: List<String>?) = Unit

  fun updateDnsFakeIpFilterMode(value: ConfigurationOverride.FilterMode?) = Unit

  fun updateDnsGeoIpFallback(value: Boolean?) = Unit

  fun updateDnsGeoIpCode(value: String?) = Unit

  fun updateDnsDomainFallback(value: List<String>?) = Unit

  fun updateDnsIpcidrFallback(value: List<String>?) = Unit

  fun updateDnsNameserverPolicy(value: Map<String, String>?) = Unit
}

@PreviewWrapper(TabbyThemeWrapper::class)
@PreviewTabby
@Composable
private fun OverrideSettingsContentPreview() {
  OverrideSettingsContent(
    configuration = ConfigurationOverride(),
    actions = object : OverrideSettingsActions {},
    showResetConfirmDialog = false,
    onShowResetConfirmDialogChange = {},
    onResetConfirmed = {},
    onOpenEditableTextMap = { _, _, _ -> },
    onOpenEditableTextList = { _, _, _ -> },
  )
}
