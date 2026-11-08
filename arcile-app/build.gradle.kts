// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            packaging {
                jniLibs {
                    keepDebugSymbols += setOf(
                        "**/libandroidx.graphics.path.so",
                        "**/libdatastore_shared_counter.so"
                    )
                }
            }
        }
    }

    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
            // Reports and metrics make every Compose compile slower, so they are opt-in.
            if (providers.gradleProperty("arcile.composeReports").orNull == "true") {
                reportsDestination.set(layout.buildDirectory.dir("compose_compiler/reports"))
                metricsDestination.set(layout.buildDirectory.dir("compose_compiler/metrics"))
            }
        }
    }

    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        jvmArgs("-Xshare:off")
    }
}
