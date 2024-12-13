package me.voxelsquid.quill.settlement

import me.voxelsquid.quill.settlement.SettlementManager.Companion.settlementsWorldKey
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.BoundingBox
import java.util.*

// Сериализация сетлментов происходит при загрузке чанков, когда вилладжер загружается из папки мира
// У вилладжера в PDC хранится информация о поселении, к которому он принадлежит. Это значение инициализириуется, если рядом есть 10 жителей.
class Settlement(val world: World, var name: String, val center: Location, var currentMayor: UUID?, val creationDate: Date, val villagers: MutableList<Villager> = mutableListOf()) {

    private var borderBoundingBox = BoundingBox.of(center, 32.0, 32.0, 32.0)
    private val cuboidVisualizer  = CachedSettlementCuboid(world, borderBoundingBox)
    private val tileEntities      = mutableMapOf<Material, Int>()

    private fun countTileEntities() {

        // Надо будет подумать над тем, какие блоки мы будем требовать для.. эм.. зачем и какие. Да.
        var beds = 0
        var bells = 0

        for (x in borderBoundingBox.minX.toInt()..borderBoundingBox.maxX.toInt()) {
            for (y in borderBoundingBox.minY.toInt()..borderBoundingBox.maxY.toInt()) {
                for (z in borderBoundingBox.minZ.toInt()..borderBoundingBox.maxZ.toInt()) {
                    val block = world.getBlockAt(x, y, z)
                    when {
                        block.type.toString().contains("_BED") -> beds++
                        block.type == Material.BELL -> bells++
                    }
                }
            }
        }

        tileEntities[Material.RED_BED] = beds / 2
        tileEntities[Material.BELL] = bells
    }

    fun visualizeSettlementTerritory(player : Player) {
        cuboidVisualizer.showBoundingBox(player)
    }

    fun bedAmount() : Int = tileEntities[Material.RED_BED] ?: 0
    fun bellAmount() : Int = tileEntities[Material.BELL] ?: 0

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

    // Механизм сериализации, мы сохраняем сериализованный в JSON сетлемент в самом мире.
    // Стоит учесть, что поселений может быть много и у нас должен быть некий список.
    // Получается, речь идёт о массиве JSON-элементов.
    fun save() {
        world.persistentDataContainer.set(settlementsWorldKey, PersistentDataType.STRING, "")
    }

    enum class SettlementSize {
        UNDERDEVELOPED, // Неразвитое поселение
        EMERGING,       // Появляющееся поселение
        ESTABLISHED,    // Установленное поселение
        ADVANCED,       // Продвинутое поселение
        METROPOLIS      // Мегаполис
    }

}