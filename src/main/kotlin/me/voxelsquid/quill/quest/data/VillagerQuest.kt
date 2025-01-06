package me.voxelsquid.quill.quest.data

import com.google.gson.*
import me.voxelsquid.quill.util.InventorySerializer
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Type

data class VillagerQuest (val type: QuestType, val questItem: ItemStack, var rewardItem: ItemStack, val questInfo: QuestInfo, var timeCreated: Long, val rewardPrice: Int) {

    data class QuestInfo(val twoWordsDescription: String, val questDescriptionForFamousPlayer: String, val questDescriptionForNeutralPlayer: String, val questDescriptionForInfamousPlayer: String, val rewardTextForFamousPlayer: String, val rewardTextForNeutralPlayer: String, val rewardTextForInfamousPlayer: String)

    class Builder {

        lateinit var questType:  QuestType
        lateinit var questItem:  ItemStack
        lateinit var rewardItem: ItemStack
        private lateinit var questInfo:  QuestInfo
                 private var rewardPrice = 0

        fun setQuestType(questType: QuestType)   = apply { this.questType = questType }
        fun setQuestItem(questItem: ItemStack)   = apply { this.questItem = questItem }
        fun setRewardItem(rewardItem: ItemStack) = apply { this.rewardItem = rewardItem }
        fun setQuestInfo(questInfo: QuestInfo)   = apply { this.questInfo = questInfo }
        fun setRewardPrice(price: Int)           = apply { this.rewardPrice = price }

        fun build(): VillagerQuest {
            return VillagerQuest(
                questType,
                questItem,
                rewardItem,
                questInfo,
                System.currentTimeMillis(),
                rewardPrice
            )
        }
    }

    class VillagerQuestAdapter : JsonSerializer<VillagerQuest>, JsonDeserializer<VillagerQuest> {

        override fun serialize(
            src: VillagerQuest,
            typeOfSrc: Type,
            context: JsonSerializationContext
        ): JsonElement {
            val jsonObject = JsonObject()

            jsonObject.add("type", context.serialize(src.type))

            // Использование ваших функций для сериализации в Base64
            jsonObject.addProperty("questItem", InventorySerializer.serializeItemStack(src.questItem))
            jsonObject.addProperty("rewardItem", InventorySerializer.serializeItemStack(src.rewardItem))

            jsonObject.add("questInfo", context.serialize(src.questInfo))
            jsonObject.add("rewardPrice", context.serialize(src.rewardPrice))

            return jsonObject
        }

        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): VillagerQuest {
            val jsonObject = json.asJsonObject

            val type = context.deserialize<QuestType>(jsonObject.get("type"), QuestType::class.java)

            // Использование ваших функций для десериализации из Base64
            val questItem = InventorySerializer.deserializeItemStack(jsonObject.get("questItem").asString)
            val rewardItem = InventorySerializer.deserializeItemStack(jsonObject.get("rewardItem").asString)

            val questInfo = context.deserialize<QuestInfo>(jsonObject.get("questInfo"), QuestInfo::class.java)
            val rewardPrice = context.deserialize<Int>(jsonObject.get("rewardPrice"), Int::class.java)

            return Builder()
                .setQuestType(type)
                .setQuestItem(questItem)
                .setRewardItem(rewardItem)
                .setQuestInfo(questInfo)
                .setRewardPrice(rewardPrice)
                .build()
        }
    }

}