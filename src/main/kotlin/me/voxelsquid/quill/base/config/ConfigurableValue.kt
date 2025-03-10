package me.voxelsquid.quill.base.config

import me.voxelsquid.quill.QuestIntelligence.Companion.pluginInstance
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ConfigurableValue<T>(
    private val path: String,
    private val defaultValue: T,
    private val comments: MutableList<String> = mutableListOf(),
    private val fileName: String = "config.yml"
) {
    private val configFile: File = File(pluginInstance.dataFolder, fileName)
    private val config: YamlConfiguration
        get() = YamlConfiguration.loadConfiguration(configFile).apply {
            options().parseComments(true)
        }

    init {
        register(this)
        ensureConfigInitialized()
    }

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    fun get(): T {
        val config = config
        if (!config.isSet(path)) return defaultValue

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

    companion object {
        private val instances = ConcurrentHashMap<String, ConfigurableValue<*>>()

        fun register(container: ConfigurableValue<*>) {
            val key = "${container.fileName}:${container.path}"
            instances[key] = container
        }

        @Synchronized
        fun reloadAll() {
            instances.values.forEach { it.reload() }
        }

        fun getByFile(fileName: String): List<ConfigurableValue<*>> {
            return instances.values.filter { it.fileName == fileName }
        }

        fun clearAll() {
            instances.clear()
        }

    }
}