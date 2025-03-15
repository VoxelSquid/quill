package me.voxelsquid.quill

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import me.voxelsquid.quill.QuestIntelligence.Companion.dialogueFormat
import me.voxelsquid.quill.gameplay.ai.GeminiProvider
import me.voxelsquid.quill.base.config.ConfigurationManager
import me.voxelsquid.quill.base.PluginRuntimeController
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager
import me.voxelsquid.quill.gameplay.quest.data.VillagerQuest
import me.voxelsquid.quill.gameplay.settlement.SettlementManager
import me.voxelsquid.quill.gameplay.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.gameplay.settlement.SettlementManager.Companion.settlementsWorldKey
import me.voxelsquid.quill.gameplay.util.LocationAdapter
import me.voxelsquid.quill.gameplay.villager.interaction.DialogueManager
import me.voxelsquid.quill.gameplay.villager.interaction.DialogueManager.DialogueFormat
import me.voxelsquid.quill.gameplay.villager.interaction.InteractionMenu
import me.voxelsquid.quill.gameplay.villager.interaction.InteractionMenuManager
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.raid.Raid
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.geysermc.geyser.api.GeyserApi
import java.util.*

class QuestIntelligence : JavaPlugin() {

    lateinit var controller:      PluginRuntimeController
    lateinit var geminiProvider:  GeminiProvider
    lateinit var humanoidManager: HumanoidManager
    lateinit var configManager:   ConfigurationManager

    var allowedWorlds: List<World> = mutableListOf()

    override fun onEnable() {

        // PluginRuntimeController checks some stuff, saves resources, generates configs, setups commands.
        controller = PluginRuntimeController()
        if (!controller.isOk())
            return

        configManager = controller.configurationManager
        allowedWorlds = controller.allowedWorlds.map { server.getWorld(it) ?: throw NullPointerException("Non-existent world specified in the config.yml: $it.") }

        geminiProvider = GeminiProvider(this)
        geminiProvider.generateTranslation()

        controller.setupCommands()
        humanoidManager = HumanoidManager()
    }

    override fun onDisable() {
        DialogueManager.dialogues.values.forEach(DialogueManager.DialogueWindow::destroy)
        InteractionMenuManager.openedMenuList.forEach(InteractionMenu::destroy)
        allowedWorlds.forEach { world ->
            world.persistentDataContainer.set(settlementsWorldKey, PersistentDataType.STRING, gson.toJson(settlements[world]?.map { it.data }))
        }
    }

    init {
        pluginInstance = this
    }

    companion object {

        lateinit var pluginInstance: QuestIntelligence

        val verboseKey            by lazy { NamespacedKey(pluginInstance, "verbose") }
        val immersiveDialoguesKey by lazy { NamespacedKey(pluginInstance, "immersiveDialogues") }
        val currentSettlementKey  by lazy { NamespacedKey(pluginInstance, "currentSettlement") }

        val gson: Gson = GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(VillagerQuest::class.java, VillagerQuest.VillagerQuestAdapter())
            .registerTypeAdapter(Location::class.java, LocationAdapter())
            .create()

        fun getOminousBanner() : ItemStack {
            return CraftItemStack.asBukkitCopy(
                Raid.getOminousBannerInstance((pluginInstance.server.worlds.random() as CraftWorld).handle.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)))
        }

        val Player.dialogueFormat: DialogueFormat
            get() {
                this.persistentDataContainer.get(immersiveDialoguesKey, PersistentDataType.STRING)?.let { type ->
                    return DialogueFormat.valueOf(type)
                }
                return pluginInstance.controller.format.also { type ->
                    this.persistentDataContainer.set(immersiveDialoguesKey, PersistentDataType.STRING, type.toString())
                }
            }

        var Player.currentSettlement: String?
            get() = this.persistentDataContainer.get(currentSettlementKey, PersistentDataType.STRING)
            set(value) {
                if (value != null) {
                    this.persistentDataContainer.set(currentSettlementKey, PersistentDataType.STRING, value)
                } else this.persistentDataContainer.remove(currentSettlementKey)
            }

        fun Player.isGeyserPlayer() : Boolean = try {
            GeyserApi.api().connectionByUuid(this.uniqueId) != null
        } catch (exception: Exception) {
            false
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
            this.sendMessage(pluginInstance.controller.messagePrefix + message)
        }

        fun Player.sendVerbose(message: String) {
            if (player!!.persistentDataContainer.get(verboseKey, PersistentDataType.BOOLEAN) == true) {
                this.sendMessage(pluginInstance.controller.messagePrefix + message)
            }
        }

    }

    val debug = true
    fun debug(message: String) {
        if (debug) logger.info("[DEBUG] $message")
    }

}
