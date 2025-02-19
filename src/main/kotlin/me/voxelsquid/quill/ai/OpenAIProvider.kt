package me.voxelsquid.quill.ai

import me.voxelsquid.quill.QuestIntelligence

class OpenAIProvider(plugin: QuestIntelligence) : BaseAIProvider(plugin) {
    private val apiKey = plugin.config.getString("core-settings.openai-api-key")
    private val model = plugin.config.getString("core-settings.openai-model") ?: "gpt-3.5-turbo"
    override val url = "https://api.openai.com/v1/chat/completions"

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
            "messages": [{"role": "user", "content": "$prompt"}],
            "temperature": 0.7
        }
        """.trimIndent()
    }

    override fun cleanResponse(response: String): String {
        val jsonResponse = plugin.gson.fromJson(response, Map::class.java)
        return (jsonResponse["choices"] as List<Map<String, Any>>)
            .firstOrNull()
            ?.get("message")
            ?.let { (it as Map<String, String>)["content"] }
            ?: ""
    }
}

