package me.voxelsquid.quill.ai

import me.voxelsquid.quill.QuestIntelligence

class CohereProvider(plugin: QuestIntelligence) : BaseAIProvider(plugin) {
    private val apiKey = plugin.config.getString("core-settings.cohere-api-key")
    private val model = plugin.config.getString("core-settings.cohere-model") ?: "command"
    override val url = "https://api.cohere.ai/v1/generate"

    override fun createGenerationRequest(): GenerationRequest {
        return GenerationRequest(
            client,
            url,
            plugin,
            this::createJsonRequest,
            this::cleanResponse
        )
    }

    override fun createJsonRequest(prompt: String): String {
        return """
        {
            "model": "$model",
            "prompt": "$prompt",
            "max_tokens": 300,
            "temperature": 0.7,
            "k": 0,
            "stop_sequences": [],
            "return_likelihoods": "NONE"
        }
        """.trimIndent()
    }

    override fun cleanResponse(response: String): String {
        val jsonResponse = plugin.gson.fromJson(response, Map::class.java)
        return (jsonResponse["generations"] as List<Map<String, String>>)
            .firstOrNull()
            ?.get("text")
            ?.trim()
            ?: ""
    }
}

