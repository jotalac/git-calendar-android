import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.jotalac"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.jotalac"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 code and resource shrinking; the default Android rules are included.
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        generateLocaleConfig = true
    }
}

// The Go core is bound with gomobile from ../core-shim into app/libs/gcal.aar (see the root Makefile).
// `go` is looked up from `go.path` in local.properties, then the usual install locations.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val goBinary: String = localProps.getProperty("go.path")
    ?: listOf("/opt/homebrew/bin/go", "/usr/local/go/bin/go", "/usr/local/bin/go")
        .firstOrNull { File(it).exists() }
    ?: "go"
val sdkDir: String? = localProps.getProperty("sdk.dir")

val buildCoreAar by tasks.registering(Exec::class) {
    description = "Builds app/libs/gcal.aar from core-shim with gomobile"
    workingDir = rootProject.projectDir
    inputs.dir(rootProject.file("core-shim"))
    inputs.dir(rootProject.file("core/pkg")) // core-shim/go.mod replaces the core with ../core
    outputs.file(file("libs/gcal.aar"))
    commandLine(
        buildList {
            add("make"); add("core"); add("GO=$goBinary")
            sdkDir?.let { add("ANDROID_HOME=$it") }
        }
    )
}
tasks.named("preBuild") { dependsOn(buildCoreAar) }

dependencies {
    implementation(files("libs/gcal.aar"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
