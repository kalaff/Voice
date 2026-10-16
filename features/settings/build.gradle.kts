plugins {
  id("voice.library")
  id("voice.compose")
  alias(libs.plugins.metro)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  implementation(projects.core.common)
  implementation(projects.navigation)
  implementation(projects.core.strings)
  implementation(projects.core.featureflag)
  implementation(projects.core.ui)
  implementation(projects.core.remoteconfig.api)
  implementation(projects.core.data.api)
  implementation(projects.core.playback)
  implementation(projects.core.documentfile)
  implementation(projects.core.audiobookshelf)
  implementation(libs.serialization.json)

  implementation(libs.androidxCore)
}
