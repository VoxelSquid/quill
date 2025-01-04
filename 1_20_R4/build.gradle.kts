group = "me.voxelsquid"
version = "1.0-SNAPSHOT"

plugins {
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.8"
}

dependencies {
    implementation(rootProject.project("base"))
    paperweight.paperDevBundle("1.20.6-R0.1-SNAPSHOT")
}