package me.voxelsquid.quill.gameplay.settlement

import me.voxelsquid.quill.QuestIntelligence.Companion.currentSettlement
import me.voxelsquid.quill.QuestIntelligence.Companion.pluginInstance
import me.voxelsquid.quill.base.config.ConfigurableValue
import me.voxelsquid.quill.gameplay.settlement.SettlementManager.Companion.settlements
import org.bukkit.Sound
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.raid.RaidFinishEvent
import org.bukkit.event.raid.RaidTriggerEvent

class ReputationManager : Listener {

    /*
        TODO:
         1. Добавляем начисление репутации за выполнение квестов, а ещё за завершение рейда.
         2. Имплементируем влияние репутации на торговые цены. Множитель цен теперь зависит не от количества положительной репутации, а от статуса.
            Например, Friendly даст 5% скидку, а Exalted — 25%. Exiled запрещает торговлю.
     */

    @EventHandler
    private fun handlePlayerKillEntity(event: EntityDeathEvent) {

        val victim = event.entity
        val killer = event.damageSource.causingEntity ?: return

        (killer as? Player)?.let { player ->

            // Smart world check.
            if (!plugin.allowedWorlds.contains(player.world))
                return

            // Check if entity is from spawner, preventing cheesy grinding.
            if (ignoreFromSpawners && victim.fromMobSpawner())
                return

            // Get the nearby settlement or return.
            val settlement = settlements[player.world]?.find { it.data.settlementName == killer.currentSettlement } ?: return

            val value: Int = when (victim) {
                is Zombie    -> if (victim.isAdult) zombieReputation else bZombieReputation
                is Skeleton  -> skeletonReputation
                is Creeper   -> creeperReputation
                is Spider    -> spiderReputation
                is Enderman  -> endermanReputation
                is Villager  -> villagerReputation
                is IronGolem -> ironGolemReputation
                is Raider    -> if (victim is Ravager) ravagerReputation else raiderReputation
                is Phantom   -> phantomReputation
                else -> { return }
            }

            settlement.changeReputation(player, value)
        }

    }

    @EventHandler
    private fun handleRaidTrigger(event: RaidTriggerEvent) {
        event.player.let { player ->

            // Smart world check.
            if (!plugin.allowedWorlds.contains(player.world))
                return

            // Get the nearby settlement or return.
            val settlement = settlements[player.world]?.find { it.data.settlementName == player.currentSettlement } ?: return
            settlement.changeReputation(player, raidStartReputation)
        }
    }

    @EventHandler
    private fun handleRaidFinish(event: RaidFinishEvent) {
        event.winners.forEach { player ->

            // Smart world check.
            if (!plugin.allowedWorlds.contains(player.world))
                return

            // Get the nearby settlement or return.
            val settlement = settlements[player.world]?.find { it.data.settlementName == player.currentSettlement } ?: return
            settlement.changeReputation(player, raidFinishReputation)
        }
    }

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    companion object {
        private val plugin = pluginInstance

        private val ignoreFromSpawners = ConfigurableValue(path = "reputation.ignore-spawner-entities", defaultValue = true, comments = mutableListOf("Check if entity is from spawner, preventing cheesy grinding.")).get()
        private val chatNotification   = ConfigurableValue(path = "reputation.chat-notification", defaultValue = true, comments = mutableListOf("Notify players in chat when their reputation changes.")).get()
        private val zombieReputation   = ConfigurableValue(path = "reputation.kill.zombie", defaultValue = 20).get()
        private val bZombieReputation  = ConfigurableValue(path = "reputation.kill.baby-zombie", defaultValue = 30).get()
        private val skeletonReputation = ConfigurableValue(path = "reputation.kill.skeleton", defaultValue = 25).get()
        private val creeperReputation  = ConfigurableValue(path = "reputation.kill.creeper", defaultValue = 25).get()
        private val spiderReputation   = ConfigurableValue(path = "reputation.kill.spider", defaultValue = 20).get()
        private val endermanReputation = ConfigurableValue(path = "reputation.kill.enderman", defaultValue = 100).get()
        private val raiderReputation   = ConfigurableValue(path = "reputation.kill.raider", defaultValue = 50).get()
        private val ravagerReputation  = ConfigurableValue(path = "reputation.kill.ravager", defaultValue = 250).get()
        private val phantomReputation  = ConfigurableValue(path = "reputation.kill.phantom", defaultValue = 30).get()

        // Negative reputation for killing a villager or an iron golem.
        private val villagerReputation  = ConfigurableValue(path = "reputation.kill.villager", defaultValue = -250).get()
        private val ironGolemReputation = ConfigurableValue(path = "reputation.kill.iron-golem", defaultValue = -250).get()

        private val raidStartReputation  = ConfigurableValue(path = "reputation.raid.start", defaultValue = -250).get()
        private val raidFinishReputation = ConfigurableValue(path = "reputation.raid.finish", defaultValue = 500).get()

        // Reputation change notification messages.
        private val increaseMessage = ConfigurableValue(fileName = "language.yml", path = "settlement-reputation.increase", defaultValue = "§9Reputation with {currentSettlement} increased by {amount}.").get()
        private val decreaseMessage = ConfigurableValue(fileName = "language.yml", path = "settlement-reputation.decrease", defaultValue = "§9Reputation with {currentSettlement} decreased by {amount}.").get()

        // Reputation status update stuff.
        private val statusUpdateMessage = ConfigurableValue(fileName = "language.yml", path = "settlement-reputation.status-update.message", defaultValue = "§eYour standing with {currentSettlement} has shifted to {status}.").get()
        private val statusUpdateSound   = ConfigurableValue(path = "reputation.status-update.sound", defaultValue = "ui.hud.bubble_pop", comments = mutableListOf("https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html")).get()

        // Reputation status required values.
        private val exiledReputationRequired = ConfigurableValue(path = "reputation.status.exiled", defaultValue = -1000).get()
        private val hostileReputationRequired = ConfigurableValue(path = "reputation.status.hostile", defaultValue = -500).get()
        private val unfriendlyReputationRequired = ConfigurableValue(path = "reputation.status.unfriendly", defaultValue = -250).get()
        private val neutralReputationRequired = ConfigurableValue(path = "reputation.status.neutral", defaultValue = 0).get()
        private val friendlyReputationRequired = ConfigurableValue(path = "reputation.status.friendly", defaultValue = 250).get()
        private val honoredReputationRequired = ConfigurableValue(path = "reputation.status.honored", defaultValue = 500).get()
        private val reveredReputationRequired = ConfigurableValue(path = "reputation.status.revered", defaultValue = 1000).get()
        private val exaltedReputationRequired = ConfigurableValue(path = "reputation.status.exalted", defaultValue = 2000).get()

        private val exiled = ConfigurableValue(fileName = "language.yml", path = "reputation.status.exiled", defaultValue = "Exiled").get()
        private val hostile = ConfigurableValue(fileName = "language.yml", path = "reputation.status.hostile", defaultValue = "Hostile").get()
        private val unfriendly = ConfigurableValue(fileName = "language.yml", path = "reputation.status.unfriendly", defaultValue = "Unfriendly").get()
        private val neutral = ConfigurableValue(fileName = "language.yml", path = "reputation.status.neutral", defaultValue = "Neutral").get()
        private val friendly = ConfigurableValue(fileName = "language.yml", path = "reputation.status.friendly", defaultValue = "Friendly").get()
        private val honored = ConfigurableValue(fileName = "language.yml", path = "reputation.status.honored", defaultValue = "Honored").get()
        private val revered = ConfigurableValue(fileName = "language.yml", path = "reputation.status.revered", defaultValue = "Revered").get()
        private val exalted = ConfigurableValue(fileName = "language.yml", path = "reputation.status.exalted", defaultValue = "Exalted").get()

        fun Settlement.changeReputation(player: Player, value: Int) {

            val previousStatus = player.getPlayerReputationStatus(this)
            data.reputation[player.uniqueId] = (data.reputation[player.uniqueId] ?: 0) + value
            val newStatus = player.getPlayerReputationStatus(this)

            val reputationChangeMessage = (if (value > 0) increaseMessage else decreaseMessage).replace("{currentSettlement}", this.data.settlementName).replace("{amount}", value.toString().replace("-", ""))
            if (chatNotification) {
                player.sendMessage(reputationChangeMessage)

                // Reputation status update notification.
                if (previousStatus != newStatus) {
                    val statusChangeMessage = statusUpdateMessage.replace("{currentSettlement}", this.data.settlementName).replace("{status}", newStatus.localizedName)
                    player.sendMessage(statusChangeMessage)
                    player.playSound(player.eyeLocation, statusUpdateSound, 1F, 1F)
                }
            }

        }

        enum class Reputation(val localizedName: String) {
            EXALTED(exalted),
            REVERED(revered),
            HONORED(honored),
            FRIENDLY(friendly),
            NEUTRAL(neutral),
            UNFRIENDLY(unfriendly),
            HOSTILE(hostile),
            EXILED(exiled)
        }

        fun Player.getPlayerReputationStatus(settlement: Settlement): Reputation {
            val reputation = settlement.getPlayerReputation(this)

            return when {
                reputation >= exaltedReputationRequired -> Reputation.EXALTED
                reputation >= reveredReputationRequired -> Reputation.REVERED
                reputation >= honoredReputationRequired -> Reputation.HONORED
                reputation >= friendlyReputationRequired -> Reputation.FRIENDLY
                reputation >= neutralReputationRequired -> Reputation.NEUTRAL
                reputation >= unfriendlyReputationRequired -> Reputation.UNFRIENDLY
                reputation >= hostileReputationRequired -> Reputation.HOSTILE
                reputation >= exiledReputationRequired -> Reputation.EXILED
                else -> Reputation.EXILED // Fallback for anything below exiled threshold
            }
        }
    }

}