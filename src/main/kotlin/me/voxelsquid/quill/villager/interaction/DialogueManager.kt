package me.voxelsquid.quill.villager.interaction

import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.dialogueFormat
import me.voxelsquid.quill.villager.VillagerManager.Companion.voicePitch
import me.voxelsquid.quill.villager.VillagerManager.Companion.voiceSound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
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

    fun startDialogue(pair: Pair<Player, Villager>, text: String, follow: Boolean = true, size: Float = 0.3F, interrupt: Boolean = false) {

        val (player, villager) = pair

        when (player.dialogueFormat) {

            DialogueFormat.IMMERSIVE -> {

                if (!interrupt && dialogues.containsKey(pair)) {
                    return
                }

                DialogueWindow(plugin, player, villager, size, text.replace("\\\"", "\"").split(" "), follow, interrupt).schedule()
            }

            DialogueFormat.CHAT -> {
                this.sendDialogueInChat(player, villager, text)
            }

            DialogueFormat.BOTH -> {

                if (!interrupt && dialogues.containsKey(pair)) {
                    return
                }

                DialogueWindow(plugin, player, villager, size, text.replace("\\\"", "\"").split(" "), follow, interrupt).schedule()
                this.sendDialogueInChat(player, villager, text)
            }

        }
    }

    private val cooldownPlayers = mutableListOf<Player>()
    private fun sendDialogueInChat(player: Player, villager: Villager, message: String) {

        // primitive yet clever cooldown system
        if (cooldownPlayers.contains(player)) {
            return
        } else {
            cooldownPlayers.add(player)
            plugin.server.scheduler.runTaskLater(plugin, { _ ->
                cooldownPlayers.remove(player)
            }, 20)
        }

        val formattedMessage: String = plugin.language?.getString("villager-message-chat-format")!!
            .replace("{villagerName}", (villager.customName() as TextComponent).content())
            .replace("{message}", message)
        player.sendMessage(formattedMessage)

        if (player.dialogueFormat != DialogueFormat.BOTH)
            player.playSound(villager.location, villager.voiceSound, 1F, villager.voicePitch)
    }

    enum class DialogueFormat {
        IMMERSIVE, CHAT, BOTH
    }

    companion object {
        val dialogues: MutableMap<Pair<Player, Villager>, DialogueWindow> = mutableMapOf()
    }

    class DialogueWindow(
        private val plugin: QuestIntelligence,
        private val player: Player,
        val villager: Villager,
        private val size: Float,
        private val words: List<String>,
        private val follow: Boolean,
        cancelPrevious: Boolean
    ) {

        private val display: TextDisplay

        private val voice: Sound = villager.voiceSound
        private val pitch: Float = villager.voicePitch

        private val height = 1.25
        private val maxDistance = 5.5

        private val pauseDurationBetweenSentences = 3000L
        private val pauseDurationBetweenWords = 175L

        // If player is SNEAKING during dialogue, it will speed up!
        private val fastPauseDurationBetweenSentences = 1250L
        private val fastPauseDurationBetweenWords = 100L

        private var isCancelled = false
        private var isDestroyed = false

        init {
            if (cancelPrevious) dialogues[player to villager]?.let {
                it.display.remove()
                it.isCancelled = true
            }
            display = villager.world.spawnEntity(villager.location, EntityType.TEXT_DISPLAY) as TextDisplay
            dialogues[player to villager] = this
        }

        fun schedule() {

            display.billboard = Display.Billboard.CENTER
            display.isSeeThrough = true
            display.isVisibleByDefault = false
            player.showEntity(plugin, display)
            display.transformation =
                Transformation(Vector3f(0f, 0f, 0f), AxisAngle4f(), Vector3f(size, size, size), AxisAngle4f())

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

                            display.text(display.text().append(Component.text("$word ")))
                            player.playSound(villager.location, voice, 1F, pitch)

                            if (follow)
                                (villager as CraftVillager).handle.tradingPlayer = (player as CraftPlayer).handle

                        }

                        val pauseDuration = when {
                            player.isSneaking && sentence -> fastPauseDurationBetweenSentences
                            player.isSneaking -> fastPauseDurationBetweenWords
                            sentence -> pauseDurationBetweenSentences
                            else -> pauseDurationBetweenWords
                        }

                        Thread.sleep(pauseDuration)

                        if (clear) {
                            display.text(Component.text(""))
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
            (villager as CraftVillager).handle.tradingPlayer = null
            dialogues.remove(player to villager, this@DialogueWindow)
            isDestroyed = true
        }

        private fun checkDistance(): Boolean = player.location.distance(display.location) > maxDistance

        private fun calculatePosition(): Location {
            return player.eyeLocation.add(villager.location.add(0.0, if (villager.pose != Pose.SLEEPING) height else height - 0.4, 0.0)).multiply(0.5)
        }

    }

}