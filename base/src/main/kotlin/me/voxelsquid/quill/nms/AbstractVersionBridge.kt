package me.voxelsquid.quill.nms

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Villager
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.trim.TrimPattern

abstract class AbstractVersionBridge {
    abstract val attributes: Map<UniversalAttribute, Attribute>
    abstract val trims: Map<Material, TrimPattern>
    abstract fun getOminousBanner(): ItemStack
    abstract fun consume(villager: Villager, item: ItemStack, sound: Sound, duration: Int, period: Long, onDone: () -> Unit)
    abstract fun addAttributeModifier(meta: ItemMeta, attribute: UniversalAttribute, slot: EquipmentSlotGroup, amount: Double, operation: AttributeModifier.Operation)
}

enum class UniversalAttribute {
    MAX_HEALTH, ATTACK_SPEED, ATTACK_DAMAGE, BLOCK_BREAK_SPEED, BLOCK_INTERACTION_RANGE, ENTITY_INTERACTION_RANGE, PROTECTION, ARMOR_TOUGHNESS, SCALE
}