package me.voxelsquid.quill.event

import me.voxelsquid.quill.ai.GeminiProvider
import me.voxelsquid.quill.settlement.Settlement
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class SettlementNameGenerateEvent(val settlement: Settlement, val data: GeminiProvider.SettlementInformation) :
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