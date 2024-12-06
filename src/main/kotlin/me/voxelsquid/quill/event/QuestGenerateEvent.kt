package me.voxelsquid.quill.event

import me.voxelsquid.quill.quest.data.VillagerQuest
import org.bukkit.entity.Villager
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class QuestGenerateEvent(val villager: Villager, val quest: VillagerQuest) :
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