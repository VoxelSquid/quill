package me.voxelsquid.quill.event

import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidController
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class HumanoidInitializationEvent(val player: Player, val entity: LivingEntity, val humanoidProvider: HumanoidController, val metadata: List<EntityData>) :
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