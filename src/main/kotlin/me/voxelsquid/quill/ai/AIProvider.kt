package me.voxelsquid.quill.ai

import me.voxelsquid.quill.settlement.Settlement
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import me.voxelsquid.quill.quest.QuestManager
import me.voxelsquid.quill.quest.data.VillagerQuest

interface AIProvider {
    fun generateTranslation()
    fun generateSettlementName(settlement: Settlement)
    fun generatePersonalHumanoidData(entity: LivingEntity)
    fun generateUniqueItemDescription(villager: Villager, item: ItemStack)
    fun generateQuestData(questManager: QuestManager, villager: Villager, quest: VillagerQuest.Builder)
    fun createJsonRequest(prompt: String): String
}
