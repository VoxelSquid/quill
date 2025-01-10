import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml

group = "me.voxelsquid.quill"
version = "0.1.9-OPEN-ALPHA"
description = "AI-driven overhaul of villagers and a modern take on questing."

bukkitPluginYaml {
  main = "me.voxelsquid.quill.QuestIntelligence"
  load = BukkitPluginYaml.PluginLoadOrder.POSTWORLD
  authors.add("NoLogicWasHere")
  apiVersion = "1.20"
}

plugins {
  kotlin("jvm") version "2.0.20"
  id("io.papermc.paperweight.userdev") version "2.0.0-beta.11"
  id("xyz.jpenilla.run-paper") version "2.3.1" // Adds runServer and runMojangMappedServer tasks for testing
  id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.2.0" // Generates plugin.yml based on the Gradle config
  id("com.gradleup.shadow") version "8.3.3"
}

repositories {
  mavenCentral()
  maven("https://repo.aikar.co/content/groups/aikar/")
  maven("https://hub.spigotmc.org/nexus/content/groups/public/")
  maven("https://papermc.io/repo/repository/maven-public/")
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
  implementation("com.squareup.okhttp3:okhttp:4.1.0")
  implementation("co.aikar:acf-paper:0.5.1-SNAPSHOT")
  implementation("com.google.code.gson:gson:2.11.0")
}

tasks {

  compileJava {
    options.release = 21
  }

  javadoc {
    options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
  }

  reobfJar {
    outputJar = layout.buildDirectory.file("libs/quill-${project.version}.jar")
  }

  shadowJar {
    relocate("co.aikar.commands", "me.voxelsquid.quill.command")
    relocate("co.aikar.locales", "me.voxelsquid.quill.command.locales")
  }

}

tasks.assemble {
  dependsOn(tasks.reobfJar)
  dependsOn(tasks.shadowJar)
}

java {
  toolchain.languageVersion = JavaLanguageVersion.of(21)
}

kotlin {
  jvmToolchain(21)
}
