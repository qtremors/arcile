plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    api(project(":core:storage:domain"))
    api(project(":core:operation:api"))

    api(libs.junit)
    api(libs.kotlinx.coroutines.test)

    implementation(libs.kotlinx.serialization.json)
}
