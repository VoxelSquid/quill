package me.voxelsquid.quill.ai

import me.voxelsquid.quill.QuestIntelligence
import org.bukkit.configuration.file.YamlConfiguration
import me.voxelsquid.quill.QuestIntelligence.Companion.languageFile
import java.io.File

class AIFeature(private val plugin: QuestIntelligence) {
    private lateinit var provider: BaseAIProvider

    fun initialize() {
        val providerType = plugin.config.getString("core-settings.ai-provider") ?: "gemini"
        provider = when (providerType.toLowerCase()) {
            "gemini" -> GeminiProvider(plugin)
            "openai" -> OpenAIProvider(plugin)
            "cohere" -> CohereProvider(plugin)
            else -> throw IllegalArgumentException("Unknown AI provider: $providerType")
        }

        if (!languageFile.exists()) {
            plugin.saveResource("language.yml", false)
        }

        if (plugin.config.getBoolean("core-settings.automatic-configuration-translation")) {
            provider.generateTranslation()
        } else {
            plugin.logger.info("Automatic configuration translation is disabled. :(")
            plugin.language = YamlConfiguration.loadConfiguration(languageFile)
            provider.createGenerationRequest().generate("Gentlemen, you can't fight in here! This is the war room!", ping = true)
        }
    }

    fun getProvider(): AIProvider = provider
}

