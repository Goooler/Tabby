package com.github.kr328.clash.proxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common.default_
import com.github.kr328.clash.common.direct_mode
import com.github.kr328.clash.common.dont_modify
import com.github.kr328.clash.common.filter
import com.github.kr328.clash.common.global_mode
import com.github.kr328.clash.common.mode
import com.github.kr328.clash.common.more
import com.github.kr328.clash.common.name
import com.github.kr328.clash.common.proxy
import com.github.kr328.clash.common.rule_mode
import com.github.kr328.clash.common.sort
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.proxy.Res
import com.github.kr328.clash.proxy.delay
import com.github.kr328.clash.proxy.delay_test
import com.github.kr328.clash.proxy.doubles
import com.github.kr328.clash.proxy.layout
import com.github.kr328.clash.proxy.mode_switch_tips
import com.github.kr328.clash.proxy.multiple
import com.github.kr328.clash.proxy.not_selectable
import com.github.kr328.clash.proxy.proxy_empty_tips
import com.github.kr328.clash.proxy.proxy_scroll_to_top
import com.github.kr328.clash.proxy.scroll_selected_to_top
import com.github.kr328.clash.proxy.single
import com.github.kr328.clash.proxy.vm.ProxyViewModel
import com.github.kr328.clash.proxy.vm.ProxyViewModel.SelectedProxy
import com.github.kr328.clash.ui.component.Spacer
import com.github.kr328.clash.ui.component.TabbyScaffold
import com.github.kr328.clash.ui.component.WeightSpacer
import com.github.kr328.clash.ui.icon.BaselineArrowUp
import com.github.kr328.clash.ui.icon.BaselineCircleCenter
import com.github.kr328.clash.ui.icon.BaselineFlashOn
import com.github.kr328.clash.ui.icon.BaselineMoreVert
import com.github.kr328.clash.ui.icon.TabbyIcons
import com.github.kr328.clash.ui.theme.PreviewTabby
import com.github.kr328.clash.ui.theme.TabbyThemeWrapper
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ProxyScreen(
  modifier: Modifier = Modifier,
  viewModel: ProxyViewModel = koinViewModel<ProxyViewModel>(),
  onReLaunch: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val selectedProxies by viewModel.selectedProxies.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val modeSwitchTips = stringResource(Res.string.mode_switch_tips)

  LaunchedEffect(viewModel) {
    viewModel.eventState.collect { event ->
      when (event) {
        ReLaunch -> {
          onReLaunch()
        }
        ShowModeSwitchTips -> {
          snackbarHostState.showSnackbar(message = modeSwitchTips)
        }
      }
    }
  }

  LifecycleStartEffect(viewModel) {
    viewModel.refresh()
    onStopOrDispose {}
  }

  ProxyContent(
    modifier = modifier,
    snackbarHostState = snackbarHostState,
    uiState = uiState,
    selectedProxies = selectedProxies,
    onPageChanged = viewModel::onPageChanged,
    onUrlTest = viewModel::onUrlTest,
    onExcludeNotSelectableChanged = viewModel::onExcludeNotSelectableChanged,
    onProxyLineChanged = viewModel::onProxyLineChanged,
    onProxySortChanged = viewModel::onProxySortChanged,
    onOverrideModeSelected = viewModel::onOverrideModeSelected,
    onProxySelected = viewModel::onProxySelected,
    onProxyDelayTest = viewModel::onProxyDelayTest,
  )
}

@Composable
private fun ProxyContent(
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState,
  uiState: ProxyViewModel.UiState,
  selectedProxies: List<SelectedProxy>,
  onPageChanged: (Int) -> Unit,
  onUrlTest: (Int) -> Unit,
  onExcludeNotSelectableChanged: (Boolean) -> Unit,
  onProxyLineChanged: (Int) -> Unit,
  onProxySortChanged: (ProxySort) -> Unit,
  onOverrideModeSelected: (TunnelState.Mode?) -> Unit,
  onProxySelected: (Int, String) -> Unit,
  onProxyDelayTest: (Int, String) -> Unit,
) {
  var menuVisible by remember { mutableStateOf(false) }
  var scrollSelectedToTopRequestVersion by remember { mutableIntStateOf(0) }
  var scrollSelectedToTopRequestPage by remember { mutableIntStateOf(0) }
  val currentGroup = uiState.groups.getOrNull(uiState.currentPage)
  val hasGroups = uiState.groupNames.isNotEmpty()
  val groupNames = uiState.groupNames
  val pagerState =
    if (groupNames.isNotEmpty()) {
      val initialPage = uiState.initialPage.coerceIn(groupNames.indices)
      rememberPagerState(initialPage = initialPage, pageCount = { groupNames.size })
    } else {
      null
    }
  val gridStates = groupNames.map { rememberLazyGridState() }
  val scope = rememberCoroutineScope()

  pagerState?.let { validPagerState ->
    LaunchedEffect(validPagerState) {
      snapshotFlow { validPagerState.currentPage }.collect(onPageChanged)
    }

    val currentPage = uiState.currentPage.coerceIn(groupNames.indices)
    LaunchedEffect(currentPage) {
      if (currentPage != validPagerState.currentPage) validPagerState.scrollToPage(currentPage)
    }
  }

  val firstRowSize = columnsForProxyLine(uiState.proxyLine)
  val showScrollToTopFab by
    remember(pagerState, firstRowSize, groupNames.size) {
      derivedStateOf {
        val validPagerState = pagerState ?: return@derivedStateOf false
        val currentGridState =
          gridStates.getOrNull(validPagerState.currentPage) ?: return@derivedStateOf false

        !currentGridState.isScrollInProgress &&
          currentGridState.firstVisibleItemIndex >= firstRowSize
      }
    }

  if (menuVisible) {
    ModalBottomSheet(
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      onDismissRequest = { menuVisible = false },
    ) {
      ProxyMenuSheetContent(
        overrideMode = uiState.overrideMode,
        excludeNotSelectable = uiState.excludeNotSelectable,
        proxyLine = uiState.proxyLine,
        proxySort = uiState.proxySort,
        onExcludeNotSelectableChanged = {
          menuVisible = false
          onExcludeNotSelectableChanged(it)
        },
        onProxyLineChanged = {
          menuVisible = false
          onProxyLineChanged(it)
        },
        onProxySortChanged = {
          menuVisible = false
          onProxySortChanged(it)
        },
        onOverrideModeSelected = {
          menuVisible = false
          onOverrideModeSelected(it)
        },
      )
    }
  }

  TabbyScaffold(
    modifier = modifier,
    snackbarHostState = snackbarHostState,
    title = stringResource(CommonRes.string.proxy),
    actions = {
      if (hasGroups) {
        if (currentGroup?.urlTesting == true) {
          CircularProgressIndicator(
            modifier = Modifier.padding(horizontal = 12.dp).size(24.dp),
            strokeWidth = 2.dp,
          )
        } else {
          IconButton(onClick = { onUrlTest(uiState.currentPage) }) {
            Icon(
              imageVector = TabbyIcons.BaselineFlashOn,
              contentDescription = stringResource(Res.string.delay_test),
            )
          }
        }

        IconButton(
          onClick = {
            scrollSelectedToTopRequestPage = pagerState?.currentPage ?: uiState.currentPage
            scrollSelectedToTopRequestVersion += 1
          },
        ) {
          Icon(
            imageVector = TabbyIcons.BaselineCircleCenter,
            contentDescription = stringResource(Res.string.scroll_selected_to_top),
          )
        }
      }

      IconButton(onClick = { menuVisible = true }) {
        Icon(
          imageVector = TabbyIcons.BaselineMoreVert,
          contentDescription = stringResource(CommonRes.string.more),
        )
      }
    },
    floatingActionButton = {
      if (showScrollToTopFab) {
        FloatingActionButton(
          onClick = {
            val validPagerState = pagerState ?: return@FloatingActionButton
            val currentGridState =
              gridStates.getOrNull(validPagerState.currentPage) ?: return@FloatingActionButton
            scope.launch { currentGridState.animateScrollToItem(0) }
          },
        ) {
          Icon(
            imageVector = TabbyIcons.BaselineArrowUp,
            contentDescription = stringResource(Res.string.proxy_scroll_to_top),
          )
        }
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (uiState.groupNames.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = stringResource(Res.string.proxy_empty_tips),
            style = MaterialTheme.typography.titleMedium,
          )
        }
      } else {
        ProxyPagerContent(
          uiState = uiState,
          selectedProxies = selectedProxies,
          scrollSelectedToTopRequestVersion = scrollSelectedToTopRequestVersion,
          scrollSelectedToTopRequestPage = scrollSelectedToTopRequestPage,
          pagerState = pagerState ?: return@Box,
          gridStates = gridStates,
          onProxySelected = onProxySelected,
          onProxyDelayTest = onProxyDelayTest,
        )
      }
    }
  }
}

@Composable
private fun ProxyPagerContent(
  uiState: ProxyViewModel.UiState,
  selectedProxies: List<SelectedProxy>,
  scrollSelectedToTopRequestVersion: Int,
  scrollSelectedToTopRequestPage: Int,
  pagerState: PagerState,
  gridStates: List<LazyGridState>,
  onProxySelected: (Int, String) -> Unit,
  onProxyDelayTest: (Int, String) -> Unit,
) {
  val groupNames = uiState.groupNames
  if (groupNames.isEmpty()) return

  val scope = rememberCoroutineScope()

  Column(modifier = Modifier.fillMaxSize()) {
    PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 0.dp) {
      groupNames.forEachIndexed { index, name ->
        Tab(
          selected = pagerState.currentPage == index,
          onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
          text = { Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
      }
    }

    HorizontalDivider()

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
      ProxyGroupPage(
        index = page,
        proxyLine = uiState.proxyLine,
        group = uiState.groups.getOrNull(page) ?: ProxyViewModel.UiState.ProxyGroupUiState(),
        selectedProxyName = selectedProxies.getOrNull(page)?.name,
        isCurrentPage = page == pagerState.currentPage,
        scrollSelectedToTopRequestVersion = scrollSelectedToTopRequestVersion,
        scrollSelectedToTopRequestPage = scrollSelectedToTopRequestPage,
        gridState = gridStates[page],
        selectedProxies = selectedProxies,
        onProxySelected = onProxySelected,
        onProxyDelayTest = onProxyDelayTest,
      )
    }
  }
}

@Composable
private fun ProxyGroupPage(
  index: Int,
  proxyLine: Int,
  group: ProxyViewModel.UiState.ProxyGroupUiState,
  selectedProxyName: String?,
  isCurrentPage: Boolean,
  scrollSelectedToTopRequestVersion: Int,
  scrollSelectedToTopRequestPage: Int,
  gridState: LazyGridState,
  selectedProxies: List<SelectedProxy>,
  onProxySelected: (Int, String) -> Unit,
  onProxyDelayTest: (Int, String) -> Unit,
) {
  val sources = group.sources
  val refreshVersion = group.refreshVersion
  val selectedControl = MaterialTheme.colorScheme.onPrimary
  val selectedBackground = MaterialTheme.colorScheme.primary
  val unselectedControl = MaterialTheme.colorScheme.onSurface
  val unselectedBackground = MaterialTheme.colorScheme.surface

  LaunchedEffect(scrollSelectedToTopRequestVersion) {
    if (
      scrollSelectedToTopRequestVersion == 0 ||
        index != scrollSelectedToTopRequestPage ||
        !isCurrentPage
    ) {
      return@LaunchedEffect
    }

    val selectedIndex = sources.indexOfFirst { it.proxy.name == selectedProxyName }
    if (selectedIndex < 0) return@LaunchedEffect

    gridState.animateScrollToItem(index = selectedIndex)
  }

  LazyVerticalGrid(
    state = gridState,
    columns = GridCells.Fixed(columnsForProxyLine(proxyLine)),
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(all = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items(count = sources.size, key = { itemIndex -> sources[itemIndex].proxy.name }) { itemIndex ->
      val source = sources[itemIndex]
      val parentNow = selectedProxies.getOrNull(index)
      val linkNow = source.linkIndex.takeIf { it >= 0 }?.let { selectedProxies.getOrNull(it) }
      val item =
        remember(source, refreshVersion, proxyLine, parentNow, linkNow) {
          source.toUiState(
            parentNow = parentNow ?: SelectedProxy("?"),
            linkNow = linkNow,
            proxyLine = proxyLine,
            selectedControl = selectedControl,
            selectedBackground = selectedBackground,
            unselectedControl = unselectedControl,
            unselectedBackground = unselectedBackground,
            delayTesting = source.proxy.name in group.delayTestingKeys,
          )
        }

      ProxyItemCard(
        item = item,
        proxyLine = proxyLine,
        selectable = group.selectable,
        onClick = { onProxySelected(index, item.key) },
        onDelayClick = { onProxyDelayTest(index, item.key) },
      )
    }
  }
}

@Composable
private fun ProxyItemCard(
  item: ProxyViewModel.UiState.ProxyItemUiState,
  proxyLine: Int,
  selectable: Boolean,
  onClick: () -> Unit,
  onDelayClick: () -> Unit,
) {
  val shape = RoundedCornerShape(if (proxyLine == 1) 0.dp else 5.dp)
  val modifier =
    Modifier.fillMaxWidth()
      .then(if (proxyLine == 1) Modifier else Modifier.shadow(elevation = 2.dp, shape = shape))
      .clip(shape)
      .background(item.background)
      .clickable(enabled = selectable, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp)

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      modifier = if (item.selected) Modifier.basicMarquee() else Modifier,
      text = item.title,
      color = item.controls,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      overflow = if (item.selected) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = item.subtitle,
        color = item.controls,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = if (item.selected) TextOverflow.Clip else TextOverflow.Ellipsis,
      )
      WeightSpacer(1f)
      Text(
        modifier =
          Modifier.clip(CircleShape)
            .clickable(onClick = onDelayClick)
            .background(item.controls.copy(alpha = if (item.delayTesting) 0.33f else 0.14f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        text = item.delayText,
        color = item.controls,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
      )
    }
  }
}

@Composable
private fun ColumnScope.ProxyMenuSheetContent(
  overrideMode: TunnelState.Mode?,
  excludeNotSelectable: Boolean,
  proxyLine: Int,
  proxySort: ProxySort,
  onExcludeNotSelectableChanged: (Boolean) -> Unit,
  onProxyLineChanged: (Int) -> Unit,
  onProxySortChanged: (ProxySort) -> Unit,
  onOverrideModeSelected: (TunnelState.Mode?) -> Unit,
) {
  ProxyMenuSection(title = stringResource(CommonRes.string.filter)) {
    ProxyMenuCheckboxRow(
      title = stringResource(Res.string.not_selectable),
      checked = excludeNotSelectable,
      onClick = { onExcludeNotSelectableChanged(!excludeNotSelectable) },
    )
  }

  ProxyMenuSection(title = stringResource(CommonRes.string.mode)) {
    ProxyMenuRadioRow(
      title = stringResource(CommonRes.string.dont_modify),
      selected = overrideMode == null,
      onClick = { onOverrideModeSelected(null) },
    )
    ProxyMenuRadioRow(
      title = stringResource(CommonRes.string.direct_mode),
      selected = overrideMode == TunnelState.Mode.Direct,
      onClick = { onOverrideModeSelected(TunnelState.Mode.Direct) },
    )
    ProxyMenuRadioRow(
      title = stringResource(CommonRes.string.global_mode),
      selected = overrideMode == TunnelState.Mode.Global,
      onClick = { onOverrideModeSelected(TunnelState.Mode.Global) },
    )
    ProxyMenuRadioRow(
      title = stringResource(CommonRes.string.rule_mode),
      selected = overrideMode == TunnelState.Mode.Rule,
      onClick = { onOverrideModeSelected(TunnelState.Mode.Rule) },
    )
  }

  ProxyMenuSection(title = stringResource(Res.string.layout)) {
    ProxyMenuRadioRow(
      title = stringResource(Res.string.single),
      selected = proxyLine == 1,
      onClick = { onProxyLineChanged(1) },
    )
    ProxyMenuRadioRow(
      title = stringResource(Res.string.doubles),
      selected = proxyLine == 2,
      onClick = { onProxyLineChanged(2) },
    )
    ProxyMenuRadioRow(
      title = stringResource(Res.string.multiple),
      selected = proxyLine == 3,
      onClick = { onProxyLineChanged(3) },
    )
  }

  ProxyMenuSection(title = stringResource(CommonRes.string.sort)) {
    ProxyMenuRadioRow(
      title = stringResource(CommonRes.string.default_),
      selected = proxySort == ProxySort.Default,
      onClick = { onProxySortChanged(ProxySort.Default) },
    )
    ProxyMenuRadioRow(
      title = stringResource(CommonRes.string.name),
      selected = proxySort == ProxySort.Title,
      onClick = { onProxySortChanged(ProxySort.Title) },
    )
    ProxyMenuRadioRow(
      title = stringResource(Res.string.delay),
      selected = proxySort == ProxySort.Delay,
      onClick = { onProxySortChanged(ProxySort.Delay) },
    )
  }

  Spacer(24.dp)
}

@Composable
private fun ProxyMenuSection(title: String, content: @Composable () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
    )
    content()
    Spacer(8.dp)
  }
}

@Composable
private fun ProxyMenuCheckboxRow(title: String, checked: Boolean, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(value = checked, onValueChange = { onClick() }, role = Role.Checkbox)
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Checkbox(checked = checked, onCheckedChange = null)
    Spacer(12.dp)
    Text(text = title, style = MaterialTheme.typography.bodyLarge)
  }
}

@Composable
private fun ProxyMenuRadioRow(title: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Spacer(12.dp)
    Text(text = title, style = MaterialTheme.typography.bodyLarge)
  }
}

private fun columnsForProxyLine(proxyLine: Int): Int =
  when (proxyLine) {
    1 -> 1
    2 -> 2
    else -> 3
  }

@PreviewWrapper(TabbyThemeWrapper::class)
@PreviewTabby
@Composable
private fun ProxyMenuSheetContentPreview() {
  Column {
    ProxyMenuSheetContent(
      overrideMode = TunnelState.Mode.Rule,
      excludeNotSelectable = true,
      proxyLine = 2,
      proxySort = ProxySort.Delay,
      onExcludeNotSelectableChanged = {},
      onProxyLineChanged = {},
      onProxySortChanged = {},
      onOverrideModeSelected = {},
    )
  }
}

@PreviewWrapper(TabbyThemeWrapper::class)
@PreviewTabby
@Composable
private fun ProxyContentPreview() {
  val groups = remember {
    listOf(
      ProxyViewModel.UiState.ProxyGroupUiState(
        selectable = true,
        sources =
          listOf(
            ProxyViewModel.UiState.ProxyItemSource(
              proxy =
                Proxy(
                  name = "auto",
                  title = "Auto",
                  subtitle = "",
                  type = Proxy.Type.URLTest,
                  delay = 48,
                  isGroup = true,
                ),
              linkIndex = 1,
            ),
            ProxyViewModel.UiState.ProxyItemSource(
              proxy =
                Proxy(
                  name = "hk-01",
                  title = "Hong Kong 01",
                  subtitle = "BGP | 1.2x",
                  type = Proxy.Type("Shadowsocks"),
                  delay = 62,
                  isGroup = false,
                ),
              linkIndex = -1,
            ),
          ),
      ),
    )
  }

  ProxyContent(
    snackbarHostState = SnackbarHostState(),
    selectedProxies = listOf(SelectedProxy("auto"), SelectedProxy("hk-01")),
    uiState =
      ProxyViewModel.UiState(
        groupNames = listOf("Auto"),
        groups = groups,
        currentPage = 0,
        proxyLine = 2,
        excludeNotSelectable = false,
        proxySort = ProxySort.Delay,
        overrideMode = TunnelState.Mode.Rule,
        initialPage = 0,
      ),
    onPageChanged = {},
    onUrlTest = {},
    onExcludeNotSelectableChanged = {},
    onProxyLineChanged = {},
    onProxySortChanged = {},
    onOverrideModeSelected = {},
    onProxySelected = { _, _ -> },
    onProxyDelayTest = { _, _ -> },
  )
}
