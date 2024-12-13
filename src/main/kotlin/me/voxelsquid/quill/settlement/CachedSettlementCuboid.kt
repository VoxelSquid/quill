package me.voxelsquid.quill.settlement

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox

class CachedSettlementCuboid(private val world: World, private val boundingBox: BoundingBox) {

    private val particleLocations: MutableList<Location> = mutableListOf()

    init {
        this.cacheParticleLocations()
    }

    private fun cacheParticleLocations() {
        val minX = boundingBox.minX
        val minY = boundingBox.minY
        val minZ = boundingBox.minZ
        val maxX = boundingBox.maxX
        val maxY = boundingBox.maxY
        val maxZ = boundingBox.maxZ

        // Кэширование партиклей для углов
        val corners = listOf(
            Location(world, minX, minY, minZ),
            Location(world, minX, minY, maxZ),
            Location(world, minX, maxY, minZ),
            Location(world, minX, maxY, maxZ),
            Location(world, maxX, minY, minZ),
            Location(world, maxX, minY, maxZ),
            Location(world, maxX, maxY, minZ),
            Location(world, maxX, maxY, maxZ)
        )

        particleLocations.addAll(corners)

        // Добавляем партикли по всем граням кубоида
        // Граница по оси X
        for (y in minY.toInt()..maxY.toInt()) {
            for (z in minZ.toInt()..maxZ.toInt()) {
                particleLocations.add(Location(world, minX, y.toDouble(), z.toDouble())) // Лицевая плоскость
                particleLocations.add(Location(world, maxX, y.toDouble(), z.toDouble())) // Задняя плоскость
            }
        }

        // Граница по оси Y
        for (x in minX.toInt()..maxX.toInt()) {
            for (z in minZ.toInt()..maxZ.toInt()) {
                particleLocations.add(Location(world, x.toDouble(), minY, z.toDouble())) // Нижняя плоскость
                particleLocations.add(Location(world, x.toDouble(), maxY, z.toDouble())) // Верхняя плоскость
            }
        }

        // Граница по оси Z
        for (x in minX.toInt()..maxX.toInt()) {
            for (y in minY.toInt()..maxY.toInt()) {
                particleLocations.add(Location(world, x.toDouble(), y.toDouble(), minZ)) // Передняя плоскость
                particleLocations.add(Location(world, x.toDouble(), y.toDouble(), maxZ)) // Задняя плоскость
            }
        }
    }

    fun showBoundingBox(player: Player) {
        val world: World = player.world

        // Генерируем партикли из кэша
        particleLocations.forEach { loc ->
            world.spawnParticle(Particle.FLAME, loc, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }
}