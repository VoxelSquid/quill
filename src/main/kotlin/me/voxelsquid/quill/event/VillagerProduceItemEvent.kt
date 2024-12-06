package me.voxelsquid.quill.event

import org.bukkit.entity.Villager
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class VillagerProduceItemEvent(val villager: Villager, val producedItem: ItemStack) :
    Event() {

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    companion object {

        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }

}