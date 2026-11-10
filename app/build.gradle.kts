@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("com.google.devtools.ksp")
    kotlin("kapt")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization")
}
apply(plugin = "dagger.hilt.android.plugin")

sealed class Version(
    open val versionMajor: Int,
    val versionMinor: Int,
    val versionPatch: Int,
    val versionBuild: Int = 0
) {
    abstract fun toVersionName(): String
    class Alpha(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
        Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName(): String =
            "${versionMajor}.${versionMinor}.${versionPatch}-alpha.$versionBuild"
    }

    class Beta(versionMajor: Int, versionMinor: Int, versionPatch: Int, versionBuild: Int) :
        Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName(): String =
            "${versionMajor}.${versionMinor}.${versionPatch}-beta.$versionBuild"
    }

    class Stable(versionMajor: Int, versionMinor: Int, versionPatch: Int) :
        Version(versionMajor, versionMinor, versionPatch) {
        override fun toVersionName(): String =
            "${versionMajor}.${versionMinor}.${versionPatch}"
    }

    class ReleaseCandidate(
        versionMajor: Int,
        versionMinor: Int,
        versionPatch: Int,
        versionBuild: Int
    ) :
        Version(versionMajor, versionMinor, versionPatch, versionBuild) {
        override fun toVersionName(): String =
            "${versionMajor}.${versionMinor}.${versionPatch}-rc.$versionBuild"
    }
}

val currentVersion: Version = Version.Stable(
    versionMajor = 2,
    versionMinor = 0,
    versionPatch = 3,
)

val keystorePropertiesFile: File = rootProject.file("keystore.properties")

val splitApks = !project.hasProperty("noSplits")

val abiFilterList = (properties["ABI_FILTERS"] as String).split(';')


android {
    // Signing material comes either from keystore.properties (local) or from CI
    // environment variables, so release builds are reproducible without committing secrets.
    val envStore = System.getenv("DUCK_KEYSTORE_PATH")
    val envStorePassword = System.getenv("DUCK_KEYSTORE_PASSWORD")
    val envKeyAlias = System.getenv("DUCK_KEY_ALIAS")
    val envKeyPassword = System.getenv("DUCK_KEY_PASSWORD")
    val hasEnvSigning =
        !envStore.isNullOrBlank() && !envStorePassword.isNullOrBlank() && !envKeyAlias.isNullOrBlank()

    if (keystorePropertiesFile.exists() || hasEnvSigning) {
        val keystoreProperties = Properties()
        if (keystorePropertiesFile.exists()) {
            keystoreProperties.load(FileInputStream(keystorePropertiesFile))
        }
        signingConfigs {
            getByName("debug") {
                if (hasEnvSigning) {
                    keyAlias = envKeyAlias
                    keyPassword = envKeyPassword
                    storeFile = file(envStore!!)
                    storePassword = envStorePassword
                } else {
                    keyAlias = keystoreProperties["keyAlias"].toString()
                    keyPassword = keystoreProperties["keyPassword"].toString()
                    storeFile = file(keystoreProperties["storeFile"]!!)
                    storePassword = keystoreProperties["storePassword"].toString()
                }
            }
        }
    }

    compileSdk = 34



    defaultConfig {
        applicationId = "com.duck.app"
        minSdk = 21
        targetSdk = 34
        versionCode = 20000003

        if (splitApks) {
            splits {
                abi {
                    isEnable = true
                    reset()
                    include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                    isUniversalApk = true
                }
            }
        }

        versionName = currentVersion.toVersionName().run {
            if (!splitApks) "$this-(F-Droid)"
            else this
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ksp {
            arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
            arg("room.incremental", "true")
        }
        if (!splitApks) {
            ndk {
                abiFilters.addAll(abiFilterList)
            }
        }
    }
    val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)

    androidComponents {
        onVariants { variant ->

            variant.outputs.forEach { output ->
                val name =
                    if (splitApks) {
                        output.filters.find { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
                    } else {
                        abiFilterList.firstOrNull()
                    }

                val baseAbiCode = abiCodes[name]

                if (baseAbiCode != null) {
                    output.versionCode.set(baseAbiCode + (output.versionCode.get() ?: 0))
                }

            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists() || hasEnvSigning) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        debug {
            if (keystorePropertiesFile.exists() || hasEnvSigning) {
                signingConfig = signingConfigs.getByName("debug")
            }
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "duck Debug")
        }
    }

    buildFeatures {
        compose = true
    }

    lint {
        disable.addAll(listOf("MissingTranslation", "ExtraTranslation", "MissingQuantity"))
    }

    applicationVariants.all {
        // name looks like "arm64-v8aRelease" or "universalRelease"; strip the build type
        // so artifacts read duck-2.0.0-arm64-v8a.apk
        val buildType = buildType.name
        outputs.all {
            val abi = name.removeSuffix(buildType).removeSuffix("-")
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName =
                "duck-${defaultConfig.versionName}-${abi}-${buildType}.apk"
        }
    }

    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.androidxComposeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs.useLegacyPackaging = true
    }
    androidResources {
        generateLocaleConfig = true
    }

    namespace = "com.duck.app"
}

kotlin {
    jvmToolchain(11)
}

dependencies {

    implementation(project(":color"))

    //Core libs for the app
    implementation(libs.bundles.core)

    //Lifecycle support for Jetpack Compose
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewModelCompose)

    //Material UI, Accompanist...
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidxCompose)
    implementation(libs.bundles.accompanist)

    //Coil (For Jetpack Compose)
    implementation(libs.coil.kt.compose)

    //Serialization
    implementation(libs.kotlinx.serialization.json)

    //DI (Dependency Injection - Hilt)
    implementation(libs.androidx.hilt.navigation.compose)
    kapt(libs.hilt.ext.compiler)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    //Database powered by Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network requests (OkHttp)
    implementation(libs.okhttp)

    //YoutubeDl for Android (youtubedl-android)
    implementation(libs.bundles.youtubedlAndroid)

    //SVG implementation (AndroidSVG by Caverock)
    implementation(libs.caverock.androidsvg)

    //MMKV (Ultrafast Key-Value storage)
    implementation(libs.mmkv)

    //Unit testing libraries
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
//  androidTestImplementation(libs.androidx.compose.ui.test)

    //UI debugging library for Jetpack Compose
    implementation(libs.androidx.compose.ui.tooling)

}

class RoomSchemaArgProvider(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File
) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> {
        return listOf("room.schemaLocation=${schemaDir.path}")
    }
}
