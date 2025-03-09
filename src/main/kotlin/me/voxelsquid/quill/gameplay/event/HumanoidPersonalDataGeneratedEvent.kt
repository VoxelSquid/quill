package me.voxelsquid.quill.gameplay.event

import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class HumanoidPersonalDataGeneratedEvent(val entity: LivingEntity, val personalData: HumanoidManager.PersonalHumanoidData) : Event() {

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