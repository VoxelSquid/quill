package me.voxelsquid.quill.villager.interaction

import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.dialogueFormat
import me.voxelsquid.quill.QuestIntelligence.Companion.sendTutorialMessage
import me.voxelsquid.quill.QuestIntelligence.TutorialMessage
import me.voxelsquid.quill.villager.VillagerManager.Companion.openTradeMenu
import me.voxelsquid.quill.villager.VillagerManager.Companion.personalData
import me.voxelsquid.quill.villager.VillagerManager.Companion.quests
import me.voxelsquid.quill.villager.VillagerManager.Companion.talk
import me.voxelsquid.quill.villager.VillagerManager.Companion.voicePitch
import me.voxelsquid.quill.villager.VillagerManager.Companion.voiceSound
import me.voxelsquid.quill.villager.interaction.DialogueManager.Companion.dialogues
import me.voxelsquid.quill.villager.interaction.MenuManager.Companion.openedMenuList
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.block.data.type.Bed
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.entity.CraftVillager
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f

class MenuManager(private val plugin: QuestIntelligence): Listener {

    private val buttonTextColor = TextColor.fromHexString(plugin.config.getString("core-settings.menu-button-text-color")!!)!!

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            openedMenuList.toList().forEach(InteractionMenu::relocate)
        }, 0L, 1L)
    }

    companion object {
        val openedMenuList: MutableList<InteractionMenu> = mutableListOf()
    }

    @EventHandler
    private fun whenVillagerDies(event: EntityDeathEvent) {
        (event.entity as? Villager)?.let { villager ->
            openedMenuList.filter { it.villager == villager }.forEach(InteractionMenu::destroy)
            dialogues.values.filter { it.villager == villager }.forEach(DialogueManager.DialogueWindow::destroy)
        }
    }

    @EventHandler
    private fun handleVillagerInteraction(event: PlayerInteractEntityEvent) {
        (event.rightClicked as? Villager)?.let { villager ->

            val player: Player = event.player

            // Отмена стандартного события
            event.isCancelled = true

            // Обработка повторного нажатия в случае наличия уже открытого меню
            openedMenuList.find { it.viewer == player }?.let { menu ->
                menu.invokeSelected()
                menu.destroy()
                (villager as CraftVillager).handle.tradingPlayer = null
                return
            }

            // Меню не должно открываться, если житель уже что-то говорит
            if (dialogues.containsKey(player to villager)) {
                return
            }

            // Обработка состояния спящего жителя
            if (villager.pose == Pose.SLEEPING) {
                villager.personalData?.let {
                    villager.talk(player, it.sleepInterruptionMessages.random(), followDuringDialogue = false)
                    player.sendTutorialMessage(TutorialMessage.SLEEP_INTERRUPTION)
                }
                return
            }

            player.sendTutorialMessage(TutorialMessage.VILLAGER_INTERACTION)
            player.inventory.heldItemSlot = 4
            this.showDefaultMenu(player, villager)
        }
    }

    @EventHandler
    private fun handlePlayerQuit(event: PlayerQuitEvent) {
        openedMenuList.removeIf { it.viewer == event.player}
    }

    private fun showDefaultMenu(player: Player, villager: Villager) {

        val builder = Builder(villager, player)

        builder.button(Component.text(plugin.language!!.getString("interaction-menu.quests-button")!!).color(buttonTextColor)) {

            if (villager.profession == Villager.Profession.NONE) {
                villager.personalData?.let {
                    villager.talk(player, it.joblessMessages.random(), followDuringDialogue = true)
                    return@button
                }
            }

            if (villager.quests.isNotEmpty()) {
                this.showQuestListMenu(player, villager)
            }
        }

        builder.button(Component.text(plugin.language!!.getString("interaction-menu.trade-button")!!).color(buttonTextColor)) {
            plugin.server.scheduler.runTaskLater(plugin, { _ -> villager.openTradeMenu(player) }, 1L)
            player.sendTutorialMessage(TutorialMessage.QUESTING)
        }

        builder.button(Component.text(plugin.language!!.getString("interaction-menu.actions-button")!!).color(buttonTextColor)) {
            this.showActionMenu(player, villager)
        }

        builder.button(Component.text(plugin.language!!.getString("interaction-menu.close-button")!!).color(buttonTextColor)) { menu ->
            menu.destroy()
        }

        builder.build()
    }

    private fun showActionMenu(player: Player, villager: Villager) {

        val builder = Builder(villager, player)
        builder.button(Component.text(plugin.language!!.getString("interaction-menu.order-button")!!).color(buttonTextColor)) {
            player.sendMessage("It's not implemented yet. §4:(") // TODO заказы вещей в зависимости от профессии
        }

        builder.button(Component.text(plugin.language!!.getString("interaction-menu.talk-button")!!).color(buttonTextColor)) {
            player.sendMessage("It's not implemented yet. §4:(") // TODO генерация фраз при генерации квеста
        }

        builder.button(Component.text(plugin.language!!.getString("interaction-menu.gift-button")!!).color(buttonTextColor)) {
            player.sendMessage("It's not implemented yet. §4:(") // TODO подарки (надо ли?..)
        }

        builder.button(Component.text(plugin.language!!.getString("interaction-menu.return-button")!!).color(buttonTextColor)) {
            this.showDefaultMenu(player, villager)
        }

        builder.build()
    }

    private fun showQuestListMenu(player: Player, villager: Villager) {

        val builder = Builder(villager, player)
        villager.quests.forEach { quest ->
            builder.button(Component.text(quest.questInfo.twoWordsDescription).color(buttonTextColor)) {
                villager.talk(player, quest.questInfo.questDescription)
                if (player.dialogueFormat != DialogueManager.DialogueFormat.CHAT) {
                    player.sendTutorialMessage(TutorialMessage.DIALOGUE)
                }
            }
        }

        builder.button(Component.text(plugin.language!!.getString("interaction-menu.return-button")!!).color(buttonTextColor)) { menu ->
            menu.destroy()
            this.showDefaultMenu(player, villager)
        }

        builder.build()
    }

    @EventHandler
    private fun onPlayerItemHeld(event: PlayerItemHeldEvent) {

        val player = event.player
        val menu   = openedMenuList.find { it.viewer == player } ?: return

        event.isCancelled = true
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1F, 2F)
        if (event.previousSlot < event.newSlot) {
            menu.index += 1
        } else menu.index -= 1

    }

    @EventHandler
    private fun onPlayerInteract(event: PlayerInteractEvent) {
        event.clickedBlock?.let { block ->
            (block.blockData as? Bed)?.let { bed ->
                if (bed.isOccupied) event.isCancelled = true
            }
        }
    }

    @EventHandler
    private fun onPlayerDamageVillager(event: EntityDamageByEntityEvent) {

        if (event.entity !is Villager || event.damager !is Player)
            return

        val villager = event.entity as Villager
        val player   = event.damager as Player
        val personalData = villager.personalData ?: return
        val message      = personalData.damageMessages.random()

        // Скип диалога
        if (dialogues.contains(player to villager)) {
            player.playSound(villager.location, villager.voiceSound, 1F, villager.voicePitch)
            dialogues[player to villager]?.destroy()
            event.isCancelled = true
            return
        }

        villager.talk(player, message, displaySize = 0.55F, followDuringDialogue = false, interruptPreviousDialogue = true)
    }

    class Builder(villager: Villager, viewer: Player) {

        private val menu: InteractionMenu = InteractionMenu(villager, viewer)

        fun button(name: TextComponent, action: (InteractionMenu) -> Unit): Builder {
            menu.addLine(name, {
                action(menu)
            })
            return this
        }

        fun build(): InteractionMenu {
            return menu
        }

    }

}

class InteractionMenu(
    val villager: Villager,
    val viewer: Player,
) {

    // Позиция для отображения текста
    private val pivot: Location = calculatePosition()
    private val textDisplays: MutableMap<TextDisplay, () -> Unit> = mutableMapOf()

    private val height = 1.4
    private val maxDistance = 5.5
    private val size = 0.4F
    private val step = 0.125

    private val defaultColor = Color.fromARGB(150, 0, 0, 0)
    private val selectedColor = Color.fromARGB(150, 200, 200, 0)

    init {
        openedMenuList.add(this)
    }

    /**
     * Вычисляет позицию отображения текста относительно игрока и жителя.
     */
    private fun calculatePosition(): Location {
        return viewer.eyeLocation.add(villager.location.add(0.0, height, 0.0)).multiply(0.5)
    }

    /**
     * Перемещает GUI, если игрок находится в пределах допустимого расстояния.
     */
    fun relocate() {
        if (viewer.location.distance(villager.location.add(0.0, height, 0.0)) > maxDistance) {
            destroy()
            return
        }
        updatePosition()
        (villager as CraftVillager).handle.tradingPlayer = (viewer as CraftPlayer).handle
    }

    private fun updatePosition() {
        val newLocation = calculatePosition()
        textDisplays.keys.forEachIndexed { index, display ->
            display.teleport(newLocation.clone().add(0.0, -index * step, 0.0))
        }
    }

    var index: Int = 0
        set(value) {
            field = cyclicIndex(value)
            updateSelection()
        }

    /**
     * Добавляет текстовую строку и связанную с ней функцию в GUI.
     *
     * @param text Текст для отображения.
     * @param function Функция, вызываемая при выборе этой строки.
     */
    fun addLine(text: TextComponent, function: () -> Unit) {
        val display = createButtonDisplay(text)
        textDisplays[display] = function
        updateSelection()
    }

    private fun createButtonDisplay(text: TextComponent): TextDisplay {
        val display = viewer.world.spawnEntity(
            pivot.clone().add(0.0, -textDisplays.size * step, 0.0),
            EntityType.TEXT_DISPLAY
        ) as TextDisplay
        display.isVisibleByDefault = false
        viewer.showEntity(QuestIntelligence.pluginInstance, display)
        display.transformation = Transformation(Vector3f(0f, 0f, 0f), AxisAngle4f(), Vector3f(size, size, size), AxisAngle4f())
        display.text(text)
        display.billboard = Display.Billboard.CENTER
        return display
    }

    private fun updateSelection() {
        textDisplays.keys.forEach { display -> display.backgroundColor = defaultColor }
        textDisplays.keys.toList().getOrNull(index)?.backgroundColor = selectedColor
    }

    /**
     * Вызывает функцию, связанную с текущим выбранным элементом.
     */
    fun invokeSelected() {
        textDisplays.keys.toList().getOrNull(index)?.let { display ->
            textDisplays[display]?.invoke()
        }
    }

    /**
     * Уничтожает GUI и освобождает ресурсы.
     */
    fun destroy() {
        openedMenuList.remove(this)
        textDisplays.keys.forEach(TextDisplay::remove)
        textDisplays.clear()
    }

    /**
     * Циклический индекс для навигации по доступным строкам.
     */
    private fun cyclicIndex(value: Int): Int {
        return when {
            value >= textDisplays.size -> 0
            value < 0 -> textDisplays.size - 1
            else -> value
        }
    }

}