import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.protobuf)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

// Short commit hash of the working tree, with a "+" appended when there are uncommitted changes.
// versionName alone cannot identify a build: 1.4.7 and 1.4.7v4 both reported as "1.4.7 (147)" in
// bug reports, so we could not tell which build a crash came from. This is computed at build time
// and never has to be remembered. Falls back to "unknown" outside a git checkout or without git,
// so the build never depends on it.
val gitBuildId: String = try {
    val hash = providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        workingDir = rootProject.projectDir
    }.standardOutput.asText.get().trim()
    val dirty = providers.exec {
        commandLine("git", "status", "--porcelain")
        workingDir = rootProject.projectDir
    }.standardOutput.asText.get().trim().isNotEmpty()
    if (hash.isEmpty()) "unknown" else hash + if (dirty) "+" else ""
} catch (e: Exception) {
    "unknown"
}

val hasSigningConfig = listOf(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD"
).all { localProperties[it] != null }

android {
    namespace = "com.zomdroid"
    // Compose Backdrop/Shapes require API 36 at compile time; target/minSdk remain unchanged.
    compileSdk = 36

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = file(localProperties["RELEASE_STORE_FILE"].toString())
                storePassword = localProperties["RELEASE_STORE_PASSWORD"].toString()
                keyAlias = localProperties["RELEASE_KEY_ALIAS"].toString()
                keyPassword = localProperties["RELEASE_KEY_PASSWORD"].toString()

            }
        }
    }

    defaultConfig {
        applicationId = "com.zomdroid"
        minSdk = 30
        targetSdk = 35
        versionCode = 148
        versionName = "1.4.8"

        buildConfigField("String", "GIT_BUILD_ID", "\"$gitBuildId\"")

        // JavaSteam + protobuf + kotlin stack push past the 64K method limit.
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.add("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                // Align native .so segments to 16 KB pages (Android 15+ requirement).
                // NDK r27 doesn't enable this by default; the flag adds -Wl,-z,max-page-size=16384.
                arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
            }
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            outputImpl.outputFileName = "zomdroid-${variant.buildType.name}-${variant.versionName}.apk"
        }
    }

    buildTypes {
        if (hasSigningConfig) {
            release {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            // JavaSteam / protobuf / bouncycastle / kotlin bring duplicate metadata files.
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/{AL2.0,LGPL2.1}",
                "**/*.proto"
            )
        }
    }
  ndkVersion = "27.3.13750724"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.31.1"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                maybeCreate("java").apply {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(files("jars/fmod.jar"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.commons.io)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.legacy.support.v4)
    implementation(libs.androidx.security.crypto)

    // --- In-app Steam downloader (ported from RimDroid, MIT). JavaSteam = SteamKit2 port. ---
    implementation("in.dragonbra:javasteam:1.8.0")
    implementation("in.dragonbra:javasteam-depotdownloader:1.8.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.83")     // crypto provider JavaSteam needs
    implementation("com.google.protobuf:protobuf-java:4.31.1") // must match JavaSteam's protobuf
    implementation("com.github.luben:zstd-jni:1.5.7-6@aar")    // zstd depot-chunk decompression (arm64 .so)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockwebserver3)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
