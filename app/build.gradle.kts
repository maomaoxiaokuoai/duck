import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation("dev.chrisbanes.haze:haze:0.7.2")
            
            // Ktor
            val ktor_version = "2.3.8"
            implementation("io.ktor:ktor-client-core:$ktor_version")
            implementation("io.ktor:ktor-client-android:$ktor_version")
            implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
            implementation("io.ktor:ktor-client-logging:$ktor_version")
            
            // Kotlinx Serialization & Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            
            
        }
        
        androidMain.dependencies {
            implementation("androidx.core:core-ktx:1.12.0")
            implementation("androidx.core:core-splashscreen:1.0.1")
            implementation("androidx.activity:activity-compose:1.8.2")
            
            // Old Android-only dependencies (will break if moved to commonMain)
            implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
            implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
            implementation("androidx.compose.material:material-icons-extended:1.6.0")
            implementation("com.google.android.material:material:1.11.0")
            implementation("androidx.compose.material3:material3-window-size-class:1.2.0")
            
            val room_version = "2.6.1"
            implementation("androidx.room:room-runtime:$room_version")
            implementation("androidx.room:room-ktx:$room_version")
            
            implementation("io.coil-kt:coil-compose:2.6.0")
            implementation("io.coil-kt:coil-video:2.6.0")
            
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
            implementation("androidx.media3:media3-exoplayer:1.2.1")
            implementation("androidx.media3:media3-ui:1.2.1")
            
        }
    }
}

android {
    namespace = "com.duck.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.duck.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 20000070
        versionName = "2.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

    }

    signingConfigs {
        create("release") {
            // CI passes these through env vars; a local build without them stays unsigned.
            val path = System.getenv("DUCK_KEYSTORE_PATH")
            if (!path.isNullOrEmpty() && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("DUCK_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("DUCK_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("DUCK_KEY_PASSWORD") ?: ""
            }
        }
    }

    // One APK per ABI plus a universal one, named the way the release page expects.
    // Shipping every ABI in a single file doubled the download for no benefit.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            // FilterType.ABI.value is the string the API expects here.
            val abi = output.getFilter("ABI") ?: "universal"
            output.outputFileName = "duck-" + versionName + "-" + abi + "-release.apk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildFeatures {
        buildConfig = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.6.1")
}

