plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "quill"
include("base", "1_21_R1", "1_21_R2", "1_21_R3", "1_20_R4")