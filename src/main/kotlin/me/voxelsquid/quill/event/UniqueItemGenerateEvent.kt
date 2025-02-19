package me.voxelsquid.quill.event

import me.voxelsquid.quill.ai.AIProvider
import org.bukkit.entity.Villager
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class UniqueItemGenerateEvent(val villager: Villager, val item: ItemStack, val data: AIProvider.UniqueItemDescription) :
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