package com.github.kr328.clash.profile.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.Res as CommonRes
import com.github.kr328.clash.common._new
import com.github.kr328.clash.common.cancel
import com.github.kr328.clash.common.delete
import com.github.kr328.clash.common.export
import com.github.kr328.clash.common.more
import com.github.kr328.clash.common.ok
import com.github.kr328.clash.common.util.grantPermissions
import com.github.kr328.clash.glue.model.ConfigFile
import com.github.kr328.clash.glue.util.Validator
import com.github.kr328.clash.glue.util.ValidatorFileName
import com.github.kr328.clash.profile.Res
import com.github.kr328.clash.profile.file_name
import com.github.kr328.clash.profile.files
import com.github.kr328.clash.profile.import_
import com.github.kr328.clash.profile.invalid_file_name
import com.github.kr328.clash.profile.rename
import com.github.kr328.clash.profile.util.elapsedIntervalString
import com.github.kr328.clash.profile.vm.FilesViewModel
import com.github.kr328.clash.ui.component.SizeSpacer
import com.github.kr328.clash.ui.component.TabbyScaffold
import com.github.kr328.clash.ui.icon.BaselineAdd
import com.github.kr328.clash.ui.icon.BaselineEdit
import com.github.kr328.clash.ui.icon.BaselineGetApp
import com.github.kr328.clash.ui.icon.BaselineMoreVert
import com.github.kr328.clash.ui.icon.BaselineSave
import com.github.kr328.clash.ui.icon.OutlineArticle
import com.github.kr328.clash.ui.icon.OutlineDelete
import com.github.kr328.clash.ui.icon.OutlineFolder
import com.github.kr328.clash.ui.icon.TabbyIcons
import com.github.kr328.clash.ui.theme.PreviewTabby
import com.github.kr328.clash.ui.theme.TabbyThemeWrapper
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import me.saket.bytesize.binaryBytes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun FilesScreen(
  uuid: Uuid,
  modifier: Modifier = Modifier,
  viewModel: FilesViewModel = koinViewModel<FilesViewModel>(),
  onFinish: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var pendingImportTarget by remember { mutableStateOf<ConfigFile?>(null) }
  var pendingExportSource by remember { mutableStateOf<ConfigFile?>(null) }

  val openFileLauncher = rememberLauncherForActivityResult(StartActivityForResult()) {}

  val importLauncher =
    rememberLauncherForActivityResult(GetContent()) { uri ->
      viewModel.onImportResult(uri, pendingImportTarget)
      pendingImportTarget = null
    }

  val exportLauncher =
    rememberLauncherForActivityResult(CreateDocument("text/plain")) { uri ->
      viewModel.onExportResult(uri, pendingExportSource)
      pendingExportSource = null
    }

  LaunchedEffect(uuid) { viewModel.init(uuid = uuid) }

  LaunchedEffect(viewModel) {
    viewModel.eventState.collect { event ->
      when (event) {
        Finish -> {
          onFinish()
        }
        is OpenFile -> {
          openFileLauncher.launch(
            Intent(Intent.ACTION_VIEW).setDataAndType(event.uri, "text/plain").grantPermissions(),
          )
        }
        is RequestImport -> {
          pendingImportTarget = event.targetConfigFile
          importLauncher.launch("*/*")
        }
        is RequestExport -> {
          pendingExportSource = event.sourceConfigFile
          exportLauncher.launch(event.sourceConfigFile.name)
        }
        is ShowMessage -> {
          snackbarHostState.showSnackbar(message = event.message)
        }
      }
    }
  }

  LifecycleStartEffect(viewModel) {
    viewModel.resume()
    onStopOrDispose {}
  }

  FilesContent(
    modifier = modifier,
    snackbarHostState = snackbarHostState,
    uiState = uiState,
    onBack = viewModel::onBack,
    onOpen = viewModel::onOpen,
    onNew = { viewModel.onRequestImport(null) },
    onImport = { viewModel.onRequestImport(it) },
    onExport = { viewModel.onRequestExport(it) },
    onRename = viewModel::onRename,
    onDelete = viewModel::onDelete,
  )
}

@Composable
private fun FilesContent(
  modifier: Modifier = Modifier,
  snackbarHostState: SnackbarHostState,
  uiState: FilesViewModel.UiState,
  onBack: () -> Unit,
  onOpen: (ConfigFile) -> Unit,
  onNew: () -> Unit,
  onImport: (ConfigFile) -> Unit,
  onExport: (ConfigFile) -> Unit,
  onRename: (ConfigFile, String) -> Unit,
  onDelete: (ConfigFile) -> Unit,
) {
  var menuConfigFile by remember { mutableStateOf<ConfigFile?>(null) }
  var renameConfigFile by remember { mutableStateOf<ConfigFile?>(null) }
  val currentInBaseDir = uiState.currentInBaseDir
  val configurationEditable = uiState.configurationEditable
  val files = uiState.configFiles

  if (menuConfigFile != null) {
    ModalBottomSheet(
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      onDismissRequest = { menuConfigFile = null },
    ) {
      val file = menuConfigFile!!
      if (!file.isDirectory && (!currentInBaseDir || configurationEditable)) {
        FilesMenuAction(
          icon = TabbyIcons.BaselineGetApp,
          text = stringResource(Res.string.import_),
          onClick = {
            menuConfigFile = null
            onImport(file)
          },
        )
      }
      if (!file.isDirectory && file.size > 0) {
        FilesMenuAction(
          icon = TabbyIcons.BaselineSave,
          text = stringResource(CommonRes.string.export),
          onClick = {
            menuConfigFile = null
            onExport(file)
          },
        )
      }
      if (!currentInBaseDir) {
        FilesMenuAction(
          icon = TabbyIcons.BaselineEdit,
          text = stringResource(Res.string.rename),
          onClick = {
            menuConfigFile = null
            renameConfigFile = file
          },
        )
        FilesMenuAction(
          icon = TabbyIcons.OutlineDelete,
          text = stringResource(CommonRes.string.delete),
          tint = MaterialTheme.colorScheme.error,
          onClick = {
            menuConfigFile = null
            onDelete(file)
          },
        )
      }
      SizeSpacer(16.dp)
    }
  }

  BackHandler(onBack = onBack)

  TabbyScaffold(
    modifier = modifier,
    snackbarHostState = snackbarHostState,
    title = stringResource(Res.string.files),
    onBack = onBack,
    actions = {
      if (!currentInBaseDir) {
        IconButton(onClick = onNew) {
          Icon(
            imageVector = TabbyIcons.BaselineAdd,
            contentDescription = stringResource(CommonRes.string._new),
          )
        }
      }
    },
  ) { innerPadding ->
    val context = LocalContext.current
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
      while (true) {
        delay(1.minutes)
        currentTime = System.currentTimeMillis()
      }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      items(items = files, key = ConfigFile::id) { file ->
        FileItem(
          configFile = file,
          currentTime = currentTime,
          context = context,
          onClick = { onOpen(file) },
          onMore = { menuConfigFile = file },
        )
        HorizontalDivider()
      }
    }
  }

  if (renameConfigFile != null) {
    TextInputDialog(
      title = stringResource(Res.string.file_name),
      initialValue = renameConfigFile!!.name,
      hint = stringResource(Res.string.file_name),
      error = stringResource(Res.string.invalid_file_name),
      validator = ValidatorFileName,
      onDismiss = { renameConfigFile = null },
      onConfirm = { newName ->
        onRename(renameConfigFile!!, newName)
        renameConfigFile = null
      },
    )
  }
}

@Composable
private fun TextInputDialog(
  title: String,
  initialValue: String? = null,
  hint: String? = null,
  error: String? = null,
  validator: Validator = { true },
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  val initialText = initialValue.orEmpty()

  var inputText by remember {
    mutableStateOf(
      TextFieldValue(text = initialText, selection = TextRange(initialValue?.length ?: 0)),
    )
  }
  var inputError by remember { mutableStateOf(if (!validator(initialText)) error else null) }
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    keyboardController?.show()
  }

  val isValidInput = validator(inputText.text)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = inputText,
        onValueChange = { newValue ->
          inputText = newValue
          inputError =
            if (!validator(newValue.text)) {
              error
            } else {
              null
            }
        },
        label = hint?.let { { Text(it) } },
        isError = inputError != null,
        supportingText = inputError?.let { { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(inputText.text) }, enabled = isValidInput) {
        Text(stringResource(CommonRes.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(CommonRes.string.cancel)) }
    },
  )
}

@Composable
private fun FileItem(
  configFile: ConfigFile,
  currentTime: Long,
  context: Context,
  onClick: () -> Unit,
  onMore: () -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 56.dp)
        .clickable(onClick = onClick)
        .padding(end = 0.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier.size(width = 65.dp, height = 56.dp),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector =
          if (configFile.isDirectory) TabbyIcons.OutlineFolder else TabbyIcons.OutlineArticle,
        contentDescription = null,
        modifier = Modifier.size(28.dp),
      )
    }

    Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
      Text(text = configFile.name)
      if (!configFile.isDirectory) {
        SizeSpacer(3.dp)
        Text(
          text = configFile.size.binaryBytes.toString(),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    if (!configFile.isDirectory) {
      Text(
        text = (currentTime - configFile.lastModified).elapsedIntervalString(),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 8.dp),
      )
    }

    IconButton(onClick = onMore) {
      Icon(
        imageVector = TabbyIcons.BaselineMoreVert,
        contentDescription = stringResource(CommonRes.string.more),
      )
    }
  }
}

@Composable
private fun FilesMenuAction(
  icon: ImageVector,
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tint: Color = MaterialTheme.colorScheme.onSurface,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(24.dp),
    )
    SizeSpacer(16.dp)
    Text(text = text, color = tint)
  }
}

@PreviewWrapper(TabbyThemeWrapper::class)
@PreviewTabby
@Composable
private fun FilesContentPreview() {
  FilesContent(
    snackbarHostState = SnackbarHostState(),
    uiState =
      FilesViewModel.UiState(
        configFiles =
          listOf(
            ConfigFile("1", "config.yaml", 1024, System.currentTimeMillis() - 60_000, false),
            ConfigFile("2", "scripts", 0, System.currentTimeMillis() - 3_600_000, true),
          ),
        currentInBaseDir = true,
        configurationEditable = false,
      ),
    onBack = {},
    onOpen = {},
    onNew = {},
    onImport = {},
    onExport = {},
    onRename = { _, _ -> },
    onDelete = {},
  )
}
