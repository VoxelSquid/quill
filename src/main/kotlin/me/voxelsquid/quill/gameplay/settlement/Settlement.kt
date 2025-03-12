package me.voxelsquid.quill.gameplay.settlement

import me.voxelsquid.quill.QuestIntelligence
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.util.BoundingBox
import java.util.*

class Settlement(val data: SettlementData, val villagers: MutableSet<Villager> = mutableSetOf()) {

    data class SettlementData(val worldUUID: UUID, var settlementName: String, val center: Location, val creationTime: Long, val reputation: MutableMap<UUID, Int> = mutableMapOf())

    val creationDate = Date(data.creationTime)
    val world        = QuestIntelligence.pluginInstance.server.getWorld(data.worldUUID)!!
    var territory    = BoundingBox.of(data.center, 64.0, 64.0, 64.0)

    fun size(): SettlementSize {
        return when {
            villagers.size in 1..10 -> SettlementSize.UNDERDEVELOPED
            villagers.size in 11..20 -> SettlementSize.EMERGING
            villagers.size in 21..30 -> SettlementSize.ESTABLISHED
            villagers.size in 31..50 -> SettlementSize.ADVANCED
            villagers.size > 50 -> SettlementSize.METROPOLIS
            else -> SettlementSize.UNDERDEVELOPED
        }
    }

    fun getPlayerReputation(player: Player): Int = data.reputation[player.uniqueId] ?: 0

    enum class SettlementSize {
        UNDERDEVELOPED,
        EMERGING,
        ESTABLISHED,
        ADVANCED,
        METROPOLIS
    }

}