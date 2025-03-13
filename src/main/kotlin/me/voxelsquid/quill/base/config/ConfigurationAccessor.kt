package me.voxelsquid.quill.base.config

import me.voxelsquid.quill.QuestIntelligence.Companion.pluginInstance
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class ConfigurationAccessor<T>(
    private val path: String,
    private val defaultValue: T,
    private val comments: MutableList<String> = mutableListOf(),
    fileName: String = "config.yml"
) {

    private val configFile = File(pluginInstance.dataFolder, fileName)
    private var config     = if (fileName != "language.yml") YamlConfiguration.loadConfiguration(configFile) else pluginInstance.configManager.language

    init {
        ensureConfigInitialized()
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    fun get(): T {

        val config = config
        if (!config.isSet(path)) {
            return defaultValue
        }

        val rawValue = config.get(path)
        return when {
            defaultValue is Enum<*> -> {
                try {
                    val enumClass = defaultValue!!::class.java as Class<out Enum<*>>
                    java.lang.Enum.valueOf(enumClass, rawValue.toString().uppercase()) as T
                } catch (e: IllegalArgumentException) {
                    defaultValue
                }
            }
            rawValue is String -> {
                return ChatColor.translateAlternateColorCodes('&', rawValue) as T
            }
            else -> rawValue as T
        }
    }

    fun reload() {
        ensureConfigInitialized()
    }

    private fun ensureConfigInitialized() {
        if (!configFile.exists()) {
            pluginInstance.dataFolder.mkdirs()
            configFile.createNewFile()
            initializeDefaultConfig()
        } else {
            val config = config
            if (!config.isSet(path)) {
                val storeValue = if (defaultValue is Enum<*>) defaultValue.name else defaultValue
                config.set(path, storeValue)
                config.setComments(path, comments)
                saveConfig(config)
            }
        }
    }

    private fun initializeDefaultConfig() {
        val config = config
        val storeValue = if (defaultValue is Enum<*>) defaultValue.name else defaultValue
        config.set(path, storeValue)
        config.setComments(path, comments)
        saveConfig(config)
    }

    private fun saveConfig(config: YamlConfiguration) {
        try {
            config.save(configFile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}