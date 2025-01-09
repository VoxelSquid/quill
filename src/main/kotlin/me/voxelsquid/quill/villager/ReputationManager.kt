package me.voxelsquid.quill.villager

import me.voxelsquid.quill.QuestIntelligence
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.raid.RaidFinishEvent
import org.bukkit.persistence.PersistentDataType
import java.util.*

class ReputationManager : Listener {

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        this.startReputationTick()
    }

    @EventHandler
    private fun handlePlayerKillEntity(event: EntityDeathEvent) {

        val victim = event.entity
        val killer = event.damageSource.causingEntity ?: return

        (killer as? Player)?.let { player ->

            // Respect
            when (victim) {
                is Villager  -> player.changeRespect(plugin.config.getDouble("reputation-settings.respect.villager-kill") * if (victim.isAdult) 1.0 else plugin.config.getDouble("reputation-settings.formula.villager-baby-kill-multiplier"))
                is IronGolem -> player.changeRespect(plugin.config.getDouble("reputation-settings.respect.iron-golem-kill"))
                is Player    -> {} // TODO: Калькуляция значений уникальна для каждого игрока. Нужен метод.
                is Zombie    -> player.changeRespect(plugin.config.getDouble("reputation-settings.respect.zombie-kill"))
                is Illager   -> player.changeRespect(plugin.config.getDouble("reputation-settings.respect.illager-kill"))
            }

            // Fame
            when (victim) {
                is Villager    -> player.fame += plugin.config.getDouble("reputation-settings.fame.villager-kill") * if (victim.isAdult) 1.0 else plugin.config.getDouble("reputation-settings.formula.villager-baby-kill-multiplier")
                is IronGolem   -> player.fame += plugin.config.getDouble("reputation-settings.fame.iron-golem-kill")
                is Player      -> {} // TODO: Калькуляция значений уникальна для каждого игрока. Нужен метод.
                is Illager     -> player.fame += plugin.config.getDouble("reputation-settings.fame.illager-kill")
                is Wither      -> player.fame += plugin.config.getDouble("reputation-settings.fame.wither-kill")
                is EnderDragon -> player.fame += plugin.config.getDouble("reputation-settings.fame.ender-dragon-kill")
            }

        }

    }

    @EventHandler
    private fun handleRaidFinish(event: RaidFinishEvent) {
        event.winners.forEach { player ->
            player.changeRespect((plugin.config.getDouble("reputation-settings.respect.raid-finish")))
            player.fame += plugin.config.getDouble("reputation-settings.fame.raid-finish")
        }
    }

    private val searchDistance = plugin.config.getDouble("reputation-settings.respect.nearby-villagers-search-distance")
    private fun Player.changeRespect(amount: Double) {
        this.getNearbyEntities(searchDistance, searchDistance, searchDistance).filterIsInstance<Villager>().forEach { villager ->
            villager.setRespect(this, villager.getRespect(this) + amount)
        }
    }

    private fun startReputationTick() {

        plugin.server.scheduler.runTaskTimer(plugin, { _ ->

            // Iron golem look for infamous players, attacking the closest
            plugin.enabledWorlds.forEach { world ->
                world.entities.filterIsInstance<IronGolem>().forEach { ironGolem ->
                    ironGolem.target = ironGolem.getNearbyEntities(15.0, 15.0, 15.0).filterIsInstance<Player>().firstOrNull { it.gameMode == GameMode.SURVIVAL && it.fame <= -40 }
                }
            }

        }, 0, 20)
    }

    companion object {

        private val plugin = QuestIntelligence.pluginInstance

        private val respectKey: NamespacedKey = NamespacedKey(plugin, "respect")
        private val fameKey:   NamespacedKey = NamespacedKey(plugin, "fame")

        /* Subclass for managing reputation */
        class ReputationMap {

            private val reputationMap: MutableMap<UUID, Double> = mutableMapOf()

            companion object {
                private val gson = plugin.gson

                // Deserialize from a string
                fun fromString(data: String): ReputationMap {
                    val mapType = object : com.google.gson.reflect.TypeToken<MutableMap<UUID, Double>>() {}.type
                    val map = gson.fromJson<MutableMap<UUID, Double>>(data, mapType) ?: mutableMapOf()
                    val instance = ReputationMap()
                    instance.reputationMap.putAll(map)
                    return instance
                }

                // Serialize to a string
                fun toString(map: ReputationMap): String {
                    return gson.toJson(map.reputationMap)
                }
            }

            // Get reputation for a specific player
            fun getRespect(player: Player): Double {
                return reputationMap[player.uniqueId] ?: 0.0
            }

            // Set reputation for a specific player
            fun setRespect(player: Player, amount: Double) {
                reputationMap[player.uniqueId] = amount
            }
        }

        /* Extension for handling Villager reputation */
        private fun Villager.getReputationMap(): ReputationMap {
            val json = this.persistentDataContainer.getOrDefault(respectKey, PersistentDataType.STRING, "{}")
            return ReputationMap.fromString(json)
        }

        private fun Villager.setReputationMap(map: ReputationMap) {
            val json = ReputationMap.toString(map)
            this.persistentDataContainer.set(respectKey, PersistentDataType.STRING, json)
        }

        fun Villager.getRespect(player: Player): Double {
            return this.getReputationMap().getRespect(player)
        }

        fun Villager.setRespect(player: Player, amount: Double) {
            val map = this.getReputationMap()
            map.setRespect(player, amount)
            this.setReputationMap(map)
        }

        /** Fame is a global type of reputation that affects trades and interactions with each villager. */
        var Player.fame: Double
            get() = this.persistentDataContainer.getOrDefault(fameKey, PersistentDataType.DOUBLE, 0.0)
            set(amount) = this.persistentDataContainer.set(fameKey, PersistentDataType.DOUBLE, amount)

        val Player.fameLevel : Fame
            get() = if (fame < -25) Fame.INFAMOUS else if (fame > 25) Fame.FAMOUS else Fame.NEUTRAL

        enum class Fame {
            INFAMOUS, NEUTRAL, FAMOUS
        }

    }

}