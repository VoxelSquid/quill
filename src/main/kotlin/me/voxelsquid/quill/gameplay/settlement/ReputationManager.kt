package me.voxelsquid.quill.gameplay.settlement

import me.voxelsquid.quill.QuestIntelligence.Companion.currentSettlement
import me.voxelsquid.quill.QuestIntelligence.Companion.pluginInstance
import me.voxelsquid.quill.base.config.ConfigurationAccessor
import me.voxelsquid.quill.gameplay.settlement.SettlementManager.Companion.settlements
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

            settlement.addReputation(player, value)
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
            settlement.addReputation(player, raidStartReputation)
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
            settlement.addReputation(player, raidFinishReputation)
        }
    }

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    companion object {
        private val plugin = pluginInstance

        private val ignoreFromSpawners = ConfigurationAccessor(path = "reputation.ignore-spawner-entities", defaultValue = true, comments = mutableListOf("Check if entity is from spawner, preventing cheesy grinding.")).get()
        private val chatNotification   = ConfigurationAccessor(path = "reputation.chat-notification", defaultValue = true, comments = mutableListOf("Notify players in chat when their reputation changes.")).get()
        private val zombieReputation   = ConfigurationAccessor(path = "reputation.kill.zombie", defaultValue = 20).get()
        private val bZombieReputation  = ConfigurationAccessor(path = "reputation.kill.baby-zombie", defaultValue = 30).get()
        private val skeletonReputation = ConfigurationAccessor(path = "reputation.kill.skeleton", defaultValue = 25).get()
        private val creeperReputation  = ConfigurationAccessor(path = "reputation.kill.creeper", defaultValue = 25).get()
        private val spiderReputation   = ConfigurationAccessor(path = "reputation.kill.spider", defaultValue = 20).get()
        private val endermanReputation = ConfigurationAccessor(path = "reputation.kill.enderman", defaultValue = 100).get()
        private val raiderReputation   = ConfigurationAccessor(path = "reputation.kill.raider", defaultValue = 50).get()
        private val ravagerReputation  = ConfigurationAccessor(path = "reputation.kill.ravager", defaultValue = 250).get()
        private val phantomReputation  = ConfigurationAccessor(path = "reputation.kill.phantom", defaultValue = 30).get()

        // Negative reputation for killing a villager or an iron golem.
        private val villagerReputation  = ConfigurationAccessor(path = "reputation.kill.villager", defaultValue = -250).get()
        private val ironGolemReputation = ConfigurationAccessor(path = "reputation.kill.iron-golem", defaultValue = -250).get()

        private val raidStartReputation  = ConfigurationAccessor(path = "reputation.raid.start", defaultValue = -250).get()
        private val raidFinishReputation = ConfigurationAccessor(path = "reputation.raid.finish", defaultValue = 500).get()
        private val statusUpdateSound    = ConfigurationAccessor(path = "reputation.status-update.sound", defaultValue = "ui.hud.bubble_pop", comments = mutableListOf("https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html")).get()

        // Reputation status required values.
        private val exiledReputationRequired = ConfigurationAccessor(path = "reputation.status.exiled", defaultValue = -1000).get()
        private val hostileReputationRequired = ConfigurationAccessor(path = "reputation.status.hostile", defaultValue = -500).get()
        private val unfriendlyReputationRequired = ConfigurationAccessor(path = "reputation.status.unfriendly", defaultValue = -250).get()
        private val neutralReputationRequired = ConfigurationAccessor(path = "reputation.status.neutral", defaultValue = 0).get()
        private val friendlyReputationRequired = ConfigurationAccessor(path = "reputation.status.friendly", defaultValue = 250).get()
        private val honoredReputationRequired = ConfigurationAccessor(path = "reputation.status.honored", defaultValue = 500).get()
        private val reveredReputationRequired = ConfigurationAccessor(path = "reputation.status.revered", defaultValue = 1000).get()
        private val exaltedReputationRequired = ConfigurationAccessor(path = "reputation.status.exalted", defaultValue = 2000).get()

        // Adding the value to existent one, used by inner gameplay logic.
        fun Settlement.addReputation(player: Player, value: Int) {

            val increaseMessage = ConfigurationAccessor(fileName = "language.yml", path = "settlement-reputation.increase", defaultValue = "§9Reputation with {currentSettlement} increased by {amount}.").get()
            val decreaseMessage = ConfigurationAccessor(fileName = "language.yml", path = "settlement-reputation.decrease", defaultValue = "§9Reputation with {currentSettlement} decreased by {amount}.").get()
            val statusUpdateMessage = ConfigurationAccessor(fileName = "language.yml", path = "settlement-reputation.status-update", defaultValue = "§eYour standing with {currentSettlement} has shifted to {status}.").get()

            val previousStatus = player.getPlayerReputationStatus(this)
            data.reputation[player.uniqueId] = (data.reputation[player.uniqueId] ?: 0) + value
            val newStatus = player.getPlayerReputationStatus(this)

            val reputationChangeMessage = (if (value > 0) increaseMessage else decreaseMessage).replace("{currentSettlement}", this.data.settlementName).replace("{amount}", value.toString().replace("-", ""))
            if (chatNotification) {
                player.sendMessage(reputationChangeMessage)

                // Reputation status update notification.
                if (previousStatus != newStatus) {
                    val statusChangeMessage = statusUpdateMessage.replace("{currentSettlement}", this.data.settlementName).replace("{status}", newStatus.localizedName.get())
                    player.sendMessage(statusChangeMessage)
                    player.playSound(player.eyeLocation, statusUpdateSound, 1F, 1F)
                }
            }

        }

        // Setting the value, used by set reputation command.
        fun Settlement.setReputation(player: Player, value: Int) {

            val statusUpdateMessage = ConfigurationAccessor(fileName = "language.yml", path = "settlement-reputation.status-update", defaultValue = "§eYour standing with {currentSettlement} has shifted to {status}.").get()

            val previousStatus = player.getPlayerReputationStatus(this)
            data.reputation[player.uniqueId] = value
            val newStatus = player.getPlayerReputationStatus(this)

            if (chatNotification) {
                // Reputation status update notification.
                if (previousStatus != newStatus) {
                    val statusChangeMessage = statusUpdateMessage.replace("{currentSettlement}", this.data.settlementName).replace("{status}", newStatus.localizedName.get())
                    player.sendMessage(statusChangeMessage)
                    player.playSound(player.eyeLocation, statusUpdateSound, 1F, 1F)
                }
            }

        }

        enum class Reputation(val localizedName: ConfigurationAccessor<String>, val requiredReputationAmount: Int, val priceMultiplier: ConfigurationAccessor<Double>) {
            EXALTED(ConfigurationAccessor(fileName = "language.yml", path = "reputation.status.exalted", defaultValue = "Exalted"), exaltedReputationRequired, ConfigurationAccessor(path = "reputation.price-multiplier.exalted", defaultValue = 0.6)),
            REVERED(ConfigurationAccessor(fileName = "language.yml", path = "reputation.status.revered", defaultValue = "Revered"), reveredReputationRequired, ConfigurationAccessor(path = "reputation.price-multiplier.revered", defaultValue = 0.7)),
            HONORED(ConfigurationAccessor(fileName = "language.yml", path = "reputation.status.honored", defaultValue = "Honored"), honoredReputationRequired, ConfigurationAccessor(path = "reputation.price-multiplier.honored", defaultValue = 0.9)),
            FRIENDLY(ConfigurationAccessor(fileName = "language.yml", path = "reputation.status.friendly", defaultValue = "Friendly"), friendlyReputationRequired, ConfigurationAccessor(path = "reputation.price-multiplier.friendly", defaultValue = 0.9)),
            NEUTRAL(ConfigurationAccessor(fileName = "language.yml", path = "reputation.status.neutral", defaultValue = "Neutral"), neutralReputationRequired, ConfigurationAccessor(path = "reputation.price-multiplier.neutral", defaultValue = 1.0)),
            UNFRIENDLY(ConfigurationAccessor(fileName = "language.yml", path = "reputation.status.unfriendly", defaultValue = "Unfriendly"), unfriendlyReputationRequired, ConfigurationAccessor(path = "reputation.price-multiplier.unfriendly", defaultValue = 1.25)),
            HOSTILE(ConfigurationAccessor(fileName = "language.yml", path = "reputation.status.hostile", defaultValue = "Hostile"), hostileReputationRequired, ConfigurationAccessor(path = "reputation.price-multiplier.hostile", defaultValue = 1.5)),
            EXILED(ConfigurationAccessor(fileName = "language.yml", path = "reputation.status.exiled", defaultValue = "Exiled"), exiledReputationRequired, ConfigurationAccessor(path = "reputation.price-multiplier.exiled", defaultValue = 2.0));
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
                else -> Reputation.EXILED
            }
        }
    }

}