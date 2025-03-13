package me.voxelsquid.quill.gameplay.humanoid

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.player.TextureProperty
import com.github.retrooper.packetevents.protocol.player.UserProfile
import com.google.common.reflect.TypeToken
import com.google.gson.JsonSyntaxException
import io.papermc.paper.event.player.PlayerTradeEvent
import me.voxelsquid.quill.QuestIntelligence.Companion.gson
import me.voxelsquid.quill.QuestIntelligence.Companion.pluginInstance
import me.voxelsquid.quill.base.config.ConfigurationAccessor
import me.voxelsquid.quill.gameplay.event.HumanoidPersonalDataGeneratedEvent
import me.voxelsquid.quill.gameplay.event.QuestGenerateEvent
import me.voxelsquid.quill.gameplay.humanoid.race.HumanoidRaceManager
import me.voxelsquid.quill.gameplay.humanoid.race.HumanoidRaceManager.Race
import me.voxelsquid.quill.gameplay.humanoid.protocol.HumanoidProtocolManager
import me.voxelsquid.quill.gameplay.humanoid.race.HumanoidRaceManager.Companion.race
import me.voxelsquid.quill.gameplay.quest.QuestManager
import me.voxelsquid.quill.gameplay.quest.data.VillagerQuest
import me.voxelsquid.quill.gameplay.settlement.Settlement
import me.voxelsquid.quill.gameplay.settlement.SettlementManager
import me.voxelsquid.quill.gameplay.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.gameplay.util.InventorySerializer
import me.voxelsquid.quill.gameplay.villager.ProfessionManager
import me.voxelsquid.quill.gameplay.villager.interaction.DialogueManager
import me.voxelsquid.quill.gameplay.villager.interaction.InteractionMenuManager
import net.kyori.adventure.text.Component
import net.minecraft.world.entity.EquipmentSlot
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.craftbukkit.entity.CraftVillager
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SuspiciousStewMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scoreboard.Team
import kotlin.random.Random

class HumanoidManager : Listener {

    private val raceManager        = HumanoidRaceManager()
    private val protocolManager    = HumanoidProtocolManager(humanoidRegistry)
    private val interactionManager = InteractionMenuManager(plugin)
    private val professionManager  = ProfessionManager()
    private val tradeHandler       = HumanoidTradeHandler()
    private val questManager       = QuestManager(plugin)

    val settlementManager = SettlementManager(plugin)
    val dialogueManager   = DialogueManager(plugin)

    private val questIntervalTicks = ConfigurationAccessor(path = "gameplay.core.quest-tick-interval", defaultValue = 200L, comments = mutableListOf("Each iteration only ONE villager in the entire world will be selected to generate a new quest.")).get()
    private val foodIntervalTicks  = ConfigurationAccessor(path = "gameplay.core.food-tick-interval", defaultValue = 4800L, comments = mutableListOf("Each iteration ALL villagers in the entire world will eat.")).get()
    private val workIntervalTicks  = ConfigurationAccessor(path = "gameplay.core.work-tick-interval", defaultValue = 2400L, comments = mutableListOf("Each iteration ALL villagers in the entire world will produce items to trade.")).get()

    init {
        PacketEvents.getAPI().eventManager.registerListener(protocolManager)
        plugin.server.pluginManager.registerEvents(protocolManager, plugin)
        plugin.server.pluginManager.registerEvents(this, plugin)
        checkNamelessTeam()
        raceManager.load()
        startTickers()
    }

    private fun checkNamelessTeam() {
        plugin.server.scoreboardManager.mainScoreboard.getEntryTeam("HideMyName") ?: plugin.server.scoreboardManager.mainScoreboard.registerNewTeam("GoAheadMakeMyDay").also {
            it.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER)
            it.addEntry("HideMyName")
        }
    }

    private fun startTickers() {
        plugin.server.scheduler.runTaskTimer(plugin, { _ -> questManager.prepareQuest() }, 0, if (plugin.debug) 200 else questIntervalTicks)
        plugin.server.scheduler.runTaskTimer(plugin, { _ -> professionManager.produceProfessionItem() }, 0, if (plugin.debug) 200 else workIntervalTicks)
        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            plugin.allowedWorlds.flatMap { it.entities.filterIsInstance<Villager>() }.shuffled().forEachIndexed { index, villager ->
                if (villager.pose != Pose.SLEEPING) {
                    villager.hunger = (villager.hunger - 2.5).coerceAtLeast(0.0)
                    if (villager.hunger <= 17.5) {
                        plugin.server.scheduler.runTaskLater(plugin, { _ -> villager.eat() }, 5 + (index * 2L).coerceAtMost(40) + Random.nextInt(250))
                    }
                }
            }
        }, 0, foodIntervalTicks)
    }

    @EventHandler
    private fun onPersonalDataGenerated(event: HumanoidPersonalDataGeneratedEvent) {
        event.entity.getHumanoidController()?.let {
            event.entity.customName(Component.text(event.personalData.villagerName))
            it.personalData = event.personalData
            it.savePersonalData()
        } ?: throw IllegalArgumentException("PersonalHumanoidData generated for non-existent humanoid. Whoops.")
    }

    @EventHandler
    private fun onQuestGenerate(event: QuestGenerateEvent) {
        event.villager.addQuest(event.quest)
        plugin.debug("Quest '${event.quest.questInfo}' spawned for villager ${event.villager.customName}. Lucky them.")
    }

    @EventHandler
    private fun onVillagerPickupItem(event: EntityPickupItemEvent) {
        (event.entity as? Villager)?.addItemToQuillInventory(event.item.itemStack)
    }

    @EventHandler
    private fun onPlayerTrade(event: PlayerTradeEvent) {
        (event.villager as? Villager)?.let { villager ->
            villager.quests.find { event.trade.ingredients[0].isSimilar(it.questItem) && event.trade.result.isSimilar(it.rewardItem) }?.let {
                questManager.finishQuest(event.player, villager, it, event.trade.ingredients[0], event.trade.result)
                return
            }
            if (!event.isCancelled) villager.takeItemFromQuillInventory(event.trade.result, event.trade.result.amount)
        }
    }

    data class HumanoidController(
        val entity: LivingEntity,
        val profile: UserProfile,
        val race: Race?,
        val subscribers: MutableList<Player> = mutableListOf(),
        var personalData: PersonalHumanoidData? = null
    ) {
        fun savePersonalData() = personalData?.let {
            entity.persistentDataContainer.set(HumanoidNamespace.personalDataKey, PersistentDataType.STRING, it.toString())
        } ?: throw NullPointerException("Saving null personal data? Nice try.")
    }

    data class PersonalHumanoidData(
        val villagerName: String,
        val sleepInterruptionMessages: MutableList<String>,
        val damageMessages: MutableList<String>,
        val joblessMessages: MutableList<String>,
        val noQuestMessages: MutableList<String>
    ) {
        override fun toString(): String = gson.toJson(this)
    }

    companion object HumanoidEntityExtension {

        val plugin = pluginInstance
        val humanoidRegistry = hashMapOf<LivingEntity, HumanoidController>()
        val HUMANOID_VILLAGERS_ENABLED = plugin.controller.humanoids

        fun LivingEntity.getHumanoidController() = humanoidRegistry[this]
        fun LivingEntity.getPersonalHumanoidData() = getHumanoidController()?.personalData

        fun LivingEntity.getCharacterType() = persistentDataContainer.get(HumanoidNamespace.characterKey, PersistentDataType.STRING)?.let {
            HumanoidCharacterType.valueOf(it)
        } ?: HumanoidCharacterType.entries.random().also { setCharacterType(it) }

        fun LivingEntity.setCharacterType(characterType: HumanoidCharacterType) {
            persistentDataContainer.set(HumanoidNamespace.characterKey, PersistentDataType.STRING, characterType.toString())
        }

        fun LivingEntity.getVoiceSound(): Sound = persistentDataContainer.get(HumanoidNamespace.voiceKey, PersistentDataType.STRING)?.let {
            Sound.valueOf(it)
        } ?: race?.let {
            val voices = if (gender == HumanoidGender.MALE) it.maleVoices else it.femaleVoices
            voices.random().sound.also { sound -> persistentDataContainer.set(HumanoidNamespace.voiceKey, PersistentDataType.STRING, sound.toString()) }
        } ?: Sound.INTENTIONALLY_EMPTY

        fun LivingEntity.getVoicePitch() = persistentDataContainer.get(HumanoidNamespace.pitchKey, PersistentDataType.FLOAT) ?: race?.let {
            Random.nextDouble(it.maleVoices.random().min, it.maleVoices.random().max).toFloat().also { pitch ->
                persistentDataContainer.set(HumanoidNamespace.pitchKey, PersistentDataType.FLOAT, pitch)
            }
        } ?: 1.0F

        fun LivingEntity.skin() = race?.let { r ->
            persistentDataContainer.get(HumanoidNamespace.skinKey, PersistentDataType.STRING)?.let { skin ->
                val (value, signature) = skin.split(":"); TextureProperty("textures", value, signature)
            } ?: (if (gender == HumanoidGender.MALE) r.maleSkins else r.femaleSkins).random().also {
                persistentDataContainer.set(HumanoidNamespace.skinKey, PersistentDataType.STRING, "${it.value}:${it.signature}")
            }
        } ?: TextureProperty("textures", "", "")

        val LivingEntity.gender: HumanoidGender
            get() = persistentDataContainer.get(HumanoidNamespace.genderKey, PersistentDataType.STRING)?.let {
                HumanoidGender.valueOf(it)
            } ?: HumanoidGender.entries.random().also { persistentDataContainer.set(HumanoidNamespace.genderKey, PersistentDataType.STRING, it.toString()) }

        fun Villager.addItemToQuillInventory(vararg items: ItemStack) = quillInventory.let { inv ->
            items.forEach { it.amount = it.amount.coerceAtMost(it.maxStackSize); inv.addItem(it) }
            persistentDataContainer.set(HumanoidNamespace.inventoryKey, PersistentDataType.STRING, InventorySerializer.jsonifyInventory(inv).toString())
        }

        fun Villager.takeItemFromQuillInventory(item: ItemStack, amountToTake: Int) = quillInventory.filterNotNull().find {
            it.isSimilar(item)
        }?.also { it.amount -= amountToTake }?.let {
            persistentDataContainer.set(HumanoidNamespace.inventoryKey, PersistentDataType.STRING, InventorySerializer.jsonifyInventory(quillInventory).toString())
        }

        fun Villager.updateQuests() = quests.forEach { quest ->
            removeQuest(quest)
            pluginInstance.humanoidManager.questManager.buildQuest(quest.type, this, quest.questItem)?.let {
                addQuest(it.setQuestInfo(quest.questInfo).build().apply { timeCreated = quest.timeCreated })
            }
        }

        fun Villager.consume(item: ItemStack, sound: Sound, duration: Int, period: Long = 5L, onDone: () -> Unit) {
            val nmsVillager = (this as CraftVillager).handle
            val nmsItem = CraftItemStack.asNMSCopy(item)
            var ticks = 0
            pluginInstance.server.scheduler.runTaskTimer(pluginInstance, { task ->
                nmsVillager.isNoAi = true
                nmsVillager.setItemSlot(EquipmentSlot.MAINHAND, nmsItem)
                world.playSound(location, sound, 1F, 1F)
                if (++ticks >= duration) {
                    nmsVillager.setItemSlot(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY)
                    onDone()
                    nmsVillager.isNoAi = false
                    task.cancel()
                }
            }, 0, period)
        }

        fun Villager.eat() = quillInventory.filterNotNull().find { it.type.isEdible }?.let { food ->
            val sound = when (food.type) {
                Material.HONEY_BOTTLE -> Sound.ITEM_HONEY_BOTTLE_DRINK
                Material.MUSHROOM_STEW, Material.RABBIT_STEW, Material.SUSPICIOUS_STEW -> Sound.ENTITY_GENERIC_DRINK
                else -> Sound.ENTITY_GENERIC_EAT
            }
            consume(food, sound, 3, period = 7) {
                takeItemFromQuillInventory(food, 1)
                if (food.type.toString().contains("STEW")) {
                    addItemToQuillInventory(ItemStack(Material.BOWL))
                    (food.itemMeta as? SuspiciousStewMeta)?.customEffects?.forEach { addPotionEffect(it) }
                }
                if (food.type == Material.HONEY_BOTTLE) addItemToQuillInventory(ItemStack(Material.GLASS_BOTTLE))
                world.playSound(location, getVoiceSound(), 1F, getVoicePitch())
                world.playSound(location, Sound.ENTITY_PLAYER_BURP, 1F, 1F)
                hunger += 7.5
                if (hunger >= 20.0) addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 200, 1))
            }
        }

        val Villager.professionLevelName get() = when (villagerLevel) { 1 -> "NOVICE"; 2 -> "APPRENTICE"; 3 -> "JOURNEYMAN"; 4 -> "EXPERT"; else -> "MASTER" }

        var Villager.settlement: Settlement?
            get() = persistentDataContainer.get(HumanoidNamespace.settlementKey, PersistentDataType.STRING)?.let { name -> settlements[world]?.find { it.data.settlementName == name } }
            set(value) { value?.let { persistentDataContainer.set(HumanoidNamespace.settlementKey, PersistentDataType.STRING, it.data.settlementName) } }

        val Villager.quillInventory: Inventory
            get() = persistentDataContainer.get(HumanoidNamespace.inventoryKey, PersistentDataType.STRING)?.let {
                InventorySerializer.dejsonifyInventory(it)
            } ?: Bukkit.createInventory(null, 54).also { inv ->
                race?.spawnItems?.forEach { item -> inv.addItem(item.build()) }
                persistentDataContainer.set(HumanoidNamespace.inventoryKey, PersistentDataType.STRING, InventorySerializer.jsonifyInventory(inv).toString())
            }

        var Villager.hunger: Double
            get() = persistentDataContainer.get(HumanoidNamespace.hungerKey, PersistentDataType.DOUBLE) ?: 20.0.also { persistentDataContainer.set(HumanoidNamespace.hungerKey, PersistentDataType.DOUBLE, it) }
            set(value) { persistentDataContainer.set(HumanoidNamespace.hungerKey, PersistentDataType.DOUBLE, value) }

        val Villager.quests: MutableList<VillagerQuest>
            get() = persistentDataContainer.get(HumanoidNamespace.questDataKey, PersistentDataType.STRING)?.let {
                try {
                    gson.fromJson(it, object : TypeToken<MutableList<VillagerQuest>>() {}.type)
                } catch (exception: JsonSyntaxException) {
                    plugin.debug("Exception during quest loading! Removing every quest from ${this.getPersonalHumanoidData()!!.villagerName}.")
                    persistentDataContainer.remove(HumanoidNamespace.questDataKey)
                    mutableListOf()
                }
            } ?: mutableListOf()

        fun Villager.addQuest(quest: VillagerQuest) {
            persistentDataContainer.set(HumanoidNamespace.questDataKey, PersistentDataType.STRING, gson.toJson(quests.apply { add(quest) }))
        }

        fun Villager.removeQuest(quest: VillagerQuest) {
            persistentDataContainer.set(HumanoidNamespace.questDataKey, PersistentDataType.STRING, gson.toJson(quests.apply { removeIf { it.questInfo.questName == quest.questInfo.questName } }))
        }
    }

    enum class HumanoidGender { MALE, FEMALE }

    enum class HumanoidCharacterType {
        DEPRESSED, OPTIMISTIC, PESSIMISTIC, KIND, RUDE, MEAN, EMOTIONAL, CYNICAL, COLD, FORMAL,
        FRIENDLY, FAMILIAR, HUMOROUS, TALKATIVE, IRONIC, SARCASTIC, SERIOUS, NOSTALGIC, WITTY,
        ADVENTUROUS, MYSTERIOUS, DREAMY, IMPULSIVE, OBSESSIVE, RECKLESS, HUMBLE, FORGIVING,
        RATIONAL, ARTISTIC, ANXIOUS, PLAYFUL, RELAXED, GRUMPY, INTELLECTUAL, NAIVE, IGNORANT,
        ANGRY, MAD_SCIENTIST, DRUNKARD, SANE, ROMANTIC, REBELLIOUS, DRAMATIC, LUCKY, UNLUCKY,
        THIEF, POTHEAD, RANDOM, EVIL, SHAMAN, PHILOSOPHICAL
    }

    object HumanoidNamespace {
        val personalDataKey = NamespacedKey(pluginInstance, "PersonalData")
        val characterKey    = NamespacedKey(pluginInstance, "CharacterType")
        val voiceKey        = NamespacedKey(pluginInstance, "VoiceSound")
        val pitchKey        = NamespacedKey(pluginInstance, "VoicePitch")
        val skinKey         = NamespacedKey(pluginInstance, "Skin")
        val genderKey       = NamespacedKey(pluginInstance, "Gender")
        val questDataKey    = NamespacedKey(pluginInstance, "QuestData")
        val hungerKey       = NamespacedKey(pluginInstance, "Hunger")
        val settlementKey   = NamespacedKey(pluginInstance, "Settlement")
        val inventoryKey    = NamespacedKey(pluginInstance, "Inventory")
    }

}