import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.qtremors.arcile.plugin.glb"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.qtremors.arcile.plugin.glb"
        minSdk = 30
        targetSdk = 37
        versionCode = 100
        versionName = "1.0.0"
    }

    val keystoreProperties = Properties()
    var keystorePropertiesFile = rootProject.file("signing.properties")
    if (!keystorePropertiesFile.exists()) {
        keystorePropertiesFile = rootProject.file("local.properties")
    }
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(keystoreProperties::load)
    }
    val storeFileProp = keystoreProperties["signing.storeFile"]?.toString()
    val storePasswordProp = keystoreProperties["signing.storePassword"]?.toString()
    val keyAliasProp = keystoreProperties["signing.keyAlias"]?.toString()
    val keyPasswordProp = keystoreProperties["signing.keyPassword"]?.toString()
    val hasSigningConfig = storeFileProp != null &&
        storePasswordProp != null &&
        keyAliasProp != null &&
        keyPasswordProp != null

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                val configuredStoreFile = File(storeFileProp!!)
                storeFile = if (configuredStoreFile.isAbsolute) {
                    configuredStoreFile
                } else {
                    rootProject.file("app").resolve(configuredStoreFile)
                }
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val version = output.versionName.get() ?: "0.0.0"
            output.outputFileName.set("Arcile-GLB-Viewer-$version.apk")
        }
    }
}

dependencies {
    implementation(project(":plugin-api"))
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio.android)
    implementation(libs.filament.utils.android)
    implementation(libs.sceneview)

    testImplementation(libs.junit)
    testImplementation(libs.org.robolectric.robolectric)
}
