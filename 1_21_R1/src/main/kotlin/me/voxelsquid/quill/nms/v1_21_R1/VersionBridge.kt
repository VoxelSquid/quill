package me.voxelsquid.quill.nms.v1_21_R1

import me.voxelsquid.quill.nms.AbstractVersionBridge
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.raid.Raid
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.entity.CraftVillager
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.trim.TrimPattern
import org.bukkit.plugin.java.JavaPlugin

// 1.21.1
class VersionBridge(private val plugin: JavaPlugin) : AbstractVersionBridge() {

    override val trims: Map<Material, TrimPattern>
        get() = mutableMapOf<Material, TrimPattern>().apply {
            put(Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.RAISER)
            put(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.COAST)
            put(Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.RIB)
            put(Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.VEX)
            put(Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.EYE)
            put(Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.BOLT)
            put(Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.DUNE)
            put(Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.FLOW)
            put(Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.HOST)
            put(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.SENTRY)
            put(Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.SHAPER)
            put(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.SILENCE)
            put(Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.WILD)
            put(Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.WARD)
            put(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.TIDE)
            put(Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.WAYFINDER)
            put(Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE, TrimPattern.SNOUT)
        }

    override fun getOminousBanner() : ItemStack {
        return CraftItemStack.asBukkitCopy(
            Raid.getLeaderBannerInstance((plugin.server.worlds.random() as CraftWorld).handle.registryAccess().lookupOrThrow(
                Registries.BANNER_PATTERN)))
    }

    override fun consume(
        villager: Villager,
        item: ItemStack,
        sound: Sound,
        duration: Int,
        period: Long,
        onDone: () -> Unit,
    ) {
        val nmsVillager = (villager as CraftVillager).handle
        val nmsItem = CraftItemStack.asNMSCopy(item)

        var i = 0

        plugin.server.scheduler.runTaskTimer(plugin, { task ->

            nmsVillager.isNoAi = true
            nmsVillager.setItemSlot(EquipmentSlot.MAINHAND, nmsItem)
            villager.world.playSound(villager.location, sound, 1F, 1F)

            if (i++ >= duration) {
                nmsVillager.setItemSlot(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY)
                onDone.invoke()
                nmsVillager.isNoAi = false
                task.cancel()
            }

        }, 0, period)
    }

}