@file:Suppress("DEPRECATION")

package me.voxelsquid.quill.settlement

import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.isChristmas
import me.voxelsquid.quill.nms.UniversalAttribute
import me.voxelsquid.quill.nms.VersionProvider.Companion.universalAttribute
import me.voxelsquid.quill.util.ItemStackCalculator.Companion.calculatePrice
import me.voxelsquid.quill.util.ItemStackCalculator.Companion.setMeta
import me.voxelsquid.quill.villager.VillagerManager.Companion.character
import me.voxelsquid.quill.villager.VillagerManager.Companion.foodAmount
import me.voxelsquid.quill.villager.VillagerManager.Companion.hunger
import me.voxelsquid.quill.villager.VillagerManager.Companion.professionLevelName
import me.voxelsquid.quill.villager.VillagerManager.Companion.quests
import me.voxelsquid.quill.villager.VillagerManager.Companion.quillInventory
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import org.bukkit.util.BoundingBox
import org.ipvp.canvas.Menu
import org.ipvp.canvas.mask.BinaryMask
import org.ipvp.canvas.mask.Mask
import org.ipvp.canvas.paginate.PaginatedMenuBuilder
import org.ipvp.canvas.slot.ClickOptions
import org.ipvp.canvas.type.ChestMenu
import java.util.*

// Сериализация сетлментов происходит при загрузке чанков, когда вилладжер загружается из папки мира
// У вилладжера в PDC хранится информация о поселении, к которому он принадлежит. Это значение инициализириуется, если рядом есть 10 жителей.
class Settlement(val data: SettlementData, val villagers: MutableSet<Villager> = mutableSetOf()) {

    data class SettlementData(val worldUUID: UUID, var settlementName: String, val center: Location, var currentMayor: UUID?, val creationTime: Long, var visibilityState: Boolean)

    val creationDate = Date(data.creationTime)
    val world        = QuestIntelligence.pluginInstance.server.getWorld(data.worldUUID)!!
    var territory    = BoundingBox.of(data.center, 64.0, 64.0, 64.0)

    var arrivalPossibility = true

    private val cuboidVisualizer  = CachedSettlementCuboid(world, territory)
    private val tileEntities      = mutableMapOf<Material, Int>()

    private fun recountTileEntities() {

        // Надо будет подумать над тем, какие блоки мы будем требовать для.. эм.. зачем и какие. Да.
        var beds  = 0

        for (x in territory.minX.toInt()..territory.maxX.toInt()) {
            for (y in territory.minY.toInt()..territory.maxY.toInt()) {
                for (z in territory.minZ.toInt()..territory.maxZ.toInt()) {
                    val block = world.getBlockAt(x, y, z)
                    when {
                        block.type.toString().contains("_BED") -> beds++
                    }
                }
            }
        }

        tileEntities[Material.RED_BED] = beds / 2
    }

    fun openControlPanelMenu(player: Player) {

        val language = plugin.language ?: return

        this.recountTileEntities()
        val settlementMenu = ChestMenu.builder(3).title(data.settlementName).build()

        fun dispatchPlaceholders(lore: List<String>) : List<String> {

            val placeholders = mapOf(
                "villagerAmount"          to villagers.size.toString(),
                "visibilityState"         to if (data.visibilityState) language.getString("settlement-menu.visibility-state.visible")!! else language.getString("settlement-menu.visibility-state.hidden")!!,
                "totalFoodAmount"         to villagers.sumOf { it.foodAmount }.toString(),
                "starvingVillagersAmount" to villagers.count { it.foodAmount == 0 }.toString(),
                "bedAmount"               to bedAmount().toString(),
                "arrivalPossibility"      to if (arrivalPossibility) language.getString("settlement-menu.arrival-possibility.possible")!! else language.getString("settlement-menu.arrival-possibility.impossible")!!
            )

            return lore.map { line ->
                placeholders.entries.fold(line) { acc, entry ->
                    acc.replace("{${entry.key}}", entry.value)
                }
            }
        }

        settlementMenu.slots.forEach {
            it.clickOptions = ClickOptions.DENY_ALL
        }

        // Villager head, clickable, should open another menu which will contains all villager residents with information and stuff.
        // Shows info about mood, health and other stuff.
        settlementMenu.getSlot(0).apply {
            val texture = if (isChristmas()) "f0ebd377fe84e00b824882a6c6e96c95d2356ff94a1f1d9a4dda2e1e4fef1ff6" else "25fafa2be55bd15aea6e2925f5d24f8068e0f4a2616f3b92b380d94912f0ec5f"
            val item = CustomHead().texture(texture).setMeta(language.getString("settlement-menu.villagers-button")!!, dispatchPlaceholders(language.getStringList("settlement-menu.villagers-button-lore")))
            item.amount = villagers.size
            this.item = item

            if (item.amount != 0) {
                this.setClickHandler { player, _ ->
                    openVillagerListMenu(player)
                }
            } else item.amount = 1
        }

        // Ender eye, clickable (requires reputation to interact). Villagers will come into the village when some conditions are met.
        settlementMenu.getSlot(8).apply {
            fun createButtonItem() = ItemStack(if (data.visibilityState) Material.ENDER_EYE else Material.ENDER_PEARL).setMeta(language.getString("settlement-menu.visibility-button")!!, dispatchPlaceholders(language.getStringList("settlement-menu.visibility-button-lore")))
            this.item = createButtonItem()
            this.setClickHandler { player, slot ->
                // TODO player status (and rep) check
                data.visibilityState = !data.visibilityState
                this.item = createButtonItem()
            }
        }

        // Book, clickable. Contains policies and history data. One of the most important buttons.
        settlementMenu.getSlot(9).apply {
            this.item = ItemStack(Material.BOOK).setMeta(language.getString("settlement-menu.policies-button")!!, dispatchPlaceholders(language.getStringList("settlement-menu.policies-button-lore")))
        }

        // Bell button. Clickable. Should open a menu.
        settlementMenu.getSlot(13).apply {
            this.item = ItemStack(Material.BELL).setMeta(language.getString("settlement-menu.actions-button")!!, dispatchPlaceholders(language.getStringList("settlement-menu.actions-button-lore")))
        }

        // Food info button. Not clickable. Shows info about food.
        settlementMenu.getSlot(17).apply {
            this.item = ItemStack(Material.BREAD).setMeta(language.getString("settlement-menu.food-button")!!, dispatchPlaceholders(language.getStringList("settlement-menu.food-button-lore")))
        }

        // Workbench button. Shows info about.. uh.. economy? I don't know.
        settlementMenu.getSlot(18).apply {
            this.item = ItemStack(Material.CRAFTING_TABLE).setMeta(language.getString("settlement-menu.craft-button")!!, dispatchPlaceholders(language.getStringList("settlement-menu.craft-button-lore")))
        }

        // Well, that's obvious. Not clickable.
        settlementMenu.getSlot(26).apply {
            this.item = ItemStack(Material.RED_BED).setMeta(language.getString("settlement-menu.beds-button")!!, dispatchPlaceholders(language.getStringList("settlement-menu.beds-button-lore")))
        }

        settlementMenu.open(player)
    }

    private val villagerHead = CustomHead().texture("b879e3661b50f20317fea11a4e775c80c0559c1242e655f81680e08a4ede3432")
    private fun openVillagerListMenu(player: Player) {

        val language = plugin.language ?: return

        fun createVillagerHead(villager: Villager) : ItemStack {

            val villagerName = villager.customName ?: "Villager"
            val placeholders = mapOf(
                "profession"      to villager.profession.key.value(),
                "professionLevel" to villager.professionLevelName,
                "personality"     to villager.character.toString(),
                "hunger"          to "${villager.hunger}/20",
                "health"          to "${villager.health}/${villager.getAttribute(universalAttribute(UniversalAttribute.MAX_HEALTH)!!)?.value}",
                "networth"        to villager.quillInventory.filterNotNull().toList().calculatePrice().toString(),
                "quests"          to "${villager.quests.size}"
            )

            val lore = language.getStringList("settlement-menu.villager-info").map { line ->
                placeholders.entries.fold(line) { acc, entry ->
                    acc.replace("{${entry.key}}", entry.value)
                }
            }

            return villagerHead.clone().setMeta("§6$villagerName", lore)
        }

        // Создаём предметы (головы жителей с инфой о них), помещаем их в лист
        val villagers = mutableListOf<ItemStack>()
        this.villagers.forEach { villager ->
            villagers.add(createVillagerHead(villager))
        }

        // Создаём страничное меню
        val rows = if (villagers.size > 8) 2 else 1
        val pageTemplate = ChestMenu.builder(rows).title(language.getString("settlement-menu.villagers-menu-title")!!).redraw(true)
        val itemSlots: Mask = BinaryMask.builder(pageTemplate.dimensions).pattern("111111111").build()
        val pages: List<Menu> = PaginatedMenuBuilder.builder(pageTemplate)
            .slots(itemSlots)
            .previousButton(ItemStack(Material.ARROW))
            .previousButtonEmpty(ItemStack(Material.AIR))
            .previousButtonSlot(9)
            .nextButton(ItemStack(Material.ARROW))
            .nextButtonEmpty(ItemStack(Material.AIR))
            .nextButtonSlot(17)
            .addItems(villagers)
            .build()

        pages.first().open(player)
    }

    fun visualizeSettlementTerritory(player : Player) {
        cuboidVisualizer.showBoundingBox(player)
    }

    private fun bedAmount() : Int = tileEntities[Material.RED_BED] ?: 0

    fun size(): SettlementSize {
        return when {
            villagers.size in 1..10 -> SettlementSize.UNDERDEVELOPED
            villagers.size in 11..20 -> SettlementSize.EMERGING
            villagers.size in 21..30 -> SettlementSize.ESTABLISHED
            villagers.size in 31..50 -> SettlementSize.ADVANCED
            villagers.size > 50 -> SettlementSize.METROPOLIS
            else -> SettlementSize.UNDERDEVELOPED
        }
    }

    private companion object {
        private val plugin = QuestIntelligence.pluginInstance
    }

    enum class SettlementSize {
        UNDERDEVELOPED, // Неразвитое поселение
        EMERGING,       // Появляющееся поселение
        ESTABLISHED,    // Установленное поселение
        ADVANCED,       // Продвинутое поселение
        METROPOLIS      // Мегаполис
    }

}