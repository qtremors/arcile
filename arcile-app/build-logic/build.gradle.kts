plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        register("arcileAndroidApplicationConventions") {
            id = "arcile.android.application.conventions"
            implementationClass = "dev.qtremors.arcile.buildlogic.ArcileAndroidApplicationConventionsPlugin"
        }
    }
}
