package me.voxelsquid.quill.villager.interaction

import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidEntityExtension.getPersonalHumanoidData
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidEntityExtension.humanoidRegistry
import me.voxelsquid.quill.villager.ReputationManager
import me.voxelsquid.quill.villager.ReputationManager.Companion.fame
import me.voxelsquid.quill.villager.ReputationManager.Companion.fameLevel
import me.voxelsquid.quill.villager.ReputationManager.Companion.getRespect
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.openTradeMenu
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.quests
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.talk
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.voicePitch
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.voiceSound
import me.voxelsquid.quill.villager.interaction.DialogueManager.Companion.dialogues
import me.voxelsquid.quill.villager.interaction.InteractionMenuManager.Companion.openedMenuList
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
import org.bukkit.event.EventPriority
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

class InteractionMenuManager(private val plugin: QuestIntelligence): Listener {

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
            dialogues.values.filter { it.entity == villager }.forEach(DialogueManager.DialogueWindow::destroy)
        }
    }

    private val lastInteraction = mutableMapOf<Player, Long>()
    @EventHandler(priority = EventPriority.HIGHEST)
    private fun handleVillagerInteraction(event: PlayerInteractEntityEvent) {
        (event.rightClicked as? Villager)?.let { villager ->

            val player: Player = event.player
            val last = lastInteraction.computeIfAbsent(player) { System.currentTimeMillis() }
            val time = System.currentTimeMillis()

            if (time - last <= 200) {
                return
            } else lastInteraction[player] = time

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
                villager.getPersonalHumanoidData()?.let {
                    villager.talk(player, it.sleepInterruptionMessages.random(), followDuringDialogue = false)
                }
                return
            }

            // Обработка крайне негативной репутации
            if (villager.getRespect(player) <= -40 || player.fame <= -40) {
                villager.getPersonalHumanoidData()?.let {
                    villager.talk(player, it.badReputationInteractionDenial.random(), followDuringDialogue = false)
                }
                return
            }

            // Обработка взаимодействия с ребёнком
            if (!villager.isAdult) {
                villager.getPersonalHumanoidData()?.let { data ->
                    if (villager.getRespect(player) <= -20 || player.fame <= -20) {
                        villager.talk(player, data.badReputationInteractionDenial.random(), followDuringDialogue = false)
                    } else if (villager.getRespect(player) >= 20 || player.fame >= 20) {
                        villager.talk(player, data.kidInteractionFamousPlayer.random(), followDuringDialogue = false)
                    } else villager.talk(player, data.kidInteractionNeutralPlayer.random(), followDuringDialogue = false)
                }
                return
            }

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

            // Когда игрок спрашивает о квестах у безработного жителя
            if (villager.profession == Villager.Profession.NONE) {
                villager.getPersonalHumanoidData()?.let {
                    villager.talk(player, it.joblessMessages.random(), followDuringDialogue = true)
                    return@button
                }
            }

            // Когда игрок спрашивает о квестах у жителя с работой, но без квестов
            if (villager.quests.isEmpty()) {
                villager.getPersonalHumanoidData()?.let {
                    villager.talk(player, it.noQuestMessages.random(), followDuringDialogue = true)
                    return@button
                }
            }

            if (villager.quests.isNotEmpty()) {
                this.showQuestListMenu(player, villager)
            }
        }

        builder.button(Component.text(plugin.language!!.getString("interaction-menu.trade-button")!!).color(buttonTextColor)) {
            plugin.server.scheduler.runTaskLater(plugin, { _ -> villager.openTradeMenu(player) }, 1L)
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

                // Fame defines how villagers will talk with a player about quest details
                villager.talk(player, when(player.fameLevel) {
                    ReputationManager.Companion.Fame.INFAMOUS -> quest.questInfo.questDescriptionForInfamousPlayer
                    ReputationManager.Companion.Fame.NEUTRAL  -> quest.questInfo.questDescriptionForNeutralPlayer
                    ReputationManager.Companion.Fame.FAMOUS   -> quest.questInfo.questDescriptionForFamousPlayer
                })

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
        (event.damager as? Player)?.let { player ->
            (event.entity as? LivingEntity)?.let { entity ->

                if (!humanoidRegistry.contains(entity)) {
                    return
                }

                val personalData = entity.getPersonalHumanoidData() ?: return
                val message      = personalData.damageMessages.random()

                // Скип диалога
                if (dialogues.contains(player to entity)) {
                    dialogues[player to entity]?.destroy()
                    event.isCancelled = true
                    return
                }

                entity.talk(player, message, displaySize = 0.55F, followDuringDialogue = false, interruptPreviousDialogue = true)

            }
        }
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