@file:Suppress("DEPRECATION")

package me.voxelsquid.quill.gameplay.villager.interaction

import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.dialogueFormat
import me.voxelsquid.quill.QuestIntelligence.Companion.isGeyserPlayer
import me.voxelsquid.quill.QuestIntelligence.Companion.pluginInstance
import me.voxelsquid.quill.base.config.ConfigurationAccessor
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.getVoicePitch
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.getVoiceSound
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.entity.CraftVillager
import org.bukkit.entity.*
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f

class DialogueManager(private val plugin: QuestIntelligence) {

    init {
        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            dialogues.values.forEach(DialogueWindow::relocate)
        }, 0L, 1L)
    }

    fun  startDialogue(pair: Pair<Player, LivingEntity>, text: String, follow: Boolean = true, size: Float = 0.35F, interrupt: Boolean = false) {

        val (player, villager) = pair
        val formattedText = dialogueBoxTextBaseColor + text.replace(Regex("\\*\\*(.*?)\\*\\*")) { matchResult ->
            "${dialogueBoxTextImportantColor}${matchResult.groupValues[1]}${dialogueBoxTextBaseColor}"
        }.replace(Regex("\\*(.*?)\\*")) { matchResult ->
            "${dialogueBoxTextInterestingColor}${matchResult.groupValues[1]}${dialogueBoxTextBaseColor}"
        }.replace("\\\"", "\"")

        if (player.isGeyserPlayer()) {
            this.sendDialogueInChat(player, villager, formattedText)
            return
        }

        when (player.dialogueFormat) {

            DialogueFormat.IMMERSIVE -> {

                if (!interrupt && dialogues.containsKey(pair)) {
                    return
                }

                DialogueWindow(plugin, player, villager, size, formattedText.split(" "), follow, interrupt).schedule()
            }

            DialogueFormat.CHAT -> {
                this.sendDialogueInChat(player, villager, formattedText)
            }

            DialogueFormat.BOTH -> {

                if (!interrupt && dialogues.containsKey(pair)) {
                    return
                }

                DialogueWindow(plugin, player, villager, size, formattedText.split(" "), follow, interrupt).schedule()
                this.sendDialogueInChat(player, villager, formattedText)
            }

        }
    }

    private val cooldownPlayers = mutableListOf<Player>()
    private fun sendDialogueInChat(player: Player, entity: LivingEntity, message: String) {

        // primitive yet clever cooldown system
        if (cooldownPlayers.contains(player)) {
            return
        } else {
            cooldownPlayers.add(player)
            plugin.server.scheduler.runTaskLater(plugin, { _ ->
                cooldownPlayers.remove(player)
            }, 20)
        }

        val formattedMessage = plugin.configManager.language.getString("villager-message-chat-format")!!.replace("{villagerName}", entity.customName ?: "").replace("{message}", message)
        player.sendMessage(formattedMessage)

        if (player.dialogueFormat != DialogueFormat.BOTH)
            player.playSound(entity.location, entity.getVoiceSound(), 1F, entity.getVoicePitch())
    }

    enum class DialogueFormat {
        IMMERSIVE, CHAT, BOTH
    }

    companion object {

        private val dialogueBoxSize = ConfigurationAccessor("text-formatting.dialogue-box.size", 0.28F).get()
        private val dialogueBoxTextBaseColor = ConfigurationAccessor("text-formatting.dialogue-box.text.base-color", "&f", mutableListOf("Standard color of common words in dialogue boxes.")).get()
        private val dialogueBoxTextImportantColor = ConfigurationAccessor("text-formatting.dialogue-box.text.important-color", "&5", mutableListOf("Color of important words in dialogue boxes that the AI will try to pay attention to.")).get()
        private val dialogueBoxTextInterestingColor = ConfigurationAccessor("text-formatting.dialogue-box.text.interesting-color", "&6", mutableListOf("Color of interesting words in dialogue boxes that may be interesting to the player.")).get()
        private val dialogueBackgroundAlpha = ConfigurationAccessor("text-formatting.dialogue-box.background.alpha", 185, mutableListOf("0-255")).get()
        private val dialogueBackgroundRed = ConfigurationAccessor("text-formatting.dialogue-box.background.red", 0, mutableListOf("0-255")).get()
        private val dialogueBackgroundGreen = ConfigurationAccessor("text-formatting.dialogue-box.background.green", 0, mutableListOf("0-255")).get()
        private val dialogueBackgroundBlue = ConfigurationAccessor("text-formatting.dialogue-box.background.blue", 0, mutableListOf("0-255")).get()

        val dialogues: MutableMap<Pair<Player, LivingEntity>, DialogueWindow> = mutableMapOf()

        fun LivingEntity.talk(player: Player, text: String?, displaySize: Float = dialogueBoxSize, followDuringDialogue: Boolean = true, interruptPreviousDialogue: Boolean = false) {
            text?.let { pluginInstance.humanoidManager.dialogueManager.startDialogue(player to this, it, size = displaySize, follow = followDuringDialogue, interrupt = interruptPreviousDialogue) }
        }

    }

    class DialogueWindow(
        private val plugin: QuestIntelligence,
        private val player: Player,
        val entity: LivingEntity,
        private val size: Float,
        private val words: List<String>,
        private val follow: Boolean,
        cancelPrevious: Boolean,
    ) {

        private val display: TextDisplay
        private val displayBackgroundColor = Color.fromARGB(dialogueBackgroundAlpha, dialogueBackgroundRed, dialogueBackgroundGreen, dialogueBackgroundBlue)

        private val voice: Sound = entity.getVoiceSound()
        private val pitch: Float = entity.getVoicePitch()

        private val height = if (entity is Ageable && !entity.isAdult) 0.75 else 1.25
        private val maxDistance = 5.5

        private val pauseDurationBetweenSentences = 3000L
        private val pauseDurationBetweenWords = 175L

        // If player is SNEAKING during dialogue, it will speed up!
        private val fastPauseDurationBetweenSentences = 1250L
        private val fastPauseDurationBetweenWords = 100L

        private var isCancelled = false
        private var isDestroyed = false

        init {
            if (cancelPrevious) dialogues[player to entity]?.let {
                it.display.remove()
                it.isCancelled = true
            }
            display = entity.world.spawnEntity(entity.location, EntityType.TEXT_DISPLAY) as TextDisplay
            dialogues[player to entity] = this
        }

        fun schedule() {

            display.billboard = Display.Billboard.CENTER
            display.isSeeThrough = false
            display.isVisibleByDefault = false
            player.showEntity(plugin, display)
            display.transformation =
                Transformation(Vector3f(0f, 0f, 0f), AxisAngle4f(), Vector3f(size, size, size), AxisAngle4f())

            display.backgroundColor = displayBackgroundColor

            val task = object : BukkitRunnable() {
                override fun run() {

                    var wordAmount = 0

                    for (word in words) {

                        if (!plugin.isEnabled || word.isEmpty() || isCancelled || isDestroyed)
                            break

                        val sentence = word.last() == '.' || word.last() == '!' || word.last() == '?'
                        val lastWord = words.indexOf(word) == words.lastIndex
                        val clear    = ++wordAmount > 10 && sentence && !lastWord

                        plugin.server.scheduler.runTask(plugin) { _ ->

                            display.text += "$word "
                            player.playSound(entity.location, voice, 1F, pitch)

                            if (follow && entity is CraftVillager) entity.handle.tradingPlayer = (player as CraftPlayer).handle
                        }

                        val pauseDuration = when {
                            player.isSneaking && sentence -> fastPauseDurationBetweenSentences
                            player.isSneaking -> fastPauseDurationBetweenWords
                            sentence || lastWord -> pauseDurationBetweenSentences
                            else -> pauseDurationBetweenWords
                        }

                        Thread.sleep(pauseDuration)

                        if (clear) {
                            display.text = dialogueBoxTextBaseColor
                            wordAmount = 0
                        }
                    }

                    if (plugin.isEnabled && !isDestroyed) {
                        plugin.server.scheduler.runTask(plugin) { _ ->
                            destroy()
                        }
                    }
                }
            }

            task.runTaskAsynchronously(plugin)
        }

        fun relocate() {

            if (checkDistance()) {
                this.destroy()
                return
            }

            display.teleport(this.calculatePosition())
        }

        fun destroy() {
            display.remove()
            (entity as? CraftVillager)?.handle?.tradingPlayer = null
            dialogues.remove(player to entity, this@DialogueWindow)
            isDestroyed = true
        }

        private fun checkDistance(): Boolean = player.location.distance(display.location) > maxDistance

        private fun calculatePosition(): Location {
            return player.eyeLocation.add(entity.location.add(0.0, if (entity.pose != Pose.SLEEPING) height else height - 0.4, 0.0)).multiply(0.5)
        }

    }


}