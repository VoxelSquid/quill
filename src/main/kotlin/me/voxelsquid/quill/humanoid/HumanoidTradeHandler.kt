package me.voxelsquid.quill.humanoid

import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.quests
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.quillInventory
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.updateQuests
import me.voxelsquid.quill.humanoid.race.HumanoidRaceManager.Companion.race
import me.voxelsquid.quill.util.ItemStackCalculator.Companion.calculatePrice
import me.voxelsquid.quill.util.ItemStackCalculator.Companion.getMaterialPrice
import me.voxelsquid.quill.villager.ReputationManager.Companion.fame
import me.voxelsquid.quill.villager.ReputationManager.Companion.getRespect
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe
import java.lang.IllegalStateException

class HumanoidTradeHandler {

    companion object {

        private data class TradingSlot(var currency: Material, var amount: Int) {

            fun calculateAmount(price: Int) : Int {
                return price / currency.getMaterialPrice()
            }

            fun totalPrice() : Int {
                return currency.getMaterialPrice() * amount
            }

            fun toItemStack() : ItemStack = ItemStack(currency, amount)

        }

        private val Villager.producedItems: List<ItemStack>
            get() {
                val itemsToProduce = QuestIntelligence.pluginInstance.config.getStringList("villager-item-producing.profession.${this.profession}.item-produce")
                return quillInventory.filterNotNull().filter { itemStack -> itemsToProduce.contains(itemStack.type.toString()) }.toList()
            }

        fun Villager.openTradeMenu(player: Player) {

            // Each race has own currency.
            val race = race ?: throw IllegalStateException("Trying to open a quill trade menu for an entity without race.")
            val currency = race.normalCurrency
            val specialCurrency = race.specialCurrency

            // Clean basic merchant recipes.
            this.recipes = mutableListOf()

            // Update quests and make them first in the trade GUI.
            this.updateQuests()
            val questTrades = mutableListOf<MerchantRecipe>()
            if (quests.isNotEmpty()) {
                quests.forEach { quest ->
                    val recipe = MerchantRecipe(quest.rewardItem, 1)
                    recipe.addIngredient(quest.questItem)
                    questTrades += recipe
                }
            }

            // Going through every produced item.
            producedItems.forEach { item ->

                if (item.type == race.normalCurrency)
                    return@forEach

                // We skip adding identical trades
                if (recipes.find { recipe -> recipe.result.isSimilar(item) } != null) {
                    return@forEach
                }

                val trade = TradingSlot(Material.AIR, 0) to TradingSlot(Material.AIR, 0)

                var price = item.calculatePrice()
                val multiplier = (1.0 - 0.005 * player.fame - 0.1 * this.getRespect(player)).coerceIn(0.5, 3.0)
                price = (price.toDouble() * multiplier).toInt()

                // Use the special currency if needed.
                val useSpecialCurrency = price / currency.getMaterialPrice() > currency.maxStackSize * 2

                // First things first.
                trade.first.let { firstSlot ->

                    firstSlot.currency = if (useSpecialCurrency) specialCurrency else currency
                    val useSecondSlot = firstSlot.calculateAmount(price) > firstSlot.currency.maxStackSize

                    if (!useSecondSlot) {
                        firstSlot.amount = firstSlot.calculateAmount(price)
                    } else {
                        firstSlot.amount = firstSlot.currency.maxStackSize
                        trade.second.let { secondSlot ->
                            secondSlot.currency = if (useSpecialCurrency) specialCurrency else currency
                            secondSlot.amount   = secondSlot.calculateAmount(price - firstSlot.totalPrice())
                        }
                    }

                    if (price - firstSlot.totalPrice() > 0 && !useSecondSlot && useSpecialCurrency) {
                        trade.second.currency = currency
                        trade.second.amount   = trade.second.calculateAmount(price - firstSlot.totalPrice())
                    }
                }

                // If villager tries to sell something really cheap, skip it.
                if (trade.first.amount == 0)
                    return@forEach

                val recipe = MerchantRecipe(item, 1)
                recipe.addIngredient(trade.first.toItemStack())

                if (!trade.second.toItemStack().isEmpty)
                    recipe.addIngredient(trade.second.toItemStack())

                this.recipes = recipes + recipe
            }

            // Sort by type, then by price.
            val sorted = recipes.toMutableList().sortedWith(compareBy({ it.result.type }, { it.result.calculatePrice() }))

            if (recipes.isEmpty()) {
                this.shakeHead()
                return
                // TODO: Добавить фразы, когда у жителей нет никаких торговых сделок.
            }

            // Quest trades always first.
            recipes = questTrades + sorted

            player.openMerchant(this, false)

        }

    }

}