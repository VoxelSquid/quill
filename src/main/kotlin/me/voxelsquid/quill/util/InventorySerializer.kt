package me.voxelsquid.quill.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.spongepowered.configurate.yaml.internal.snakeyaml.external.biz.base64Coder.Base64Coder

class InventorySerializer {

    companion object {

        fun jsonifyInventory(inventory: Inventory): JsonObject {
            val obj = JsonObject()

            obj.addProperty("size", inventory.size)

            val items = JsonArray()
            for (i in 0 until inventory.size) {
                val item: ItemStack? = inventory.getItem(i)
                if (item != null) {
                    val jitem = JsonObject()
                    jitem.addProperty("slot", i)
                    val itemData: String = serializeItemStack(item)
                    jitem.addProperty("data", itemData)
                    items.add(jitem)
                }
            }
            obj.add("items", items)

            return obj
        }

        fun dejsonifyInventory(jsonInventory: String): Inventory {
            val jsonObject = JsonParser.parseString(jsonInventory).asJsonObject
            val inventory: Inventory = Bukkit.createInventory(null, jsonObject["size"].asInt)
            val items = jsonObject["items"].asJsonArray
            for (element in items) {
                val jsonItem = element.asJsonObject
                val item: ItemStack = deserializeItemStack(jsonItem["data"].asString)
                inventory.setItem(jsonItem["slot"].asInt, item)
            }
            return inventory
        }

        /** Base64 serialization. */
        fun serializeItemStack(item: ItemStack): String {
            return Base64Coder.encodeLines(item.serializeAsBytes())
        }

        /** Base64 deserialization. */
        fun deserializeItemStack(base64: String): ItemStack {
            return ItemStack.deserializeBytes(Base64Coder.decodeLines(base64))
        }

    }

}