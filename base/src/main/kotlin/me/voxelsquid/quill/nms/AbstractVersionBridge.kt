package me.voxelsquid.quill.nms

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.trim.TrimPattern

abstract class AbstractVersionBridge {
    abstract val trims: Map<Material, TrimPattern>
    abstract fun getOminousBanner() : ItemStack
    abstract fun consume(villager: Villager, item: ItemStack, sound: Sound, duration: Int, period: Long, onDone: () -> Unit)
}