plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.linksaver.qnxtwk"
    minSdk = 24
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
    create("smooth") {
      initWith(getByName("release"))
      matchingFallbacks += listOf("release")
      signingConfig = signingConfigs.getByName("debugConfig")
      isDebuggable = false
    }
    // Note: For local release-like frame timing profiling signed with debugConfig,
    // use the smooth buildType.
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  lint {
    abortOnError = false
    checkDependencies = false
    informational += listOf("UnusedResources")
  }
}

// Compose Strong Skipping Mode is enabled by default in Kotlin 2.0.20+ (project uses Kotlin 2.2.10).
// To generate Compose Compiler stability & skippability reports to inspect unstable classes, run:
//   gradle :app:compileDebugKotlin -PcomposeCompilerReports=true
// Reports are written to: app/build/compose_compiler/
composeCompiler {
  if (project.findProperty("composeCompilerReports") == "true") {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
  }
}

// Configure Room schema directory export
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}



// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(project(":core:security"))
  implementation(project(":core:crypto"))
  implementation(project(":core:data"))
  implementation(project(":feature:vault"))
  implementation(project(":feature:autofill"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.biometric)
  implementation(libs.androidx.security.crypto)
  implementation(libs.sqlcipher.android)
  implementation(libs.androidx.sqlite)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

// ==========================================
// Phase 3e Architecture & Policy Enforcement Tasks
// ==========================================

tasks.register("verifyNoNetworkPermissions") {
  notCompatibleWithConfigurationCache("Requires dynamic directory inspection")
  description = "Verifies that the merged manifest and source manifest contain zero network permissions"
  group = "verification"
  doLast {
    val forbiddenPermissions = listOf(
      "android.permission.INTERNET",
      "android.permission.ACCESS_NETWORK_STATE",
      "android.permission.ACCESS_WIFI_STATE",
      "android.permission.CHANGE_NETWORK_STATE"
    )
    val manifestFiles = fileTree(layout.buildDirectory.dir("intermediates")) {
      include("**/AndroidManifest.xml")
    }.plus(files("src/main/AndroidManifest.xml"))

    for (file in manifestFiles) {
      if (file.exists()) {
        val text = file.readText()
        for (perm in forbiddenPermissions) {
          if (text.contains(perm) && !text.contains("tools:node=\"remove\"")) {
            throw GradleException("FATAL ARCHITECTURE BREACH: Forbidden network permission '$perm' found in $file! LinkVault is strictly offline.")
          }
        }
      }
    }
    println("SUCCESS: verifyNoNetworkPermissions - Zero network permissions found.")
  }
}

tasks.register("verifyNoForbiddenDependencies") {
  notCompatibleWithConfigurationCache("Requires configurations inspection")
  description = "Verifies that no forbidden cloud, analytics, AI, or network libraries are present in configurations"
  group = "verification"
  doLast {
    val forbiddenPrefixes = listOf(
      "com.google.firebase",
      "com.google.android.gms",
      "retrofit2",
      "io.ktor",
      "com.google.ai.client.generativeai"
    )
    val checkConfigs = listOf("releaseRuntimeClasspath", "debugRuntimeClasspath")
    for (configName in checkConfigs) {
      val config = configurations.findByName(configName) ?: continue
      for (dep in config.resolvedConfiguration.resolvedArtifacts) {
        val id = "${dep.moduleVersion.id.group}:${dep.moduleVersion.id.name}"
        for (forbidden in forbiddenPrefixes) {
          if (id.startsWith(forbidden)) {
            throw GradleException("FATAL ARCHITECTURE BREACH: Forbidden dependency '$id' detected in configuration '$configName'! LinkVault is strictly zero-cloud.")
          }
        }
      }
    }
    println("SUCCESS: verifyNoForbiddenDependencies - Zero cloud/network dependencies detected.")
  }
}

tasks.register("verifyNoForbiddenImports") {
  notCompatibleWithConfigurationCache("Requires fileTree inspection")
  description = "Scans Kotlin source files for forbidden network/cloud imports"
  group = "verification"
  doLast {
    val forbiddenImports = listOf(
      "java.net.HttpURLConnection",
      "java.net.URL",
      "retrofit2.",
      "com.google.firebase",
      "okhttp3.OkHttpClient"
    )
    val srcFiles = fileTree("src/main/java") {
      include("**/*.kt")
    }
    for (file in srcFiles) {
      val lines = file.readLines()
      for ((idx, line) in lines.withIndex()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("import ")) {
          for (forbidden in forbiddenImports) {
            if (trimmed.contains(forbidden)) {
              throw GradleException("FATAL SECURITY BREACH: Forbidden import '$forbidden' detected in ${file.name}:${idx + 1}! Direct network calls are prohibited.")
            }
          }
        }
      }
    }
    println("SUCCESS: verifyNoForbiddenImports - Zero forbidden network/cloud imports detected.")
  }
}

tasks.named("check") {
  dependsOn("verifyNoNetworkPermissions", "verifyNoForbiddenDependencies", "verifyNoForbiddenImports")
}
