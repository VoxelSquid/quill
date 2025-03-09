package me.voxelsquid.quill.base.config

import me.voxelsquid.quill.QuestIntelligence.Companion.pluginInstance
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ConfigurationManager(private val dataFolder: File = plugin.dataFolder) {

    private val replaceResources = ConfigurableValue(path = "replace-resources", defaultValue = true, comments = mutableListOf("By default, the configs responsible for game values are overwritten each time the server starts with the defaults.", "This is to make it easier for me to adjust the balance. But it can be switched off.")).get()

    lateinit var language:    YamlConfiguration
    lateinit var professions: YamlConfiguration
    lateinit var prices:      YamlConfiguration
    lateinit var prompts:     YamlConfiguration
    lateinit var races:       YamlConfiguration
    lateinit var skins:       YamlConfiguration

    init {
        this.saveResources()
        this.loadResources()
    }

    private fun saveResources() {
        resources.forEach { resource -> plugin.saveResource(resource, replaceResources) }
    }

    private fun loadResources() {
        language    = YamlConfiguration.loadConfiguration(File(dataFolder, "language.yml"))
        professions = YamlConfiguration.loadConfiguration(File(dataFolder, "professions.yml"))
        prices      = YamlConfiguration.loadConfiguration(File(dataFolder, "prices.yml"))
        prompts     = YamlConfiguration.loadConfiguration(File(dataFolder, "prompts.yml"))
        races       = YamlConfiguration.loadConfiguration(File(dataFolder, "races.yml"))
        skins       = YamlConfiguration.loadConfiguration(File(dataFolder, "skins.yml"))
    }

    private companion object {
        val plugin    = pluginInstance
        val resources = listOf("config.yml", "language.yml", "professions.yml", "prices.yml", "prompts.yml", "races.yml", "skins.yml")
    }

}