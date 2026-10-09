package com.github.kr328.clash.settings.vm

import android.app.Application
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.glue.util.clashDir
import com.github.kr328.clash.glue.util.withClash
import com.github.kr328.clash.settings.ui.MetaFeatureSettingsActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MetaFeatureSettingsViewModel(
  private val application: Application,
  private val scope: CoroutineScope,
) : ViewModel(), MetaFeatureSettingsActions {
  private val validDatabaseExtensions = listOf(".metadb", ".db", ".dat", ".mmdb")
  @Volatile private var skipPersist = false

  val configuration: StateFlow<ConfigurationOverride>
    field = MutableStateFlow(ConfigurationOverride())

  val importResult: StateFlow<ImportResult>
    field = MutableStateFlow<ImportResult>(ImportResult.Idle)

  init {
    viewModelScope.launch {
      configuration.value = withClash { queryOverride(Clash.OverrideSlot.Persist) }
    }
  }

  fun persist() {
    // Intended to use non-viewModel scope as we need the action to be called on disposed.
    scope.launch {
      withClash {
        if (skipPersist) clearOverride(Clash.OverrideSlot.Persist)
        else patchOverride(Clash.OverrideSlot.Persist, configuration.value)
      }
    }
  }

  fun resetOverride() {
    skipPersist = true
  }

  fun importGeoFile(uri: Uri?, importType: ImportType) {
    viewModelScope.launch(Dispatchers.IO) {
      importResult.value = ImportResult.InProgress
      try {
        val resolver = application.contentResolver
        val cursor: Cursor =
          uri?.let { resolver.query(it, null, null, null, null, null) }
            ?: run {
              importResult.value = ImportResult.Failed
              return@launch
            }

        importResult.value = cursor.use {
          if (!it.moveToFirst()) return@use ImportResult.Failed

          val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          val displayName = if (columnIndex != -1) it.getString(columnIndex) else ""
          val ext = "." + displayName.substringAfterLast(".").lowercase()

          if (!validDatabaseExtensions.contains(ext)) {
            return@use ImportResult.UnsupportedFormat(validDatabaseExtensions.joinToString("/"))
          }

          val outputFileName =
            when (importType) {
              GeoIp -> "geoip$ext"
              GeoSite -> "geosite$ext"
              Country -> "country$ext"
              ASN -> "ASN$ext"
            }

          val outputFile = application.clashDir.resolve(outputFileName)
          outputFile.parentFile?.mkdirs()
          val inputStream = resolver.openInputStream(uri) ?: return@use ImportResult.Failed
          inputStream.use { ins -> outputFile.outputStream().use { outs -> ins.copyTo(outs) } }
          return@use ImportResult.Success(displayName)
        }
      } catch (e: Exception) {
        Logger.e("Import geo database failed: ${e.message}", e)
        importResult.value = ImportResult.Failed
      }
    }
  }

  override fun updateUnifiedDelay(value: Boolean?) = configuration.update {
    it.copy(unifiedDelay = value)
  }

  override fun updateGeodataMode(value: Boolean?) = configuration.update {
    it.copy(geodataMode = value)
  }

  override fun updateTcpConcurrent(value: Boolean?) = configuration.update {
    it.copy(tcpConcurrent = value)
  }

  override fun updateFindProcessMode(value: ConfigurationOverride.FindProcessMode?) =
    configuration.update {
      it.copy(findProcessMode = value)
    }

  override fun updateSnifferEnable(value: Boolean?) = configuration.update {
    it.copy(sniffer = it.sniffer.copy(enable = value))
  }

  override fun updateSniffHttpPorts(value: List<String>?) = configuration.update {
    it.copy(
      sniffer =
        it.sniffer.copy(
          sniff = it.sniffer.sniff.copy(http = it.sniffer.sniff.http.copy(ports = value)),
        ),
    )
  }

  override fun updateSniffHttpOverrideDestination(value: Boolean?) = configuration.update {
    it.copy(
      sniffer =
        it.sniffer.copy(
          sniff =
            it.sniffer.sniff.copy(http = it.sniffer.sniff.http.copy(overrideDestination = value)),
        ),
    )
  }

  override fun updateSniffTlsPorts(value: List<String>?) = configuration.update {
    it.copy(
      sniffer =
        it.sniffer.copy(
          sniff = it.sniffer.sniff.copy(tls = it.sniffer.sniff.tls.copy(ports = value)),
        ),
    )
  }

  override fun updateSniffTlsOverrideDestination(value: Boolean?) = configuration.update {
    it.copy(
      sniffer =
        it.sniffer.copy(
          sniff =
            it.sniffer.sniff.copy(tls = it.sniffer.sniff.tls.copy(overrideDestination = value)),
        ),
    )
  }

  override fun updateSniffQuicPorts(value: List<String>?) = configuration.update {
    it.copy(
      sniffer =
        it.sniffer.copy(
          sniff = it.sniffer.sniff.copy(quic = it.sniffer.sniff.quic.copy(ports = value)),
        ),
    )
  }

  override fun updateSniffQuicOverrideDestination(value: Boolean?) = configuration.update {
    it.copy(
      sniffer =
        it.sniffer.copy(
          sniff =
            it.sniffer.sniff.copy(quic = it.sniffer.sniff.quic.copy(overrideDestination = value)),
        ),
    )
  }

  override fun updateForceDnsMapping(value: Boolean?) = configuration.update {
    it.copy(sniffer = it.sniffer.copy(forceDnsMapping = value))
  }

  override fun updateParsePureIp(value: Boolean?) = configuration.update {
    it.copy(sniffer = it.sniffer.copy(parsePureIp = value))
  }

  override fun updateOverrideDestination(value: Boolean?) = configuration.update {
    it.copy(sniffer = it.sniffer.copy(overrideDestination = value))
  }

  override fun updateForceDomain(value: List<String>?) = configuration.update {
    it.copy(sniffer = it.sniffer.copy(forceDomain = value))
  }

  override fun updateSkipDomain(value: List<String>?) = configuration.update {
    it.copy(sniffer = it.sniffer.copy(skipDomain = value))
  }

  override fun updateSkipSrcAddress(value: List<String>?) = configuration.update {
    it.copy(sniffer = it.sniffer.copy(skipSrcAddress = value))
  }

  override fun updateSkipDstAddress(value: List<String>?) = configuration.update {
    it.copy(sniffer = it.sniffer.copy(skipDstAddress = value))
  }

  enum class ImportType {
    GeoIp,
    GeoSite,
    Country,
    ASN,
  }

  sealed interface ImportResult {
    data object Idle : ImportResult

    data object InProgress : ImportResult

    data class Success(val displayName: String) : ImportResult

    data class UnsupportedFormat(val summary: String) : ImportResult

    data object Failed : ImportResult
  }
}
