package me.voxelsquid.quill.ai

import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.settlement.Settlement
import me.voxelsquid.quill.event.*
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidCharacterType
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidEntityExtension.getCharacterType
import me.voxelsquid.quill.QuestIntelligence.Companion.languageFile
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidController.PersonalHumanoidData
import me.voxelsquid.quill.humanoid.race.HumanoidRaceManager.Companion.race
import me.voxelsquid.quill.quest.QuestManager
import me.voxelsquid.quill.quest.data.QuestType
import me.voxelsquid.quill.quest.data.VillagerQuest
import me.voxelsquid.quill.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.villager.ProfessionManager
import me.voxelsquid.quill.villager.ProfessionManager.Companion.getUniqueItemAttributes
import me.voxelsquid.quill.villager.ProfessionManager.Companion.getUniqueItemRarity
import me.voxelsquid.quill.villager.ProfessionManager.Companion.isUniqueItem
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.professionLevelName
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.settlement
import net.kyori.adventure.text.TextComponent
import org.bukkit.entity.Ageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.configuration.file.YamlConfiguration
import okhttp3.*
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.random.Random
import com.google.gson.JsonSyntaxException

abstract class BaseAIProvider(protected val plugin: QuestIntelligence) : AIProvider {
    protected val client: OkHttpClient
    protected val previousNames = mutableListOf<String>()

    init {
        val builder = OkHttpClient.Builder()
        if (plugin.config.getString("core-settings.proxy.host") != "PROXY_HOST") {
            val proxyHost = plugin.config.getString("core-settings.proxy.host")!!
            val proxyPort = plugin.config.getInt("core-settings.proxy.port")
            val proxyType = Proxy.Type.valueOf(plugin.config.getString("core-settings.proxy.type")!!)
            val proxy = Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort))
            val username = plugin.config.getString("core-settings.proxy.username")!!
            val password = plugin.config.getString("core-settings.proxy.password")!!

            val proxyAuthenticator = object : Authenticator {
                @Throws(IOException::class)
                override fun authenticate(route: Route?, response: Response): Request {
                    val credential = Credentials.basic(username, password)
                    return response.request.newBuilder()
                        .addHeader("Proxy-Authorization", credential)
                        .build()
                }
            }

            plugin.logger.info("Proxy usage in config.yml detected. When sending requests, a proxy will be used.")
            builder.proxy(proxy).proxyAuthenticator(proxyAuthenticator)
        }
        client = builder.build()
        if (!languageFile.exists())
            plugin.saveResource("language.yml", false)
        if (plugin.config.getBoolean("core-settings.automatic-configuration-translation")) {
            this.generateTranslation()
        } else {
            plugin.logger.info("Automatic configuration translation is disabled. :(")
            plugin.language = YamlConfiguration.loadConfiguration(languageFile)
        }
    }

    protected abstract val url: String

    public abstract fun createGenerationRequest(): GenerationRequest

    abstract override fun createJsonRequest(prompt: String): String

    protected abstract fun cleanResponse(response: String): String

    override fun generateTranslation() {
        val prompt = "Translate YAML file below to ${plugin.config.getString("core-settings.language")} language, keep the keys and special symbols (like §) and DO NOT translate placeholders. Wrap result as ```yaml```. \n```yaml\n${languageFile.readText()}\n```"
        createGenerationRequest().translation(prompt)
    }

    override fun generateSettlementName(settlement: Settlement) {
        val extraArguments = if (settlements.isNotEmpty()) "Avoid these names: $settlements." else ""

        val placeholders = mapOf(
            "settlementBiome"       to settlement.data.center.world.getBiome(settlement.data.center).key.value(),
            "language"              to "${plugin.config.getString("core-settings.language")}",
            "randomLetter"          to getRandomLetter(),
            "randomLetterLowerCase" to getRandomLetter().lowercase(),
            "extraArguments"        to extraArguments,
            "namingStyle"           to (plugin.config.getString("core-settings.naming-style") ?: "Fantasy")
        )

        val prompt = placeholders.entries.fold(plugin.configurationClip.promptsConfig.getString("settlement-name")!!) { acc, entry ->
            acc.replace("{${entry.key}}", entry.value)
        }

        createGenerationRequest().generate(prompt) { cleanedJsonResponse ->
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

    override fun generatePersonalHumanoidData(entity: LivingEntity) {
        val extraArguments = "Avoid these names: $previousNames."
        val race = entity.race?.name?.uppercase() ?: "dwarf"
        val raceDescription = entity.race?.description ?: ""

        val placeholders = mapOf(
            "villagerRace"        to race,
            "villagerPersonality" to "${entity.getCharacterType()}",
            "villagerGrowthStage" to if (entity is Ageable && entity.isAdult) "ADULT" else "KID",
            "language"            to "${plugin.config.getString("core-settings.language")}",
            "randomLetter"        to getRandomLetter(),
            "extraArguments"      to extraArguments,
            "namingStyle"         to (plugin.config.getString("core-settings.naming-style") ?: "Fantasy"),
            "raceDescription"     to raceDescription
        )

        val prompt = placeholders.entries.fold(plugin.configurationClip.promptsConfig.getString("personal-villager-data")!!) { acc, entry ->
            acc.replace("{${entry.key}}", entry.value)
        }

        createGenerationRequest().generate(prompt) { cleanedJsonResponse ->
            plugin.server.scheduler.runTask(plugin) { _ ->
                try {
                    val personalHumanoidData = plugin.gson.fromJson(cleanedJsonResponse, PersonalHumanoidData::class.java)
                    previousNames.add(personalHumanoidData.villagerName)
                    plugin.server.pluginManager.callEvent(HumanoidPersonalDataGeneratedEvent(entity, personalHumanoidData))
                } catch (exception: JsonSyntaxException) {
                    plugin.logger.warning("JsonSyntaxException during generating PersonalHumanoidData! Please, report this to the developer!")
                    plugin.logger.warning(cleanedJsonResponse)
                }
            }
        }
    }

    data class UniqueItemDescription(val itemDescription: String, val itemName: String)

    override fun generateUniqueItemDescription(villager: Villager, item: ItemStack) {
        val villagerName = villager.customName()?.let { (it as TextComponent).content() } ?: "unknown"
        val settlementName = villager.settlement?.data?.settlementName ?: "no settlement"
        val settlementLevel = villager.settlement?.size().toString()

        val race = villager.race ?: run {
            plugin.logger.severe("Trying to generate an unique item for a non-existent race! Cancelling.")
            return
        }
        val raceName = race.name

        val placeholders = mutableMapOf(
            "villagerName"            to villagerName,
            "villagerType"            to "${villager.villagerType}",
            "villagerRace"            to raceName,
            "villagerProfession"      to "${villager.profession}",
            "villagerProfessionLevel" to villager.professionLevelName,
            "language"                to plugin.config.getString("core-settings.language")!!,
            "itemType"                to item.type.toString(),
            "extraItemAttributes"     to item.getUniqueItemAttributes(),
            "itemRarity"              to if (item.isUniqueItem()) item.getUniqueItemRarity().toString().lowercase() else ProfessionManager.UniqueItemRarity.COMMON.toString().lowercase(),
            "settlementName"          to settlementName,
            "settlementLevel"         to settlementLevel,
            "randomLetterLowerCase"   to getRandomLetter().lowercase(),
            "namingStyle"             to (plugin.config.getString("core-settings.naming-style") ?: "Fantasy")
        )

        val promptTemplate = plugin.configurationClip.promptsConfig.getString("unique-item-description")
            ?: throw IllegalArgumentException("Unique item description is not defined! Check prompts.yml!")

        val prompt = promptTemplate.replaceMap(placeholders)

        createGenerationRequest().generate(prompt) { cleanedJson ->
            try {
                val data = plugin.gson.fromJson(cleanedJson, UniqueItemDescription::class.java)
                plugin.debug(cleanedJson)
                plugin.server.scheduler.runTask(plugin) { _ ->
                    plugin.server.pluginManager.callEvent(UniqueItemGenerateEvent(villager, item, data))
                }
            } catch (ignored: Exception) {}
        }
    }

    override fun generateQuestData(questManager: QuestManager, villager: Villager, quest: VillagerQuest.Builder) {
        var extraArguments = if (plugin.config.getBoolean("core-settings.swearing")) when (villager.getCharacterType()) {
            HumanoidCharacterType.ANGRY, HumanoidCharacterType.DRUNKARD -> "'20% of words are swearing'"
            else -> ""
        } else ""

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

        val race = villager.race ?: run {
            plugin.logger.severe("Trying to generate a quest for a non-existent race! Cancelling.")
            return
        }

        val raceName = race.name
        val raceDescription = race.description

        val settlementName  = villager.settlement?.data?.settlementName ?: ""
        val settlementLevel = villager.settlement?.size().toString()

        val placeholders = mutableMapOf(
            "villagerName"            to villagerName,
            "villagerRace"            to raceName,
            "villagerProfession"      to "${villager.profession}",
            "villagerPersonality"     to "${villager.getCharacterType()}",
            "villagerProfessionLevel" to villager.professionLevelName,
            "questItem"               to if (quest.questType == QuestType.OMINOUS_BANNER) "ominous banner" else quest.questItem.type.name.replace('_', ' ').lowercase(),
            "questItemAmount"         to quest.questItem.amount.toString(),
            "rewardItem"              to quest.rewardItem.type.name.replace('_', ' ').lowercase(),
            "language"                to plugin.config.getString("core-settings.language")!!,
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

        val promptTemplate = plugin.configurationClip.promptsConfig.getString("basic-task-description")
            ?: throw IllegalArgumentException("Basic task description is not defined! Check prompts.yml!")

        val prompt = promptTemplate.replaceMap(mapOf("questRequirements" to questRequirements)).replaceMap(placeholders)

        createGenerationRequest().generate(prompt) { cleanedQuestJson ->
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

    protected fun getRandomLetter(): String {
        val letters = 'A'..'Z'
        val randomIndex = Random.nextInt(letters.count())
        return letters.elementAt(randomIndex).toString()
    }

    protected fun String.replaceMap(replacements: Map<String, String>): String {
        var result = this
        for ((key, value) in replacements) {
            result = result.replace("{$key}", value)
        }
        return result
    }

    protected fun isChristmas(): Boolean {
        // Implement your Christmas check logic here
        return false
    }

    data class SettlementInformation(val townName: String)
}

