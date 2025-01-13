package me.voxelsquid.quill.ai

import com.google.gson.JsonSyntaxException
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.isChristmas
import me.voxelsquid.quill.QuestIntelligence.Companion.languageFile
import me.voxelsquid.quill.event.*
import me.voxelsquid.quill.illager.IllagerManager.Companion.illagerCommonData
import me.voxelsquid.quill.quest.QuestManager
import me.voxelsquid.quill.quest.data.QuestType
import me.voxelsquid.quill.quest.data.VillagerQuest
import me.voxelsquid.quill.settlement.Settlement
import me.voxelsquid.quill.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.villager.CharacterType
import me.voxelsquid.quill.villager.ProfessionManager
import me.voxelsquid.quill.villager.ProfessionManager.Companion.getUniqueItemAttributes
import me.voxelsquid.quill.villager.ProfessionManager.Companion.getUniqueItemRarity
import me.voxelsquid.quill.villager.ProfessionManager.Companion.isUniqueItem
import me.voxelsquid.quill.villager.VillagerManager
import me.voxelsquid.quill.villager.VillagerManager.Companion.character
import me.voxelsquid.quill.villager.VillagerManager.Companion.professionLevelName
import me.voxelsquid.quill.villager.VillagerManager.Companion.settlement
import net.kyori.adventure.text.TextComponent
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.inventory.meta.PotionMeta
import java.io.IOException
import java.io.StringReader
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.random.Random


class GeminiProvider(private val plugin: QuestIntelligence) {

    private val proxyHost = plugin.config.getString("core-settings.proxy.host")!!
    private val proxyPort = plugin.config.getInt("core-settings.proxy.port")
    private val proxyType = Proxy.Type.valueOf(plugin.config.getString("core-settings.proxy.type")!!)
    private val proxy     = Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort))
    private val username  = plugin.config.getString("core-settings.proxy.username")!!
    private val password  = plugin.config.getString("core-settings.proxy.password")!!

    private var proxyAuthenticator: Authenticator = object : Authenticator {
        @Throws(IOException::class)
        override fun authenticate(route: Route?, response: Response): Request {
            val credential = Credentials.basic(username, password)
            return response.request.newBuilder()
                .addHeader("Proxy-Authorization", credential)
                .build()
        }
    }

    private val client = OkHttpClient.Builder().apply {
        if (plugin.config.getString("core-settings.proxy.host") != "PROXY_HOST") {
            plugin.logger.info("Proxy usage in config.yml detected. When sending requests, a proxy will be used.")
            proxy(proxy).proxyAuthenticator(proxyAuthenticator)
        }
    }.build()

    private val previousNames = mutableListOf<String>()

    private val key = plugin.config.getString("core-settings.api-key")
    private val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=$key"

    private fun generateTranslation() = GenerationRequest(this, client, url, plugin).translation("Translate YAML file below to ${plugin.config.getString("core-settings.language")} language, keep the keys and special symbols (like §) and DO NOT translate placeholders. Wrap result as ```yaml```. \n```yaml\n${languageFile.readText()}\n```")

    init {
        if (!languageFile.exists())
            plugin.saveResource("language.yml", false)
        if (plugin.config.getBoolean("core-settings.automatic-configuration-translation")) {
            this.generateTranslation()
        } else {
            plugin.logger.info("Automatic configuration translation is disabled. :(")
            plugin.language = YamlConfiguration.loadConfiguration(languageFile)
            GenerationRequest(this, client, url, plugin).generate("Gentlemen, you can't fight in here! This is the war room!", ping = true)
        }

        // Late generation requests
        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, { _ ->
            if (illagerCommonData == null) {
                this.generateCommonIllagerData()
            }
        }, 100, 400)
    }


    data class SettlementInformation(val townName: String)
    fun generateSettlementName(settlement: Settlement) {

        val extraArguments = if (settlements.isNotEmpty()) "Avoid these names: $settlements." else ""

        val placeholders = mapOf(
            "settlementBiome"       to settlement.data.center.world.getBiome(settlement.data.center).key.value(),
            "language"              to "${plugin.config.getString("core-settings.language")}",
            "randomLetter"          to this.getRandomLetter(),
            "randomLetterLowerCase" to this.getRandomLetter().lowercase(),
            "extraArguments"        to extraArguments,
            "namingStyle"           to (plugin.config.getString("core-settings.naming-style") ?: "Fantasy")
        )

        val prompt = placeholders.entries.fold(plugin.configurationClip.promptsConfig.getString("settlement-name")!!) { acc, entry ->
            acc.replace("{${entry.key}}", entry.value)
        }

        GenerationRequest(this, client, url, plugin).generate(prompt) { cleanedJsonResponse ->
            plugin.server.scheduler.runTask(plugin) { _ ->
                plugin.server.pluginManager.callEvent(
                    SettlementNameGenerateEvent(
                        settlement,
                        plugin.gson.fromJson(cleanedJsonResponse, SettlementInformation::class.java)
                    )
                )
            }
        }

    }

    fun generatePersonalVillagerData(villager: Villager) {

        val extraArguments = "Avoid these names: $previousNames."

        val placeholders = mapOf(
            "villagerType"        to "${villager.villagerType}",
            "villagerPersonality" to "${villager.character}",
            "villagerGrowthStage" to if (villager.isAdult) "ADULT" else "KID",
            "language"            to "${plugin.config.getString("core-settings.language")}",
            "randomLetter"        to this.getRandomLetter(),
            "extraArguments"      to extraArguments,
            "namingStyle"         to (plugin.config.getString("core-settings.naming-style") ?: "Fantasy")
        )

        val prompt = placeholders.entries.fold(plugin.configurationClip.promptsConfig.getString("personal-villager-data")!!) { acc, entry ->
            acc.replace("{${entry.key}}", entry.value)
        }

        GenerationRequest(this, client, url, plugin).generate(prompt) { cleanedJsonResponse ->
            plugin.server.scheduler.runTask(plugin) { _ ->
                try {
                    val pvd = plugin.gson.fromJson(cleanedJsonResponse, VillagerManager.PersonalVillagerData::class.java)
                    this.previousNames.add(pvd.villagerName)
                    plugin.server.pluginManager.callEvent(VillagerDataGenerateEvent(villager, pvd))
                } catch (exception: JsonSyntaxException) {
                    plugin.logger.warning("JsonSyntaxException during generating PDV! Please, report this to the developer!")
                    plugin.logger.warning(cleanedJsonResponse)
                }
            }
        }
    }

    private fun generateCommonIllagerData() {

        val placeholders = mapOf(
            "namingStyle" to (plugin.config.getString("core-settings.naming-style") ?: "Fantasy")
        )

        val prompt = placeholders.entries.fold(plugin.configurationClip.promptsConfig.getString("common-illager-data") + "Rules: '15% of words are swearing'") { acc, entry ->
            acc.replace("{${entry.key}}", entry.value)
        }

        GenerationRequest(this, client, url, plugin).generate(prompt) { cleanedJsonResponse ->
            plugin.server.scheduler.runTask(plugin) { _ ->
                try {
                    val icd = plugin.gson.fromJson(cleanedJsonResponse, IllagerCommonData::class.java)
                    plugin.server.pluginManager.callEvent(IllagerCommonDataGenerateEvent(icd))
                } catch (exception: JsonSyntaxException) {
                    plugin.logger.warning("JsonSyntaxException during generating CID! Please, report this to the developer!")
                    plugin.logger.warning(cleanedJsonResponse)
                }
            }
        }
    }

    data class UniqueItemDescription(val itemDescription: String, val itemName: String)
    fun generateUniqueItemDescription(villager: Villager, item: ItemStack) {

        val villagerName = villager.customName()?.let { (it as TextComponent).content() } ?: "unknown"
        val settlementName = villager.settlement?.data?.settlementName ?: "no settlement"
        val settlementLevel = villager.settlement?.size().toString()

        val placeholders = mutableMapOf(
            "villagerName"            to villagerName,
            "villagerType"            to "${villager.villagerType}",
            "villagerProfession"      to "${villager.profession}",
            "villagerProfessionLevel" to villager.professionLevelName,
            "language"                to plugin.config.getString("core-settings.language")!!,
            "itemType"                to item.type.toString(),
            "extraItemAttributes"     to item.getUniqueItemAttributes(),
            "itemRarity"              to if (item.isUniqueItem()) item.getUniqueItemRarity().toString().lowercase() else ProfessionManager.UniqueItemRarity.COMMON.toString().lowercase(),
            "settlementName"          to settlementName,
            "settlementLevel"         to settlementLevel,
            "randomLetterLowerCase"   to this.getRandomLetter().lowercase(),
            "namingStyle"             to (plugin.config.getString("core-settings.naming-style") ?: "Fantasy")
        )

        val promptTemplate = plugin.configurationClip.promptsConfig.getString("unique-item-description")
            ?: throw IllegalArgumentException("Unique item description is not defined! Check prompts.yml!")

        val prompt = promptTemplate.replaceMap(placeholders)

        GenerationRequest(this, client, url, plugin).generate(prompt) { cleanedJson ->
            try {
                val data = plugin.gson.fromJson(cleanedJson, UniqueItemDescription::class.java)
                plugin.debug(cleanedJson)
                plugin.server.scheduler.runTask(plugin) { _ ->
                    plugin.server.pluginManager.callEvent(UniqueItemGenerateEvent(villager, item, data))
                }
            } catch (ignored: Exception) {}
        }
    }

    fun generateQuestData(questManager: QuestManager, villager: Villager, quest: VillagerQuest.Builder) {

        var extraArguments = when (villager.character) {
            CharacterType.ANGRY, CharacterType.DRUNKARD -> "'20% of words are swearing'"
            else -> ""
        }

        if (isChristmas()) {
            extraArguments += "'It's Christmas!'"
        }

        val questRequirements = plugin.configurationClip.promptsConfig.getString(
            "${quest.questType.promptConfigPath}.quest-requirements"
        ) ?: kotlin.run {
            plugin.logger.warning("Quest requirements for quest type ${quest.questType} is not defined!")
            return
        }

        val villagerName = villager.customName()?.let { (it as TextComponent).content() } ?: "unknown"
        val settlementName = villager.settlement?.data?.settlementName ?: "no settlement"
        val settlementLevel = villager.settlement?.size().toString()

        val placeholders = mutableMapOf(
            "villagerName"            to villagerName,
            "villagerType"            to "${villager.villagerType}",
            "villagerProfession"      to "${villager.profession}",
            "villagerPersonality"     to "${villager.character}",
            "villagerProfessionLevel" to villager.professionLevelName,
            "questItem"               to if (quest.questType == QuestType.OMINOUS_BANNER) "ominous banner" else quest.questItem.type.name.replace('_', ' ').lowercase(),
            "questItemAmount"         to quest.questItem.amount.toString(),
            "rewardItem"              to quest.rewardItem.type.name.replace('_', ' ').lowercase(),
            "language"                to plugin.config.getString("core-settings.language")!!,
            "treasureDescription"     to questManager.getTreasureItemDescription(quest.questItem),
            "settlementName"          to settlementName,
            "settlementLevel"         to settlementLevel,
            "extraArguments"          to "[$extraArguments]"
        )

        (quest.questItem.itemMeta as? PotionMeta)?.basePotionType?.let {
            placeholders["potionType"] = it.name.replace("_", " ")
        }

        (quest.questItem.itemMeta as? EnchantmentStorageMeta)?.let {
            placeholders["enchantmentType"] = it.storedEnchants.keys.first().key.value().replace("_", " ")
        }

        val promptTemplate = plugin.configurationClip.promptsConfig.getString("basic-task-description")
            ?: throw IllegalArgumentException("Basic task description is not defined! Check prompts.yml!")

        val prompt = promptTemplate.replaceMap(mapOf("questRequirements" to questRequirements)).replaceMap(placeholders)

        GenerationRequest(this, client, url, plugin).generate(prompt) { cleanedQuestJson ->
            try {
                val questInfo = plugin.gson.fromJson(cleanedQuestJson, VillagerQuest.QuestInfo::class.java)
                questInfo.twoWordsDescription = questInfo.twoWordsDescription.replace("*", "")
                quest.setQuestInfo(questInfo)
                plugin.server.scheduler.runTask(plugin) { _ ->
                    plugin.server.pluginManager.callEvent(QuestGenerateEvent(villager, quest.build()))
                }
            } catch (ignored: Exception) {}
        }
    }

    private fun String.replaceMap(replacements: Map<String, String>): String {
        var result = this
        for ((key, value) in replacements) {
            result = result.replace("{${key}}", value)
        }
        return result
    }

    private fun getRandomLetter(): String {
        val letters = 'A'..'Z'
        val randomIndex = Random.nextInt(letters.count())
        return letters.elementAt(randomIndex).toString()
    }

    private class GenerationRequest(
        private val geminiProvider: GeminiProvider,
        private val client: OkHttpClient,
        private val url: String,
        private val plugin: QuestIntelligence,
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
                        geminiProvider.generateTranslation()
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
                                    // plugin.logger.warning(responseText)
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

        private fun createJsonRequest(prompt: String): String {
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
                        ?.let { cleanQuestJson(it) }
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
            plugin.logger.info("ERROR DURING REQUEST WAITING!!! AAAAAAAAAAAAAA!")
            e.printStackTrace()
        }

        private fun cleanQuestJson(questJson: String): String =
                questJson.replace("```json\n", "")
                .replace("```", "")
                .replace("\\n", "\n")
                .replace(Regex("\\s{2,}"), " ") // Избавляемся от богомерзких двойных пробелов
                .replace(Regex("\\.{3}(?=\\S)"), "..." + " ") // Исправляем отсутствие пробела после троеточия
                .replace("…", "...") // Заменяем отвратительное троеточие на нормальное

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

}

data class ResponseData(val candidates: List<Candidate>, val usageMetadata: UsageMetadata)
data class Candidate(val content: Content, val finishReason: String, val index: Int, val safetyRatings: List<SafetyRating>)
data class Content(val parts: List<Part>, val role: String)
data class Part(val text: String)
data class SafetyRating(val category: String, val probability: String)
data class UsageMetadata(val promptTokenCount: Int, val candidatesTokenCount: Int, val totalTokenCount: Int)
