plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.qtremors.arcile.core.runtime"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core:storage:domain"))
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
}
