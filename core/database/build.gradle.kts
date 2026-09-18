plugins {
  alias(libs.plugins.android.multiplatform)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

kotlin {
  compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

  jvm("desktop")
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(projects.core.model)
      implementation(libs.androidx.room.runtime)
      implementation(libs.androidx.sqlite.bundled)
    }
    commonTest.dependencies { implementation(kotlin("test")) }
    androidUnitTest.dependencies { implementation(libs.androidx.room.sqlite.wrapper) }
  }
}

dependencies {
  add("kspAndroid", libs.androidx.room.compiler)
  add("kspDesktop", libs.androidx.room.compiler)
  add("kspIosArm64", libs.androidx.room.compiler)
  add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

room3 {
  schemaDirectory("$projectDir/schemas")
}
