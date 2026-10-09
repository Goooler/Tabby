package com.github.kr328.clash.crash.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.crash.Res
import com.github.kr328.clash.crash.application_crashed
import com.github.kr328.clash.crash.vm.AppCrashedViewModel
import com.github.kr328.clash.ui.component.TabbyScaffold
import com.github.kr328.clash.ui.theme.PreviewTabby
import com.github.kr328.clash.ui.theme.TabbyThemeWrapper
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun AppCrashedScreen(
  modifier: Modifier = Modifier,
  viewModel: AppCrashedViewModel = koinViewModel(),
) {
  val logs by viewModel.logs.collectAsStateWithLifecycle()

  AppCrashedContent(modifier = modifier, logs = logs)
}

@Composable
private fun AppCrashedContent(modifier: Modifier = Modifier, logs: String) {
  TabbyScaffold(modifier = modifier, title = stringResource(Res.string.application_crashed)) {
    innerPadding ->
    SelectionContainer {
      Text(
        text = logs,
        style =
          MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5,
          ),
        modifier =
          Modifier.fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
      )
    }
  }
}

@PreviewWrapper(TabbyThemeWrapper::class)
@PreviewTabby
@Composable
private fun AppCrashedScreenPreview() {
  AppCrashedContent(
    logs =
      "04-20 10:10:10.000 I/App( 1234): App version: 2.0.0\n" +
        "04-20 10:10:10.100 E/App( 1234): java.lang.IllegalStateException: Example crash\n" +
        "    at com.example.app.MainActivity.onCreate(MainActivity.kt:42)\n" +
        "    at android.app.Activity.performCreate(Activity.java:9000)",
  )
}
