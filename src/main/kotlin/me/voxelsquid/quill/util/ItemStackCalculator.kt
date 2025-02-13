package me.voxelsquid.quill.util

import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.getOminousBanner
import me.voxelsquid.quill.villager.ProfessionManager.Companion.getUniqueItemRarity
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class ItemStackCalculator {

    companion object {

        private val plugin = QuestIntelligence.pluginInstance

        fun Collection<ItemStack>.calculatePrice(): Int {
            return this.filterNot{ it.type.isEdible || it.type == Material.ENCHANTED_BOOK || it.isSimilar(
                getOminousBanner()
            ) }.sumOf { it.calculatePrice() }
        }

        /** Calculates stack price. */
        fun ItemStack.calculatePrice(): Int {

            if (this.isSimilar(getOminousBanner()))
                return plugin.configurationClip.promptsConfig.getInt("ominous-banner-quest.reward-points")

            return (this.type.getMaterialPrice() * this.amount + this.getUniqueItemRarity().extraPrice)
        }

        fun Material.getMaterialPrice(defaultPrice: Int = 50): Int {

            val pricingConfig = plugin.configurationClip.pricesConfig

            return if (pricingConfig.contains(this.name))
                pricingConfig.getInt(this.name)
            else defaultPrice.also {
                plugin.logger.info("Price for material $this not found. Updating configuration... Default price is $defaultPrice.")
                plugin.logger.info("Configuration reloaded automatically.")
                pricingConfig.set(this.name, "$it # Default price! Make sure to check it.")
                plugin.reloadConfigurations()
            }
        }

        fun ItemStack.setMeta(itemName: String, lore: List<String>): ItemStack {
            itemMeta = itemMeta?.apply {
                setDisplayName(itemName)
                this.lore = lore
            }
            return this
        }

    }

}