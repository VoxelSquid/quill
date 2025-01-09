package me.voxelsquid.quill.util

import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.illager.IllagerManager
import me.voxelsquid.quill.illager.IllagerManager.Companion
import me.voxelsquid.quill.nms.VersionProvider.Companion.ominousBanner
import me.voxelsquid.quill.villager.ReputationManager.Companion.fame
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack

class BannerHelmetHandler : Listener {

    companion object {
        private const val HELMET_SLOT_INDEX = 5
        private val plugin = QuestIntelligence.pluginInstance
        private val requiredItem: ItemStack = ominousBanner.clone()
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {

        val player = event.whoClicked as Player

        // Получаем предмет, находящийся на курсоре игрока
        val cursorItem = event.cursor // Безопасное получение предмета с курсора

        // Если на курсоре нет предмета, выходим из обработчика
        if (cursorItem.type == Material.AIR) return

        // Проверяем, что взаимодействие происходит со слотом брони
        if (event.slotType != InventoryType.SlotType.ARMOR) return

        // Проверяем, что клик произошел именно по слоту шлема
        if (event.rawSlot != HELMET_SLOT_INDEX) return

        // Получаем предмет, находящийся в слоте шлема до взаимодействия
        val itemInSlot = event.currentItem

        // Если в курсоре баннер (и игрок достаточно злобный), то только тогда пытаемся что-то сделать
        if (player.fame <= -40 && cursorItem.isSimilar(ominousBanner)) {
            when (event.click) {
                ClickType.RIGHT -> itemInSlot?.let { handleRightClick(event, it, cursorItem) } ?: handleRightClick(event, ItemStack(
                    Material.AIR), cursorItem) // Обработка правого клика
                ClickType.LEFT -> handleLeftClick(event, itemInSlot, cursorItem) // Обработка левого клика
                ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> event.isCancelled = true // Отмена Shift + клик
                else -> {
                    // Для всех остальных типов кликов пытаемся поставить предмет на голову, если разрешено
                    event.isCancelled = true
                    if (cursorItem.isSimilar(requiredItem)) {
                        event.currentItem = cursorItem
                        event.whoClicked.setItemOnCursor(itemInSlot)
                    }
                }
            }
        }

        // В случае успешного надевания баннера на голову
        if (itemInSlot?.type == Material.AIR && event.currentItem?.isSimilar(ominousBanner) == true) {
            plugin.language?.let { language ->
                language.getString("illager-party.banner-is-on-head")?.let { message ->
                    player.sendMessage(QuestIntelligence.messagePrefix + message)
                }
            }
        }
    }

    private fun handleRightClick(event: InventoryClickEvent, itemInSlot: ItemStack, cursorItem: ItemStack) {
        // Отменяем стандартное поведение клика
        event.isCancelled = true

        // Создаем копии предметов для сравнения без учета количества
        val singleItemInSlot = if (itemInSlot.type != Material.AIR) itemInSlot.clone().apply { amount = 1 } else ItemStack(
            Material.AIR)
        val singleCursorItem = cursorItem.clone().apply { amount = 1 }

        // Если типы предметов не совпадают или предмет в слоте пустой, просто меняем их местами, если предмет на курсоре разрешен
        if (singleItemInSlot.type == Material.AIR || singleItemInSlot != singleCursorItem) {
            if (cursorItem.isSimilar(requiredItem)) {
                event.currentItem = cursorItem
                event.whoClicked.setItemOnCursor(itemInSlot)
            }
            return
        }

        // Если предметы одного типа, пытаемся добавить один предмет с курсора в слот
        val cursorAmount = cursorItem.amount
        // Уменьшаем количество предметов на курсоре на 1
        cursorItem.amount = cursorAmount - 1
        // Увеличиваем количество предметов в слоте на 1
        itemInSlot.amount += 1
        event.currentItem = itemInSlot

    }

    private fun handleLeftClick(event: InventoryClickEvent, itemInSlot: ItemStack?, cursorItem: ItemStack) {
        // Отменяем стандартное поведение клика
        event.isCancelled = true

        // Проверяем, разрешен ли предмет на курсоре для надевания
        if (!cursorItem.isSimilar(requiredItem)) {
            return
        }

        // Если в слоте нет предмета, просто ставим предмет с курсора
        if (itemInSlot == null || itemInSlot.type == Material.AIR) {
            event.currentItem = cursorItem
            event.whoClicked.setItemOnCursor(null) // Очищаем курсор
            return
        }

        // Создаем копии предметов для сравнения без учета количества
        val singleItemInSlot = itemInSlot.clone().apply { amount = 1 }
        val singleCursorItem = cursorItem.clone().apply { amount = 1 }

        // Если типы предметов не совпадают, просто меняем их местами
        if (singleItemInSlot != singleCursorItem) {
            // Проверяем, разрешен ли предмет в слоте для надевания (на случай, если меняем с чем-то другим)
            if (itemInSlot.isSimilar(requiredItem)) {
                event.currentItem = cursorItem
                event.whoClicked.setItemOnCursor(itemInSlot)
            }
            return
        }

        // Если предметы одного типа, пытаемся объединить их в слоте
        val totalAmount = itemInSlot.amount + cursorItem.amount
        val maxStackSize = itemInSlot.maxStackSize // Получаем максимальный размер стака для данного типа предмета

        // Если общее количество превышает максимальный размер стака
        if (totalAmount > maxStackSize) {
            // Рассчитываем, сколько предметов можно добавить в слот
            val canAdd = maxStackSize - itemInSlot.amount
            // Устанавливаем максимальное количество в слоте
            itemInSlot.amount = maxStackSize
            // Уменьшаем количество на курсоре на добавленное количество
            cursorItem.amount -= canAdd
            // Обновляем курсор и слот
            event.currentItem = itemInSlot
            event.whoClicked.setItemOnCursor(cursorItem)
            return
        }

        // Если общее количество не превышает максимальный размер стака, добавляем все предметы с курсора в слот
        itemInSlot.amount = totalAmount
        cursorItem.amount = 0

        // Обновляем курсор и слот
        event.currentItem = itemInSlot
        event.whoClicked.setItemOnCursor(cursorItem)

    }
}