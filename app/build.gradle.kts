/*
 * Copyright (C) 2023 Xizhi Zhu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val keystorePropertiesFile = rootProject.file("keystore.properties")

plugins {
    id("com.android.application")
    id(Dependencies.Hilt.plugin)
    kotlin("android")
    kotlin("kapt")
}

android {
    namespace = Configurations.applicationId

    compileOptions {
        sourceCompatibility = Versions.java
        targetCompatibility = Versions.java
    }
    buildToolsVersion = Versions.Sdk.buildTools
    compileSdk = Versions.Sdk.compile

    defaultConfig {
        applicationId = Configurations.applicationId

        minSdk = Versions.Sdk.min
        targetSdk = Versions.Sdk.target

        versionCode = Versions.App.code
        versionName = Versions.App.name

        multiDexEnabled = true

    }

    androidResources.localeFilters.addAll(Configurations.supportedLocales)

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("debug").java.srcDirs("src/debug/kotlin")
        getByName("release").java.srcDirs("src/release/kotlin")

        getByName("test").java.srcDirs("src/test/kotlin")
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(keystorePropertiesFile.inputStream())

                val configuredStoreFile = File(keystoreProperties.getProperty("KEYSTORE_FILE"))
                storeFile = if (configuredStoreFile.isAbsolute) configuredStoreFile else File(rootDir, configuredStoreFile.path)
                storePassword = keystoreProperties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = " debug"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true

        unitTests.all { test ->
            test.maxParallelForks = Runtime.getRuntime().availableProcessors() / 2
            // Robolectric 4.9.2 supports Android 13; production still compiles and targets API 36.
            test.systemProperty("robolectric.enabledSdks", "33")
        }
    }

    packaging {
        resources.excludes.addAll(listOf(
            "META-INF/atomicfu.kotlin_module", "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/licenses/*",

            // https://github.com/Kotlin/kotlinx.coroutines/tree/master/kotlinx-coroutines-debug#debug-agent-and-android
            "win32-x86/attach_hotspot_windows.dll", "win32-x86-64/attach_hotspot_windows.dll"
        ))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// allow references to generated code
kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(Dependencies.Kotlin.coroutines)

    debugImplementation(Dependencies.AndroidX.multidex)
    implementation(Dependencies.AndroidX.activity)
    implementation(Dependencies.AndroidX.annotation)
    implementation(Dependencies.AndroidX.appCompat)
    implementation(Dependencies.AndroidX.core)
    implementation(Dependencies.AndroidX.Fragment.core)
    implementation(Dependencies.AndroidX.Lifecycle.common)
    implementation(Dependencies.AndroidX.Lifecycle.runtime)
    implementation(Dependencies.AndroidX.Lifecycle.viewModel)
    implementation(Dependencies.AndroidX.View.constraintLayout)
    implementation(Dependencies.AndroidX.View.coordinatorLayout)
    implementation(Dependencies.AndroidX.View.drawerLayout)
    implementation(Dependencies.AndroidX.View.recyclerView)
    implementation(Dependencies.AndroidX.View.swipeRefreshLayout)
    implementation(Dependencies.AndroidX.View.viewPager2)

    implementation(Dependencies.Hilt.android)
    kapt(Dependencies.Hilt.compiler)

    implementation(Dependencies.ask)
    implementation(Dependencies.logger)
    implementation(Dependencies.materialComponent)

    testImplementation(Dependencies.Kotlin.test)
    testImplementation(Dependencies.Kotlin.coroutinesTest)
    testImplementation(Dependencies.Kotlin.reflect)
    debugImplementation(Dependencies.AndroidX.Fragment.test)
    testImplementation(Dependencies.AndroidX.Test.core)
    testImplementation("androidx.test.ext:junit:1.1.4")
    kaptTest(Dependencies.Hilt.compiler)
    testImplementation(Dependencies.Hilt.test)
    testImplementation(Dependencies.mockk)
    testImplementation(Dependencies.robolectric)
}
