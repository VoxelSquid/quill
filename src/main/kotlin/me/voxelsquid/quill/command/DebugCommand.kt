package me.voxelsquid.quill.command

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.*
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.immersiveDialoguesKey
import me.voxelsquid.quill.QuestIntelligence.Companion.sendFormattedMessage
import me.voxelsquid.quill.ai.GeminiProvider
import me.voxelsquid.quill.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.villager.CharacterType
import me.voxelsquid.quill.villager.VillagerManager.Companion.character
import me.voxelsquid.quill.villager.interaction.DialogueManager
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.persistence.PersistentDataType

@CommandAlias("quill|q")
class DebugCommand(private val plugin: QuestIntelligence) : BaseCommand() {

    init {

        plugin.commandManager.commandCompletions.registerCompletion("villagerTypes") {
            listOf("SNOW", "JUNGLE", "DESERT", "SAVANNA", "TAIGA", "SWAMP", "PLAINS")
        }

        plugin.commandManager.commandCompletions.registerCompletion("villagerPersonalities") {
            CharacterType.getEnumValuesAsStrings()
        }

        plugin.commandManager.commandCompletions.registerCompletion("settlements") { context ->
            settlements[context.player.world]?.map { it.data.settlementName }
        }

    }

    @HelpCommand
    fun onHelp(sender: CommandSender) {
        sender.sendMessage("IMMA FIRIN' MAH LAZOR")
    }

    @Subcommand("reload")
    @CommandPermission("quill.reload")
    fun onReload(sender: CommandSender) {
        plugin.reloadConfigurations()
        plugin.questGenerator = GeminiProvider(plugin)
    }

    @Subcommand("tutorial")
    @CommandPermission("quill.tutorial")
    fun onTutorial(player: Player) {
        if (player.persistentDataContainer.getOrDefault(QuestIntelligence.playerTutorialKey, PersistentDataType.BOOLEAN, false)) {
            player.persistentDataContainer.set(QuestIntelligence.playerTutorialKey, PersistentDataType.BOOLEAN, false)
            player.sendFormattedMessage("command-message.tutorial-disabled")
        } else {
            player.persistentDataContainer.set(QuestIntelligence.playerTutorialKey, PersistentDataType.BOOLEAN, true)
            player.sendFormattedMessage("command-message.tutorial-enabled")
        }
    }

    @Subcommand("dialogue format")
    @CommandPermission("quill.dialogue.format")
    @Description("Specialized debug command for easy testing of villagers.")
    fun onDialogueFormat(player: Player, format: DialogueManager.DialogueFormat) {
        if (DialogueManager.DialogueFormat.entries.find { type -> type == format } != null) {
            player.persistentDataContainer.set(immersiveDialoguesKey, PersistentDataType.STRING, format.toString())
            QuestIntelligence.pluginInstance.language?.let { language ->
                language.getString("command-message.dialogue-format-changed")?.let { message ->
                    player.sendMessage(message.replace("{dialogueFormat}", format.toString()))
                }
            }
        }
    }

    @Subcommand("villager create")
    @CommandPermission("quill.villager.create")
    @CommandCompletion("@villagerPersonalities @villagerTypes")
    @Description("Specialized debug command for easy testing of villagers.")
    fun onVillager(player: Player, personality: CharacterType, type: String) {

        val world = player.world
        val villager = world.spawnEntity(player.location, EntityType.VILLAGER) as Villager

        villager.character = personality

        villager.villagerType = when (type) {
            "SNOW" -> Villager.Type.SNOW
            "JUNGLE" -> Villager.Type.JUNGLE
            "DESERT" -> Villager.Type.DESERT
            "SAVANNA" -> Villager.Type.SAVANNA
            "TAIGA" -> Villager.Type.TAIGA
            "SWAMP" -> Villager.Type.SWAMP
            else -> Villager.Type.PLAINS
        }

    }

    @Subcommand("settlement list")
    @CommandPermission("quill.settlement.list")
    fun onSettlementList(player: Player) {
        player.sendMessage("§6[8] §7Settlements:")
        settlements[player.world]?.forEach { settlement ->
            player.sendMessage(" §7- §6${settlement.data.settlementName}")
        }
    }

    @Subcommand("settlement teleport")
    @CommandPermission("quill.settlement.teleport")
    @CommandCompletion("@settlements")
    fun onSettlementTeleport(player: Player, settlementName: String) {
        val settlement = settlements[player.world]?.find { it.data.settlementName == settlementName }
        if (settlement == null) {
            player.sendMessage("§4Settlement $settlementName doesn't exist.")
            return
        }
        player.teleport(settlement.data.center)
    }

}