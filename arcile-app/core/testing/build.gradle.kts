plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.qtremors.arcile.core.testing"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    api(project(":core:storage:domain"))
    api(project(":core:operation"))
    api(project(":core:runtime"))

    api(libs.junit)
    api(libs.kotlinx.coroutines.test)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
