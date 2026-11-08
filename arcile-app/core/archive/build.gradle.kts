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
    implementation(project(":core:storage:domain"))
    implementation(libs.apache.commons.compress)
    implementation(libs.tukaani.xz)
    implementation(libs.zip4j)

    testImplementation(project(":core:storage:domain"))
    testImplementation(libs.junit)
}
