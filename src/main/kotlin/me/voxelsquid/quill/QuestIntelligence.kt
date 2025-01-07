package me.voxelsquid.quill

import co.aikar.commands.PaperCommandManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.voxelsquid.quill.ai.GeminiProvider
import me.voxelsquid.quill.command.DebugCommand
import me.voxelsquid.quill.quest.data.VillagerQuest
import me.voxelsquid.quill.settlement.CachedSettlementCuboid.Companion.particleThreadPool
import me.voxelsquid.quill.settlement.SettlementManager
import me.voxelsquid.quill.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.settlement.SettlementManager.Companion.settlementsWorldKey
import me.voxelsquid.quill.util.LocationAdapter
import me.voxelsquid.quill.villager.VillagerManager
import me.voxelsquid.quill.villager.interaction.DialogueManager
import me.voxelsquid.quill.villager.interaction.DialogueManager.DialogueFormat
import me.voxelsquid.quill.villager.interaction.InteractionMenu
import me.voxelsquid.quill.villager.interaction.MenuManager
import org.bukkit.*
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.ipvp.canvas.MenuFunctionListener
import java.io.File
import java.util.*
import kotlin.collections.HashMap


class QuestIntelligence : JavaPlugin(), Listener {

    lateinit var configurationClip: ConfigurationClip
    lateinit var commandManager:    PaperCommandManager
    lateinit var villagerManager:   VillagerManager
    lateinit var settlementManager: SettlementManager
    lateinit var questGenerator:    GeminiProvider


    var language: YamlConfiguration? = null
    var baseColor = "§f"
    var importantWordColor = "§c"
    var interestingStuffColor = "§c"

    override fun onEnable() {
        pluginInstance = this

        languageFile = File(pluginInstance.dataFolder, "language.yml")
        super.saveResource("config.yml", false)
        super.saveResource("language.yml", false)
        this.reloadConfigurations()

        if (config.getString("core-settings.api-key") == "GEMINI_API_KEY") {
            logger.severe("The plugin must be configured before it can be used. You need to replace the value of ‘core-settings.api-key’ with a real Gemini API key (it is free of charge). See config.yml for details on how to get this key.")
            logger.severe("QuestIntelligence will be disabled...")
            Bukkit.getServer().pluginManager.disablePlugin(this)
        }

        enabledWorlds = mutableListOf<World>().apply {
            config.getStringList("core-settings.enabled-worlds").forEach {
                world -> add(Bukkit.getWorld(world)!!)
            }
        }

        this.setupCommands()
        questGenerator    = GeminiProvider(this)
        villagerManager   = VillagerManager(this)
        settlementManager = SettlementManager(this)
        this.server.pluginManager.registerEvents(this, this)
        this.server.pluginManager.registerEvents(MenuFunctionListener(), this)
    }

    override fun onDisable() {
        DialogueManager.dialogues.values.forEach(DialogueManager.DialogueWindow::destroy)
        MenuManager.openedMenuList.forEach(InteractionMenu::destroy)
        particleThreadPool.shutdown()
        this.saveSettlements()
    }

    var enabledWorlds: List<World> = mutableListOf()

    private fun saveSettlements() {
        enabledWorlds.forEach { world ->
            world.persistentDataContainer.set(settlementsWorldKey, PersistentDataType.STRING, gson.toJson(settlements[world]?.map { it.data }))
        }
    }

    private fun setupCommands() {
        this.commandManager = PaperCommandManager(this)
        this.commandManager.registerCommand(DebugCommand(pluginInstance))
    }

    fun reloadConfigurations() {
        super.reloadConfig()
        this.configurationClip = ConfigurationClip(this)
        this.language = YamlConfiguration.loadConfiguration(languageFile)
        this.baseColor = config.getString("core-settings.text-formatting.base-color") ?: "§7"
        this.importantWordColor = config.getString("core-settings.text-formatting.important-color") ?: "§6"
        this.interestingStuffColor = config.getString("core-settings.text-formatting.emotional-color") ?: "§2"
        messagePrefix = pluginInstance.config.getString("core-settings.message-prefix") ?: ""
    }

    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(VillagerQuest::class.java, VillagerQuest.VillagerQuestAdapter())
        .registerTypeAdapter(Location::class.java, LocationAdapter())
        .create()

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        event.player.sendTutorialMessage(TutorialMessage.FIRST_CONNECTION)
    }

    @EventHandler
    private fun onPlayerQuit(event: PlayerQuitEvent) {
        tutorialMessages.remove(event.player)
    }

    companion object {

        var messagePrefix = ""
        private val tutorialMessages: HashMap<Player, MutableList<TutorialMessage>> = hashMapOf()

        val playerTutorialKey: NamespacedKey by lazy { NamespacedKey(pluginInstance, "tutorial") }
        val playerStatsKey: NamespacedKey by lazy { NamespacedKey(pluginInstance, "statistics") }
        val immersiveDialoguesKey: NamespacedKey by lazy { NamespacedKey(pluginInstance, "immersiveDialogues") }
        lateinit var pluginInstance: QuestIntelligence
        lateinit var languageFile: File

        val Player.dialogueFormat: DialogueFormat
            get() {
                this.persistentDataContainer.get(immersiveDialoguesKey, PersistentDataType.STRING)?.let { type ->
                    return DialogueFormat.valueOf(type)
                }
                (pluginInstance.config.getString("core-settings.default-dialogue-format") ?: "IMMERSIVE").also { type ->
                    this.persistentDataContainer.set(immersiveDialoguesKey, PersistentDataType.STRING, type)
                    return DialogueFormat.valueOf(type)
                }
            }

        fun isChristmas(): Boolean {
            val calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)

            val startOfRange = Calendar.getInstance().apply {
                set(currentYear, Calendar.DECEMBER, 16, 0, 0, 0)
            }

            val endOfRange = Calendar.getInstance().apply {
                set(currentYear + 1, Calendar.JANUARY, 5, 23, 59, 59)
            }

            return calendar.after(startOfRange) && calendar.before(endOfRange)
        }

        fun Player.sendTutorialMessage(tutorialMessage: TutorialMessage) {

            var tutorialMode = this.persistentDataContainer.get(playerTutorialKey, PersistentDataType.BOOLEAN)

            if (tutorialMode == null) {
                this.persistentDataContainer.set(playerTutorialKey, PersistentDataType.BOOLEAN, true)
                tutorialMode = true
            }

            if (!tutorialMode)
                return

            val messagesSentList = tutorialMessages.computeIfAbsent(this) {
                mutableListOf()
            }

            if (!messagesSentList.contains(tutorialMessage)) {
                pluginInstance.language?.let {
                    val sound = Sound.valueOf(it.getString("player-tutorial.tutorial-message-sound")!!)
                    this.playSound(this.location, sound, 1F, 1F)
                    messagesSentList.add(tutorialMessage)

                    it.getStringList(tutorialMessage.key).forEach { message ->
                        this.sendMessage(message)
                    }
                }
            }

        }

        fun Player.sendFormattedMessage(message: String) {
            this.sendMessage(messagePrefix + message)
        }

    }

    class ConfigurationClip(plugin: JavaPlugin) {

        private val replace: Boolean = false

        val pricesConfig: YamlConfiguration by lazy {
            plugin.saveResource("prices.yml", replace)
            YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "prices.yml"))
        }

        val promptsConfig: YamlConfiguration by lazy {
            plugin.saveResource("prompts.yml", replace)
            YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "prompts.yml"))
        }

    }

    val debug = true
    fun debug(message: String) {
        if (debug) logger.info("[DEBUG] $message")
    }

    enum class TutorialMessage(val key: String) {
        FIRST_CONNECTION("player-tutorial.first-connection-message"),
        VILLAGER_INTERACTION("player-tutorial.interaction-message"),
        DIALOGUE("player-tutorial.dialogue-message"),
        QUESTING("player-tutorial.questing-message"),
        SLEEP_INTERRUPTION("player-tutorial.sleep-interruption-message"),
        BAD_REPUTATION("player-tutorial.bad-reputation");
    }

}