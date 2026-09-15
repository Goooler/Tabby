plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
}

dependencies {
  implementation(projects.core)
  implementation(projects.common)

  implementation(libs.kotlin.coroutine.android)
  implementation(libs.androidx.room.runtime)

  ksp(libs.androidx.room.compiler)
}
