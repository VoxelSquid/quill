package me.voxelsquid.quill.util

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ConfigContainer<T>(
    private val dataFolder: File,
    private val path: String,
    private val defaultValue: T,
    private val comments: List<String>,
    private val fileName: String = "ssssss.yml"
) {
    private val configFile: File = File(dataFolder, fileName)
    private val config: YamlConfiguration
        get() = YamlConfiguration.loadConfiguration(configFile).apply {
            options().parseComments(true)
        }

    init {
        register(this)
        ensureConfigInitialized()
    }

    /**
     * Получает текущее значение из конфига или дефолтное
     */
    @Suppress("UNCHECKED_CAST")
    fun get(): T {
        val config = config
        if (!config.isSet(path)) return defaultValue

        val rawValue = config.get(path)
        return when {
            defaultValue is Enum<*> -> {
                try {
                    // Явно указываем, что defaultValue не null, чтобы избежать nullable-типа
                    val enumClass = defaultValue!!::class.java as Class<out Enum<*>>
                    java.lang.Enum.valueOf(enumClass, rawValue.toString().uppercase()) as T
                } catch (e: IllegalArgumentException) {
                    // dataFolder.logger.warning("Invalid enum value '$rawValue' at $path in $fileName, using default: $defaultValue")
                    defaultValue
                }
            }
            else -> rawValue as T
        }
    }

    // Оставшиеся методы остаются без изменений, но я включу их для полной картины
    fun set(value: T, overrideComments: Boolean = false) {
        val config = config
        val storeValue = if (value is Enum<*>) value.name else value
        config.set(path, storeValue)
        saveConfig(config)
    }

    fun reload() {
        ensureConfigInitialized()
    }

    private fun ensureConfigInitialized() {
        if (!configFile.exists()) {
            dataFolder.mkdirs()
            configFile.createNewFile()
            initializeDefaultConfig()
        } else {
            val config = config
            if (!config.isSet(path)) {
                val storeValue = if (defaultValue is Enum<*>) defaultValue.name else defaultValue
                config.set(path, storeValue)
                saveConfig(config)
            }
        }
    }

    private fun initializeDefaultConfig() {
        val config = config
        val storeValue = if (defaultValue is Enum<*>) defaultValue.name else defaultValue
        config.set(path, storeValue)
        saveConfig(config)
    }

    private fun saveConfig(config: YamlConfiguration) {
        try {
            config.save(configFile)
            // dataFolder.logger.info("Successfully saved $fileName")
        } catch (e: Exception) {
            // dataFolder.logger.severe("Failed to save config: $fileName")
            e.printStackTrace()
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, ConfigContainer<*>>()

        fun register(container: ConfigContainer<*>) {
            val key = "${container.fileName}:${container.path}"
            instances[key] = container
        }

        @Synchronized
        fun reloadAll() {
            instances.values.forEach { it.reload() }
        }

        fun getByFile(fileName: String): List<ConfigContainer<*>> {
            return instances.values.filter { it.fileName == fileName }
        }

        fun clearAll() {
            instances.clear()
        }
    }
}