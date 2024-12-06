group = "me.voxelsquid"
version = "1.0-SNAPSHOT"

plugins {
    id("io.papermc.paperweight.userdev") version "1.7.3"
}

dependencies {
    implementation(rootProject.project("base"))
    paperweight.paperDevBundle("1.21.3-R0.1-SNAPSHOT")
}