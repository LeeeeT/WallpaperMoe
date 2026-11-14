import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val version = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}

val signingNames = listOf("moe.storeFile", "moe.storePassword", "moe.keyAlias", "moe.keyPassword")
val signing = signingNames.associateWith { providers.gradleProperty(it).orNull?.takeIf(String::isNotBlank) }
val signingReady = signing.values.all { it != null }
require(signingReady || signing.values.all { it == null }) {
    "Set all or none of these properties: ${signingNames.joinToString()}"
}

android {
    namespace = "dev.leeeet.wallpapermoe"
    compileSdk {
        version = release(37)
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "dev.leeeet.wallpapermoe"
        minSdk = 34
        targetSdk = 37
        versionCode = version.getProperty("versionCode").toInt()
        versionName = version.getProperty("versionName")
    }

    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = file(signing.getValue("moe.storeFile")!!)
                storePassword = signing.getValue("moe.storePassword")
                keyAlias = signing.getValue("moe.keyAlias")
                keyPassword = signing.getValue("moe.keyPassword")
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.reorderable)
    implementation(libs.telephoto)

    testImplementation(libs.junit)
}
