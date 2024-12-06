package me.voxelsquid.quill.event

import me.voxelsquid.quill.villager.VillagerManager
import org.bukkit.entity.Villager
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class VillagerDataGenerateEvent(val villager: Villager, val data: VillagerManager.PersonalVillagerData) :
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