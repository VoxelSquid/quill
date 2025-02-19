package me.voxelsquid.quill.ai

import me.voxelsquid.quill.QuestIntelligence

class GeminiProvider(plugin: QuestIntelligence) : BaseAIProvider(plugin) {
    private val key = plugin.config.getString("core-settings.api-key")
    private val model: String = plugin.config.getString("core-settings.model") ?: "gemini-2.0-flash-exp"
    override val url: String = "https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=$key"

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
        return """{
            "contents": [{
                "parts": [{
                    "text": "$prompt"
                }]
            }],
            "safetySettings": [{
                "category": "7",
                "threshold": "4"
            }]
        }""".trimIndent()
    }

    override fun cleanResponse(response: String): String {
        return response.replace("```json\n", "")
            .replace("```", "")
            .replace("\\n", "\n")
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\.{3}(?=\\S)"), "..." + " ")
            .replace("…", "...")
    }
}

