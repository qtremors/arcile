plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.qtremors.arcile.plugin.api"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
}
