import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml

group = "me.voxelsquid.quill"
version = "0.1.4-OPEN-ALPHA"
description = "AI-driven overhaul of villagers and a modern take on questing."

bukkitPluginYaml {
    main = "me.voxelsquid.quill.QuestIntelligence"
    load = BukkitPluginYaml.PluginLoadOrder.POSTWORLD
    authors.add("NoLogicWasHere")
    apiVersion = "1.20"
}

plugins {
    kotlin("jvm") version "2.0.20"
    id("io.papermc.paperweight.userdev") version "1.7.3"
    id("xyz.jpenilla.run-paper") version "2.3.1" // Adds runServer and runMojangMappedServer tasks for testing
    id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.2.0" // Generates plugin.yml based on the Gradle config
    id("com.gradleup.shadow") version "8.3.3"
}

dependencies {
    paperweight.paperDevBundle("1.20.6-R0.1-SNAPSHOT")
    implementation(rootProject.project("base"))
    implementation(rootProject.project("1_20_R4"))
    implementation(rootProject.project("1_21_R1"))
    implementation(rootProject.project("1_21_R2"))
    implementation("com.squareup.okhttp3:okhttp:4.1.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
    implementation("com.github.IPVP-MC:canvas:2365c13da0")
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.aikar.co/content/groups/aikar/")
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")
        maven("https://papermc.io/repo/repository/maven-public/")
        maven("https://jitpack.io")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(kotlin("stdlib"))
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "21"
            javaParameters = true
        }
    }
}

tasks.assemble {
    dependsOn(tasks.reobfJar)
    dependsOn(tasks.shadowJar)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    group = "build"
    description = "Assembles the final plugin JAR by combining all subprojects."

    archiveFileName.set("${rootProject.name}-${project.version}.jar")
    destinationDirectory.set(file("${rootProject.projectDir}/build"))

    subprojects.forEach { subproject ->
        from(subproject.sourceSets.main.get().output)
    }

    exclude("META-INF/***")
}

tasks.register("buildPlugin") {
    group = "build"
    description = "Builds the final plugin JAR (fat JAR)."

    dependsOn("shadowJar")
}

tasks {

    compileJava {
        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release = 21
        options.compilerArgs.plusAssign("-parameters")
    }

    compileKotlin {
        compilerOptions.javaParameters = true
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }

    reobfJar {
        // This is an example of how you might change the output location for reobfJar. It's recommended not to do this
        // for a variety of reasons, however it's asked frequently enough that an example of how to do it is included here.
        outputJar = layout.buildDirectory.file("libs/quill-${project.version}.jar")
    }

    shadowJar {
        relocate("co.aikar.commands", "me.voxelsquid.quill.command")
        relocate("co.aikar.locales", "me.voxelsquid.quill.command.locales")
    }

}