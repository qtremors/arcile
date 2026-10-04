plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        register("arcileBuildVerification") {
            id = "arcile.build.verification"
            implementationClass = "dev.qtremors.arcile.buildlogic.ArcileBuildVerificationPlugin"
        }
    }
}
