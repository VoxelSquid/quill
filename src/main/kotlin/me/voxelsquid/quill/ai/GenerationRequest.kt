package me.voxelsquid.quill.ai

import me.voxelsquid.quill.QuestIntelligence
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.configuration.file.YamlConfiguration
import java.io.IOException
import java.io.StringReader

class GenerationRequest(
    private val client: OkHttpClient,
    private val url: String,
    private val plugin: QuestIntelligence,
    private val createJsonRequest: (String) -> String,
    private val cleanResponse: (String) -> String
) {
    fun translation(prompt: String) {
        val requestBody = createJsonRequest(prompt.replace("\"", "\\\"")).toRequestBody("application/json".toMediaTypeOrNull())
        val request = createRequest(requestBody)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                plugin.logger.warning("It seems that there was some error while translating the language file.")
                plugin.logger.warning("QuestIntelligence will use the default translation but will try to translate the language file again in 15 seconds.")
                plugin.logger.warning("If that doesn't work, there's probably an AI problem. Report it to the developer.")
                plugin.logger.warning("You can also turn off generative translation in config.yml, look for 'automatic-configuration-translation'.")
                plugin.server.scheduler.runTaskLater(plugin, { _ ->
                    plugin.aiFeature.getProvider().generateTranslation()
                }, 20 * 15)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.let { responseBody ->
                        try {
                            val responseData = plugin.gson.fromJson(responseBody.string(), ResponseData::class.java)
                            val responseText = responseData?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                            val cleanedData = responseText?.let {
                                findYaml(it)
                                cleanQuestYaml(it)
                            }

                            cleanedData?.let {
                                plugin.logger.info("Connection with the AI has been established successfully!")
                                plugin.logger.info("QuestIntelligence uses automatic configuration translation.")
                                plugin.language = YamlConfiguration.loadConfiguration(StringReader(it))
                            }

                            responseBody.close()
                        } catch (e: Exception) {
                            val responseData = plugin.gson.fromJson(responseBody.string(), ResponseData::class.java)
                            val responseText = responseData?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            plugin.logger.warning("$responseText")
                        }
                    }
                    response.close()
                } else {
                    handleFailedResponse(response)
                }
            }
        })
    }

    fun generate(prompt: String, ping: Boolean = false, onSuccess: (String) -> Unit = {}) {
        val requestBody = createJsonRequest(prompt).toRequestBody("application/json".toMediaTypeOrNull())
        val request = createRequest(requestBody)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handleError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.use { handleSuccessfulResponse(it, onSuccess) }
                    if (ping) {
                        plugin.logger.info("Connection with the AI has been established successfully!")
                    }
                } else {
                    handleFailedResponse(response)
                }
            }
        })
    }

    private fun createRequest(body: RequestBody): Request =
        Request.Builder()
            .url(url)
            .post(body)
            .build()

    private fun handleSuccessfulResponse(response: Response, onSuccess: (String) -> Unit) {
        response.body?.let { responseBody ->
            val data = responseBody.string()
            try {
                val responseData = plugin.gson.fromJson(data, ResponseData::class.java)
                val responseText = responseData?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                val cleanedData = responseText
                    ?.let { findJson(it) }
                    ?.let { cleanResponse(it) }
                cleanedData?.let(onSuccess)
            } catch (e: Exception) {
                plugin.logger.severe("Error during quest response parsing! Report it to the developer, please!")
                e.printStackTrace()
                plugin.logger.severe(data)
            }
        }
    }

    private fun handleFailedResponse(response: Response) {
        plugin.logger.warning("The request was sent, but an error was returned as a response! If you can't solve the problem yourself, look for help in the Discord server!")
        plugin.logger.warning("Request failed: ${response.code}, ${response.body?.string()}")
    }

    private fun handleError(e: Throwable) {
        if (plugin.debug) {
            plugin.debug("Error during request waiting!")
            plugin.logger.severe(e.stackTrace.joinToString("\n"))
        }
    }

    private fun findJson(response: String): String? {
        val regex = """\{[^{}]*}""".toRegex()
        return regex.find(response)?.value
    }

    private fun findYaml(response: String): String? {
        val regex = """```([\s\S]*?)```""".toRegex()
        return regex.find(response)?.groups?.get(1)?.value?.trim()
    }

    private fun cleanQuestYaml(questJson: String): String =
        questJson.replace("```yaml\n", "").replace("```", "")

}


data class ResponseData(val candidates: List<Candidate>, val usageMetadata: UsageMetadata)
data class Candidate(val content: Content, val finishReason: String, val index: Int, val safetyRatings: List<SafetyRating>)
data class Content(val parts: List<Part>, val role: String)
data class Part(val text: String)
data class SafetyRating(val category: String, val probability: String)
data class UsageMetadata(val promptTokenCount: Int, val candidatesTokenCount: Int, val totalTokenCount: Int)