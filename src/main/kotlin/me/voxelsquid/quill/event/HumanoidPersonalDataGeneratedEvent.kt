package me.voxelsquid.quill.event

import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidController.PersonalHumanoidData
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class HumanoidPersonalDataGeneratedEvent(val entity: LivingEntity, val personalData: PersonalHumanoidData) : Event() {

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