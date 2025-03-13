package me.voxelsquid.quill.base.command

import co.aikar.commands.BaseCommand
import co.aikar.commands.PaperCommandManager
import co.aikar.commands.annotation.*
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.getOminousBanner
import me.voxelsquid.quill.QuestIntelligence.Companion.immersiveDialoguesKey
import me.voxelsquid.quill.QuestIntelligence.Companion.sendFormattedMessage
import me.voxelsquid.quill.QuestIntelligence.Companion.verboseKey
import me.voxelsquid.quill.base.config.ConfigurationAccessor
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidCharacterType
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.setCharacterType
import me.voxelsquid.quill.gameplay.settlement.ReputationManager
import me.voxelsquid.quill.gameplay.settlement.ReputationManager.Companion.setReputation
import me.voxelsquid.quill.gameplay.settlement.Settlement
import me.voxelsquid.quill.gameplay.settlement.SettlementManager
import me.voxelsquid.quill.gameplay.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.gameplay.villager.interaction.DialogueManager
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.persistence.PersistentDataType
import java.lang.IllegalArgumentException

@CommandAlias("quill|q")
class CommandController : BaseCommand() {

    init {
        val commandManager = PaperCommandManager(plugin)
        commandManager.registerCommand(this)
        commandManager.commandCompletions.registerCompletion("villagerTypes") {
            listOf("SNOW", "JUNGLE", "DESERT", "SAVANNA", "TAIGA", "SWAMP", "PLAINS")
        }
        commandManager.commandCompletions.registerCompletion("reputationStates") {
            ReputationManager.Companion.Reputation.entries.map { it.name }
        }
        commandManager.commandCompletions.registerCompletion("villagerPersonalities") {
            HumanoidCharacterType.entries.map { it.name }
        }
        commandManager.commandCompletions.registerCompletion("settlements") { context ->
            settlements[context.player.world]?.map { it.data.settlementName }
        }
    }

    // TODO: Help command.
    // TODO: Reload command.

    @Subcommand("dialogue format")
    @CommandPermission("quill.dialogue.format")
    @Description("Specialized debug command for easy testing of villagers.")
    fun onDialogueFormat(player: Player, format: DialogueManager.DialogueFormat) {
        if (DialogueManager.DialogueFormat.entries.find { type -> type == format } != null) {
            player.persistentDataContainer.set(immersiveDialoguesKey, PersistentDataType.STRING, format.toString())
            plugin.configManager.language.let { language ->
                language.getString("command-message.dialogue-format-changed")?.let { message ->
                    player.sendFormattedMessage(message.replace("{dialogueFormat}", format.toString()))
                }
            }
        }
    }

    @Subcommand("villager create")
    @CommandPermission("quill.villager.create")
    @CommandCompletion("@villagerPersonalities @villagerTypes")
    @Description("Specialized debug command for easy testing of villagers.")
    fun onVillager(player: Player, characterType: HumanoidCharacterType, type: String) {

        val world = player.world
        val villager = world.spawnEntity(player.location, EntityType.VILLAGER) as Villager

        villager.setCharacterType(characterType)

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

    @Subcommand("settlement reputation")
    @CommandPermission("quill.settlement.reputation")
    @CommandCompletion("@players @reputationStates @settlements")
    fun onSettlementReputation(sender: CommandSender, target: String, status: String, settlementName: String) {

        val playerNotFoundMessage = ConfigurationAccessor(fileName = "language.yml", path = "command-error-message.player-not-found", defaultValue = "§cPlayer not found: §b{playerName} §c(online players only).").get()
        val statusNotFoundMessage = ConfigurationAccessor(fileName = "language.yml", path = "command-error-message.status-not-found", defaultValue = "§cError! Non-existent reputation status: §с{status}.").get()
        val nonExistingSettlement = ConfigurationAccessor(fileName = "language.yml", path = "command-error-message.settlement-not-found", defaultValue = "§cError! Non-existent settlement status: §с{settlementName}.").get()
        val commandSuccessMessage = ConfigurationAccessor(fileName = "language.yml", path = "command-success-message.reputation-changed", defaultValue = "§7Reputation of §e{playerName} §7has been changed to §6{status}§7.").get()

        val settlement = SettlementManager.getByName(settlementName)
        if (settlement == null) {
            sender.sendMessage(nonExistingSettlement.replace("{settlementName}", settlementName))
            return
        }

        val reputation = try {
            ReputationManager.Companion.Reputation.valueOf(status)
        } catch (exception: IllegalArgumentException) {
            sender.sendMessage(statusNotFoundMessage.replace("{status}", status))
            return
        }

        Bukkit.getPlayer(target)?.let { targetPlayer ->
            SettlementManager.getByName(settlementName)?.let { settlement: Settlement ->
                settlement.setReputation(targetPlayer, reputation.requiredReputationAmount)
                sender.sendMessage(commandSuccessMessage.replace("{playerName}", target).replace("{status}", reputation.localizedName.get()))
            }
        } ?: sender.sendMessage(playerNotFoundMessage.replace("{playerName}", target))

    }

    @Subcommand("debug banner")
    @CommandPermission("quill.banner")
    fun onDebugBanner(player: Player) {
        player.inventory.addItem(getOminousBanner().clone())
    }

    @Subcommand("debug verbose")
    @CommandPermission("quill.verbose")
    fun onDebugVerbose(player: Player) {

        val data = player.persistentDataContainer
        if (!data.has(verboseKey)) {
            data.set(verboseKey, PersistentDataType.BOOLEAN, true)
        } else data.set(verboseKey, PersistentDataType.BOOLEAN, !data.get(verboseKey, PersistentDataType.BOOLEAN)!!)

        if (data.get(verboseKey, PersistentDataType.BOOLEAN) == true) {
            player.sendFormattedMessage(plugin.configManager.language.getString("command-message.verbose.activated")!!)
        } else player.sendFormattedMessage(plugin.configManager.language.getString("command-message.verbose.deactivated")!!)
    }

    companion object {
        private val plugin = QuestIntelligence.pluginInstance
    }

}