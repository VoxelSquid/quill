package me.voxelsquid.quill.humanoid

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.player.UserProfile
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.event.HumanoidPersonalDataGeneratedEvent
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidController.HumanoidNamespace.characterKey
import me.voxelsquid.quill.humanoid.protocol.HumanoidProtocolManager
import me.voxelsquid.quill.humanoid.race.HumanoidRaceManager
import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.persistence.PersistentDataType
import org.bukkit.scoreboard.Team

class HumanoidManager : Listener {

    private val raceManager     = HumanoidRaceManager()
    private val protocolManager = HumanoidProtocolManager(humanoidRegistry)
    private val humanoidTicker  = HumanoidTicker()

    init {
        PacketEvents.getAPI().eventManager.registerListener(protocolManager)
        plugin.server.pluginManager.registerEvents(protocolManager, plugin)
        plugin.server.pluginManager.registerEvents(this, plugin)
        this.checkNamelessTeam()
        raceManager.load()
    }

    private fun checkNamelessTeam() {
        if (plugin.server.scoreboardManager.mainScoreboard.getEntryTeam("HideMyName") == null) {
            plugin.server.scoreboardManager.mainScoreboard.registerNewTeam("GoAheadMakeMyDay").also { team ->
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
                team.addEntry("HideMyName")
            }
        }
    }

    @EventHandler
    private fun whenPersonalDataGenerated(event: HumanoidPersonalDataGeneratedEvent) {
        event.entity.getHumanoidController()?.let { controller ->
            event.entity.customName(Component.text(event.personalData.villagerName))
            controller.personalData = event.personalData
            controller.savePersonalData()
        } ?: throw IllegalArgumentException("PersonalHumanoidData generated for non-existent humanoid. It wasn't supposed to happen.")
    }

    data class HumanoidController(val entity: LivingEntity,
                                  val profile: UserProfile,
                                  val subscribers: MutableList<Player> = mutableListOf(),
                                  var personalData: PersonalHumanoidData? = null) {

        fun savePersonalData() {
            this.personalData?.let { data ->
                entity.persistentDataContainer.set(personalDataKey, PersistentDataType.STRING, data.toString())
            } ?: throw NullPointerException("Trying to save non-existent personal data.")
        }

        data class PersonalHumanoidData(val villagerName: String,
                                        val sleepInterruptionMessages: MutableList<String>,
                                        val damageMessages: MutableList<String>,
                                        val joblessMessages: MutableList<String>,
                                        val noQuestMessages: MutableList<String>,
                                        val badReputationInteractionDenial: MutableList<String>,
                                        val kidInteractionFamousPlayer: MutableList<String>,
                                        val kidInteractionNeutralPlayer: MutableList<String>) {

            override fun toString(): String {
                return plugin.gson.toJson(this)
            }

        }

        companion object HumanoidNamespace {
            val personalDataKey = NamespacedKey(plugin, "PersonalData")
            val characterKey    = NamespacedKey(plugin, "CharacterType")
        }

    }

    companion object HumanoidEntityExtension {

        private val plugin   = QuestIntelligence.pluginInstance
        val humanoidRegistry = hashMapOf<LivingEntity, HumanoidController>()

        fun LivingEntity.getHumanoidController()   = humanoidRegistry[this]
        fun LivingEntity.getPersonalHumanoidData() = this.getHumanoidController()?.personalData

        fun LivingEntity.getCharacterType(): HumanoidCharacterType {
            return this.persistentDataContainer.get(characterKey, PersistentDataType.STRING)?.let { character ->
                HumanoidCharacterType.valueOf(character)
            } ?: HumanoidCharacterType.entries.random().also {
                this.setCharacterType(it)
            }
        }

        fun LivingEntity.setCharacterType(characterType: HumanoidCharacterType) {
            this.persistentDataContainer.set(characterKey, PersistentDataType.STRING, characterType.toString())
        }

    }

    enum class HumanoidCharacterType {

        DEPRESSED,
        OPTIMISTIC,
        PESSIMISTIC,
        KIND,
        RUDE,
        MEAN,
        EMOTIONAL,
        CYNICAL,
        COLD,
        FORMAL,
        FRIENDLY,
        FAMILIAR,
        HUMOROUS,
        TALKATIVE,
        IRONIC,
        SARCASTIC,
        SERIOUS,
        NOSTALGIC,
        WITTY,
        ADVENTUROUS,
        MYSTERIOUS,
        DREAMY,
        IMPULSIVE,
        OBSESSIVE,
        RECKLESS,
        HUMBLE,
        FORGIVING,
        RATIONAL,
        ARTISTIC,
        ANXIOUS,
        PLAYFUL,
        RELAXED,
        GRUMPY,
        INTELLECTUAL,
        NAIVE,
        IGNORANT,
        ANGRY,
        MAD_SCIENTIST,
        DRUNKARD,
        SANE,
        ROMANTIC,
        REBELLIOUS,
        DRAMATIC,
        LUCKY,
        UNLUCKY,
        THIEF,
        POTHEAD,
        RANDOM,
        C418;

    }

}