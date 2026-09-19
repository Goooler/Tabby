import com.android.build.api.variant.FilterConfiguration
import de.undercouch.gradle.tasks.download.Download
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.jb.compose)
  alias(libs.plugins.download)
  alias(libs.plugins.r8booster)
}

android {
  namespace = "com.github.kr328.clash.app"
  defaultConfig {
    applicationId = "io.github.goooler.tabby"
    targetSdk = 37
    versionName = "3.3.1"
    versionCode = checkNotNull(versionName).toVersionCode()
  }

  val releaseSigning =
    signingConfigs.create("release") {
      storeFile = file("release.keystore")
      storePassword = "demo.app"
      keyAlias = "key0"
      keyPassword = "demo.app"
    }

  buildTypes {
    all {
      buildConfigField("String", "COMMIT", "\"$commitHash\"")
      signingConfig = releaseSigning
    }
    release {
      optimization {
        enable = true
      }
    }
  }

  buildFeatures {
    buildConfig = true
  }

  packaging {
    jniLibs { useLegacyPackaging = true }
    resources { excludes.add("DebugProbesKt.bin") }
  }

  splits {
    abi {
      isEnable = true
      isUniversalApk = true
      reset()
      include("arm64-v8a", "x86_64")
    }
  }
}

androidComponents {
  onVariants { variant ->
    variant.outputs.forEach { output ->
      with(output) {
        val abiName =
          filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
            ?: "universal"
        outputFileName =
          "Tabby-${versionName.get()}-${versionCode.get()}-$abiName-${variant.buildType}.apk"
      }
    }
  }
}

dependencies {
  implementation(projects.glue)
  implementation(projects.ui)
  implementation(projects.ui.crash)
  implementation(projects.ui.home)
  implementation(projects.ui.log)
  implementation(projects.ui.proxy)
  implementation(projects.ui.profile)
  implementation(projects.ui.settings)

  implementation(libs.kotlin.coroutine.android)

  implementation(libs.androidx.core)
  implementation(libs.androidx.activity.compose)

  implementation(libs.jetbrains.compose.uiToolingPreview)
  debugImplementation(libs.jetbrains.compose.uiTooling)

  implementation(libs.jetbrains.androidx.navigation3.ui)

  implementation(libs.koin.android)
}

val downloadGeoFiles =
  tasks.register<Download>("downloadGeoFiles") {
    src(
      // https://github.com/MetaCubeX/meta-rules-dat
      listOf(
        "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geoip.metadb",
        "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/geosite.dat",
        "https://cdn.jsdelivr.net/gh/MetaCubeX/meta-rules-dat@release/GeoLite2-ASN.mmdb",
      )
    )
    dest("src/main/assets")
    onlyIfModified(true)
    eachFile {
      if (name == "GeoLite2-ASN.mmdb") {
        name = "ASN.mmdb"
      }
    }

    val skipDownloadGeoFiles = providers.provider {
      val propsFile =
        rootProject.file("local.properties").takeIf { it.exists() }
          ?: rootProject.file("gradle.properties")
      val properties = Properties().apply { propsFile.inputStream().use { load(it) } }
      properties.getProperty("skip.downloadGeoFiles").toBoolean() &&
        dest.exists() &&
        dest.listFiles().orEmpty().size == 3
    }
    // Skip the task when the flag is set.
    onlyIf { !skipDownloadGeoFiles.get() }
  }

tasks.preBuild { dependsOn(downloadGeoFiles) }

tasks.clean { delete(downloadGeoFiles) }

fun String.toVersionCode(): Int {
  val (major, minor, patch) = split('.').map { it.toInt() }
  return major * 1_000_000 + minor * 1_000 + patch
}

val commitHash
  get() =
    providers
      .exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }
      .standardOutput
      .asText
      .get()
      .trim()
