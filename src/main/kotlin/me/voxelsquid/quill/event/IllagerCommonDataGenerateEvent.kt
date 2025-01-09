package me.voxelsquid.quill.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

data class IllagerCommonData(val illagerNameList: MutableList<String>, val illagerInteractionPhrases: MutableList<String>, val illagerHurtPhrases: MutableList<String>, val partyLeaderInteraction: MutableList<String>)
class IllagerCommonDataGenerateEvent(val data: IllagerCommonData) :
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