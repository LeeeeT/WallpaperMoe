import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val appVersionName = requireNotNull(versionProperties.getProperty("versionName")) {
    "versionName is missing from version.properties"
}
require(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(appVersionName)) {
    "versionName must be a stable semantic version"
}
val appVersionCode = requireNotNull(versionProperties.getProperty("versionCode")) {
    "versionCode is missing from version.properties"
}.toInt().also { require(it > 0) { "versionCode must be positive" } }

val releaseSigningNames = listOf(
    "moe.storeFile",
    "moe.storePassword",
    "moe.keyAlias",
    "moe.keyPassword",
)
val releaseSigningValues = releaseSigningNames.associateWith { name ->
    providers.gradleProperty(name).orNull?.takeIf(String::isNotBlank)
}
val releaseSigningEnabled = releaseSigningValues.values.all { it != null }
require(releaseSigningEnabled || releaseSigningValues.values.all { it == null }) {
    "Release signing is only partially configured; set every ${releaseSigningNames.joinToString()} property"
}

android {
    namespace = "dev.leeeet.wallpapermoe"
    compileSdk = 37

    // Must match what the Nix-provided SDK contains: the store path is
    // read-only, so AGP uses exactly what is there.
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "dev.leeeet.wallpapermoe"
        minSdk = 34
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        // Two dependencies ship .so files, so the APK carries a slice for
        // every ABI it targets.
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Derives the locale list from the values-* folders, so the per-app
    // language picker stays correct as translations are added or removed.
    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        if (releaseSigningEnabled) {
            create("release") {
                storeFile = file(releaseSigningValues.getValue("moe.storeFile")!!)
                storePassword = releaseSigningValues.getValue("moe.storePassword")
                keyAlias = releaseSigningValues.getValue("moe.keyAlias")
                keyPassword = releaseSigningValues.getValue("moe.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.reorderable)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.okhttp.mockwebserver)
}

// A typed task with declared inputs: an ad-hoc `doLast` would capture the
// build script itself, which the configuration cache cannot serialize.
abstract class VerifyReleaseSigning : DefaultTask() {
    @get:Input
    abstract val configured: Property<Boolean>

    @get:Input
    abstract val requiredProperties: ListProperty<String>

    @TaskAction
    fun verify() {
        check(configured.get()) {
            "Release signing is not configured; set every ${requiredProperties.get().joinToString()} property"
        }
    }
}

val verifyReleaseSigning by tasks.registering(VerifyReleaseSigning::class) {
    group = "verification"
    description = "Fails unless the production release-signing properties are complete."
    configured = releaseSigningEnabled
    requiredProperties = releaseSigningNames
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyReleaseSigning)
}
