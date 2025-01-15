package me.voxelsquid.quill.settlement

import me.voxelsquid.quill.QuestIntelligence
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Villager
import org.bukkit.util.BoundingBox
import java.util.*

class Settlement(val data: SettlementData, val villagers: MutableSet<Villager> = mutableSetOf()) {

    data class SettlementData(val worldUUID: UUID, var settlementName: String, val center: Location, var currentMayor: UUID?, val creationTime: Long)

    val creationDate = Date(data.creationTime)
    val world        = QuestIntelligence.pluginInstance.server.getWorld(data.worldUUID)!!

    var territory    = BoundingBox.of(data.center, 64.0, 64.0, 64.0)
    private val tileEntities = mutableMapOf<Material, Int>()

    private fun recountTileEntities() {

        var beds  = 0

        for (x in territory.minX.toInt()..territory.maxX.toInt()) {
            for (y in territory.minY.toInt()..territory.maxY.toInt()) {
                for (z in territory.minZ.toInt()..territory.maxZ.toInt()) {
                    val block = world.getBlockAt(x, y, z)
                    when {
                        block.type.toString().contains("_BED") -> beds++
                    }
                }
            }
        }

        tileEntities[Material.RED_BED] = beds / 2
    }

    private fun bedAmount() : Int = tileEntities[Material.RED_BED] ?: 0

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

    fun isNewArrivalPossible() : Boolean {
        this.recountTileEntities()
        return villagers.size < 20 && bedAmount() > villagers.size && world.isDayTime
    }

    enum class SettlementSize {
        UNDERDEVELOPED,
        EMERGING,
        ESTABLISHED,
        ADVANCED,
        METROPOLIS
    }

}