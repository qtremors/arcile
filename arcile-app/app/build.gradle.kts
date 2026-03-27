import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.qtremors.arcile"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.qtremors.arcile"
        minSdk = 30
        targetSdk = 36
        versionCode = 40
        versionName = "0.5.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystoreProperties = Properties()
    var keystorePropertiesFile = rootProject.file("signing.properties")
    if (!keystorePropertiesFile.exists()) {
        keystorePropertiesFile = rootProject.file("local.properties")
    }
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use {
            keystoreProperties.load(it)
        }
    }

    val storeFileProp = keystoreProperties["signing.storeFile"]?.toString()
    val storePasswordProp = keystoreProperties["signing.storePassword"]?.toString()
    val keyAliasProp = keystoreProperties["signing.keyAlias"]?.toString()
    val keyPasswordProp = keystoreProperties["signing.keyPassword"]?.toString()

    val hasSigningConfig = storeFileProp != null && storePasswordProp != null && keyAliasProp != null && keyPasswordProp != null

    if (hasSigningConfig) {
        signingConfigs {
            create("release") {
                storeFile = file(storeFileProp!!)
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
            manifestPlaceholders["appLabel"] = "Arcile Debug"
        }
        release {
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appLabel"] = "Arcile"
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
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                // versionName already includes the "-debug" suffix from versionNameSuffix
                val version = variant.outputs.first().versionName.get() ?: "0.0.0"
                output.outputFileName.set("Arcile-$version.apk")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

tasks.register("checkProductionStrings") {
    group = "verification"
    description = "Flags obvious hardcoded production UI strings in key composables."

    doLast {
        val targetFiles = listOf(
            "src/main/java/dev/qtremors/arcile/presentation/ui/QuickAccessScreen.kt",
            "src/main/java/dev/qtremors/arcile/presentation/ui/StorageManagementScreen.kt",
            "src/main/java/dev/qtremors/arcile/presentation/ui/RecentFilesScreen.kt",
            "src/main/java/dev/qtremors/arcile/presentation/ui/TrashScreen.kt",
            "src/main/java/dev/qtremors/arcile/presentation/ui/HomeScreen.kt",
            "src/main/java/dev/qtremors/arcile/presentation/ui/components/ArcileTopBar.kt",
            "src/main/java/dev/qtremors/arcile/presentation/ui/components/SearchFiltersBottomSheet.kt",
            "src/main/java/dev/qtremors/arcile/presentation/ui/components/SortOptionDialog.kt"
        )
        val suspiciousPattern = Regex("""(Text\(".*[A-Za-z].*"\)|placeholder\s*=\s*".*[A-Za-z].*"|title\s*=\s*\{\s*Text\(".*[A-Za-z].*"\))""")
        val offenders = targetFiles.flatMap { relativePath ->
            val file = file(relativePath)
            if (!file.exists()) return@flatMap emptyList<String>()
            file.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                if (suspiciousPattern.containsMatchIn(trimmed) &&
                    !trimmed.contains("rememberDateFormatter") &&
                    !trimmed.contains("label = \"sort_bg\"")
                ) {
                    "$relativePath:${index + 1}: $trimmed"
                } else {
                    null
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(buildString {
                appendLine("Found hardcoded production UI strings:")
                offenders.forEach { appendLine(it) }
            })
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material.kolor)

    // Hilt DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.io.mockk.mockk)
    testImplementation(libs.app.cash.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.org.robolectric.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
