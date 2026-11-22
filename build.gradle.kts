import dev.detekt.gradle.extensions.DetektExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.ktlint) apply false
  alias(libs.plugins.detekt) apply false
}

subprojects {
  configurations.configureEach {
    resolutionStrategy.force(libs.kotlin.metadata.jvm)
  }
  plugins.withId("dev.detekt") {
    extensions.configure<DetektExtension> {
      buildUponDefaultConfig = true
      config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    }
  }
}
