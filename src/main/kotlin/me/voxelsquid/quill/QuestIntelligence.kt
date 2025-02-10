package me.voxelsquid.quill

import co.aikar.commands.PaperCommandManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.voxelsquid.quill.ai.GeminiProvider
import me.voxelsquid.quill.command.DebugCommand
import me.voxelsquid.quill.humanoid.HumanoidManager
import me.voxelsquid.quill.quest.data.VillagerQuest
import me.voxelsquid.quill.settlement.SettlementManager
import me.voxelsquid.quill.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.settlement.SettlementManager.Companion.settlementsWorldKey
import me.voxelsquid.quill.util.LocationAdapter
import me.voxelsquid.quill.villager.interaction.DialogueManager
import me.voxelsquid.quill.villager.interaction.DialogueManager.DialogueFormat
import me.voxelsquid.quill.villager.interaction.InteractionMenu
import me.voxelsquid.quill.villager.interaction.InteractionMenuManager
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.raid.Raid
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*

class QuestIntelligence : JavaPlugin(), Listener {

    lateinit var configurationClip: ConfigurationClip
    lateinit var commandManager:    PaperCommandManager
    lateinit var settlementManager: SettlementManager
    lateinit var questGenerator:    GeminiProvider
    lateinit var humanoidManager:   HumanoidManager

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
            logger.severe("QuestIntelligence will be disabled.")
            Bukkit.getServer().pluginManager.disablePlugin(this)
            return
        }

        enabledWorlds = mutableListOf<World>().apply {
            config.getStringList("core-settings.enabled-worlds").forEach {
                world -> add(Bukkit.getWorld(world)!!)
            }
        }

        this.setupCommands()
        questGenerator    = GeminiProvider(this)
        settlementManager = SettlementManager(this)
        humanoidManager   = HumanoidManager()
        this.server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        DialogueManager.dialogues.values.forEach(DialogueManager.DialogueWindow::destroy)
        InteractionMenuManager.openedMenuList.forEach(InteractionMenu::destroy)
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
        this.commandManager.registerCommand(DebugCommand())
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

    companion object {

        var messagePrefix = ""

        lateinit var pluginInstance: QuestIntelligence
        lateinit var languageFile: File

        val verboseKey            by lazy { NamespacedKey(pluginInstance, "verbose") }
        val immersiveDialoguesKey by lazy { NamespacedKey(pluginInstance, "immersiveDialogues") }
        val currentSettlementKey  by lazy { NamespacedKey(pluginInstance, "currentSettlement") }

        fun getOminousBanner() : ItemStack {
            return CraftItemStack.asBukkitCopy(
                Raid.getOminousBannerInstance((pluginInstance.server.worlds.random() as CraftWorld).handle.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)))
        }

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

        var Player.currentSettlement: String?
            get() = this.persistentDataContainer.get(currentSettlementKey, PersistentDataType.STRING)
            set(value) {
                if (value != null) {
                    this.persistentDataContainer.set(currentSettlementKey, PersistentDataType.STRING, value)
                } else this.persistentDataContainer.remove(currentSettlementKey)
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

        fun Player.sendFormattedMessage(message: String) {
            this.sendMessage(messagePrefix + message)
        }

        fun Player.sendVerbose(message: String) {
            if (player!!.persistentDataContainer.get(verboseKey, PersistentDataType.BOOLEAN) == true) {
                this.sendMessage(messagePrefix + message)
            }
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

}
