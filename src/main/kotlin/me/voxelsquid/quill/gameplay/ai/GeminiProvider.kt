package me.voxelsquid.quill.gameplay.ai

import com.google.gson.JsonSyntaxException
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.gson
import me.voxelsquid.quill.QuestIntelligence.Companion.isChristmas
import me.voxelsquid.quill.gameplay.event.HumanoidPersonalDataGeneratedEvent
import me.voxelsquid.quill.gameplay.event.QuestGenerateEvent
import me.voxelsquid.quill.gameplay.event.SettlementNameGenerateEvent
import me.voxelsquid.quill.gameplay.event.UniqueItemGenerateEvent
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidCharacterType
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.getCharacterType
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.gender
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.professionLevelName
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.settlement
import me.voxelsquid.quill.gameplay.humanoid.race.HumanoidRaceManager.Companion.race
import me.voxelsquid.quill.gameplay.quest.QuestManager
import me.voxelsquid.quill.gameplay.quest.data.QuestType
import me.voxelsquid.quill.gameplay.quest.data.VillagerQuest
import me.voxelsquid.quill.gameplay.settlement.Settlement
import me.voxelsquid.quill.gameplay.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.gameplay.villager.ProfessionManager
import me.voxelsquid.quill.gameplay.villager.ProfessionManager.Companion.getUniqueItemAttributes
import me.voxelsquid.quill.gameplay.villager.ProfessionManager.Companion.getUniqueItemRarity
import me.voxelsquid.quill.gameplay.villager.ProfessionManager.Companion.isUniqueItem
import net.kyori.adventure.text.TextComponent
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Ageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.inventory.meta.PotionMeta
import java.io.File
import java.io.IOException
import java.io.StringReader
import kotlin.random.Random

class GeminiProvider(private val plugin: QuestIntelligence) {

    private val client = OkHttpClient.Builder().build()
    private val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=${plugin.controller.apiKey}"

    private val language    = plugin.controller.language
    private val translation = plugin.controller.translation
    private val namingStyle = plugin.controller.namingStyle

    private fun generateTranslation() {
        GenerationRequest(this, client, url, plugin).translation("Translate YAML file below to $language, keep the keys and special symbols (like §) and DO NOT translate placeholders. Wrap result as ```yaml```. \n```yaml\n${File(plugin.dataFolder, "language.yml").readText()}\n```")
    }

    init {
        if (translation) {
            this.generateTranslation()
        } else {
            plugin.logger.info("Generative translation is disabled. :(")
            GenerationRequest(this, client, url, plugin).generate("Gentlemen, you can't fight in here! This is the war room!", ping = true)
        }
    }

    data class SettlementInformation(val townName: String)
    fun generateSettlementName(settlement: Settlement) {

        val extraArguments = if (settlements.isNotEmpty()) "Avoid these names: $settlements." else ""

        val placeholders = mapOf(
            "settlementBiome"       to settlement.data.center.world.getBiome(settlement.data.center).key.value(),
            "language"              to language,
            "randomLetter"          to this.getRandomLetter(),
            "randomLetterLowerCase" to this.getRandomLetter().lowercase(),
            "extraArguments"        to extraArguments,
            "namingStyle"           to namingStyle
        )

        val prompt = placeholders.entries.fold(plugin.configManager.prompts.getString("settlement-name")!!) { acc, entry ->
            acc.replace("{${entry.key}}", entry.value)
        }

        GenerationRequest(this, client, url, plugin).generate(prompt) { cleanedJsonResponse ->
            plugin.server.scheduler.runTask(plugin) { _ ->
                plugin.server.pluginManager.callEvent(
                    SettlementNameGenerateEvent(
                        settlement,
                        gson.fromJson(cleanedJsonResponse, SettlementInformation::class.java)
                    )
                )
            }
        }

    }

    private val previousHumanoidNames = mutableListOf<String>()
    fun generatePersonalHumanoidData(entity: LivingEntity) {

        val extraArguments = "Avoid these names: $previousHumanoidNames."
        val race = entity.race?.name?.uppercase() ?: "dwarf"
        val raceDescription = entity.race?.description ?: ""

        val placeholders = mapOf(
            "villagerGender"      to entity.gender.toString(),
            "villagerRace"        to race,
            "villagerPersonality" to "${entity.getCharacterType()}",
            "villagerGrowthStage" to if (entity is Ageable && entity.isAdult) "ADULT" else "KID",
            "language"            to language,
            "randomLetter"        to this.getRandomLetter(),
            "extraArguments"      to extraArguments,
            "namingStyle"         to namingStyle,
            "raceDescription"     to raceDescription
        )

        val prompt = placeholders.entries.fold(plugin.configManager.prompts.getString("personal-villager-data")!!) { acc, entry ->
            acc.replace("{${entry.key}}", entry.value)
        }

        GenerationRequest(this, client, url, plugin).generate(prompt) { cleanedJsonResponse ->
            plugin.server.scheduler.runTask(plugin) { _ ->
                try {
                    val personalHumanoidData = gson.fromJson(cleanedJsonResponse, HumanoidManager.PersonalHumanoidData::class.java)
                    this.previousHumanoidNames.add(personalHumanoidData.villagerName)
                    plugin.server.pluginManager.callEvent(HumanoidPersonalDataGeneratedEvent(entity, personalHumanoidData))
                } catch (exception: JsonSyntaxException) {
                    plugin.logger.warning("JsonSyntaxException during generating PersonalHumanoidData! Please, report this to the developer!")
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

        val race = villager.race ?: run {
            plugin.logger.severe("Trying to generate an unique item for a non-existent race! Cancelling.")
            return
        }
        val raceName = race.name

        val placeholders = mutableMapOf(
            "villagerGender"          to villager.gender.toString(),
            "villagerName"            to villagerName,
            "villagerType"            to "${villager.villagerType}",
            "villagerRace"            to raceName,
            "villagerProfession"      to "${villager.profession}",
            "villagerProfessionLevel" to villager.professionLevelName,
            "language"                to language,
            "itemType"                to item.type.toString(),
            "extraItemAttributes"     to item.getUniqueItemAttributes(),
            "itemRarity"              to if (item.isUniqueItem()) item.getUniqueItemRarity().toString().lowercase() else ProfessionManager.UniqueItemRarity.COMMON.toString().lowercase(),
            "settlementName"          to settlementName,
            "settlementLevel"         to settlementLevel,
            "randomLetterLowerCase"   to this.getRandomLetter().lowercase(),
            "namingStyle"             to namingStyle
        )

        val promptTemplate = plugin.configManager.prompts.getString("unique-item-description")
            ?: throw IllegalArgumentException("Unique item description is not defined! Check prompts.yml!")

        val prompt = promptTemplate.replaceMap(placeholders)

        GenerationRequest(this, client, url, plugin).generate(prompt) { cleanedJson ->
            try {
                val data = gson.fromJson(cleanedJson, UniqueItemDescription::class.java)
                plugin.debug(cleanedJson)
                plugin.server.scheduler.runTask(plugin) { _ ->
                    plugin.server.pluginManager.callEvent(UniqueItemGenerateEvent(villager, item, data))
                }
            } catch (ignored: Exception) {}
        }
    }

    fun generateQuestData(questManager: QuestManager, villager: Villager, quest: VillagerQuest.Builder) {

        var extraArguments = if (plugin.config.getBoolean("core-settings.swearing")) when (villager.getCharacterType()) {
            HumanoidCharacterType.ANGRY, HumanoidCharacterType.DRUNKARD -> "'20% of words are swearing'"
            else -> ""
        } else ""

        if (isChristmas()) {
            extraArguments += "'It's Christmas!'"
        }

        val questRequirements = plugin.configManager.prompts.getString(
            "${quest.questType.promptConfigPath}.quest-requirements"
        ) ?: kotlin.run {
            plugin.logger.warning("Quest requirements for quest type ${quest.questType} is not defined!")
            return
        }

        val villagerName = villager.customName()?.let { (it as TextComponent).content() } ?: "unknown"

        val race = villager.race ?: run {
            plugin.logger.severe("Trying to generate a quest for a non-existent race! Cancelling.")
            return
        }

        val raceName = race.name
        val raceDescription = race.description

        val settlementName  = villager.settlement?.data?.settlementName ?: ""
        val settlementLevel = villager.settlement?.size().toString()

        val placeholders = mutableMapOf(
            "villagerGender"          to villager.gender.toString(),
            "villagerName"            to villagerName,
            "villagerRace"            to raceName,
            "villagerProfession"      to "${villager.profession}",
            "villagerPersonality"     to "${villager.getCharacterType()}",
            "villagerProfessionLevel" to villager.professionLevelName,
            "questItem"               to if (quest.questType == QuestType.OMINOUS_BANNER) "ominous banner" else quest.questItem.type.name.replace('_', ' ').lowercase(),
            "questItemAmount"         to quest.questItem.amount.toString(),
            "rewardItem"              to quest.rewardItem.type.name.replace('_', ' ').lowercase(),
            "language"                to language,
            "treasureDescription"     to questManager.getTreasureItemDescription(quest.questItem),
            "settlementName"          to settlementName,
            "settlementLevel"         to settlementLevel,
            "raceDescription"         to raceDescription,
            "extraArguments"          to "[$extraArguments]"
        )

        (quest.questItem.itemMeta as? PotionMeta)?.basePotionType?.let {
            placeholders["potionType"] = it.name.replace("_", " ")
        }

        (quest.questItem.itemMeta as? EnchantmentStorageMeta)?.let {
            placeholders["enchantmentType"] = it.storedEnchants.keys.first().key.value().replace("_", " ")
        }

        val promptTemplate = plugin.configManager.prompts.getString("basic-task-description")
            ?: throw IllegalArgumentException("Basic task description is not defined! Check prompts.yml!")

        val prompt = promptTemplate.replaceMap(mapOf("questRequirements" to questRequirements)).replaceMap(placeholders)

        GenerationRequest(this, client, url, plugin).generate(prompt) { cleanedQuestJson ->
            try {
                val questInfo = gson.fromJson(cleanedQuestJson, VillagerQuest.QuestInfo::class.java)
                questInfo.questName = questInfo.questName.replace("*", "")
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
                    plugin.server.scheduler.runTaskLater(plugin, { _ ->
                        geminiProvider.generateTranslation()
                    }, 20 * 15)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        response.body?.let { responseBody ->
                            try {

                                val responseData = gson.fromJson(responseBody.string(), ResponseData::class.java)
                                val responseText = responseData?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                                val cleanedData = responseText?.let {
                                    findYaml(it)
                                    cleanQuestYaml(it)
                                }

                                cleanedData?.let {
                                    plugin.logger.info("Connection with the AI has been established successfully!")
                                    plugin.logger.info("QuestIntelligence uses automatic configuration translation.")
                                    // plugin.logger.warning(responseText)
                                    plugin.configManager.language = YamlConfiguration.loadConfiguration(StringReader(it))
                                }

                                responseBody.close()
                            } catch (e: Exception) {
                                val responseData = gson.fromJson(responseBody.string(), ResponseData::class.java)
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
                    val responseData = gson.fromJson(data, ResponseData::class.java)
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
            if (plugin.debug) {
                plugin.debug("Error during request waiting!")
                e.printStackTrace()
            }
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
