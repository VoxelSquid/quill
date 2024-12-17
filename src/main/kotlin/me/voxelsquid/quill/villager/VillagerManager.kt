package me.voxelsquid.quill.villager

import com.google.common.reflect.TypeToken
import io.papermc.paper.event.player.PlayerTradeEvent
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.event.QuestGenerateEvent
import me.voxelsquid.quill.event.VillagerDataGenerateEvent
import me.voxelsquid.quill.nms.VersionProvider.Companion.consume
import me.voxelsquid.quill.quest.QuestManager
import me.voxelsquid.quill.quest.data.VillagerQuest
import me.voxelsquid.quill.settlement.Settlement
import me.voxelsquid.quill.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.util.InventorySerializer
import me.voxelsquid.quill.util.ItemStackCalculator.Companion.calculatePrice
import me.voxelsquid.quill.villager.interaction.DialogueManager
import me.voxelsquid.quill.villager.interaction.MenuManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe
import org.bukkit.inventory.meta.SuspiciousStewMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import kotlin.random.Random

class VillagerManager(instance: QuestIntelligence) : Listener {

    private val interactionManager: MenuManager
    private val professionManager:  ProfessionManager

    init {

        plugin = instance
        plugin.server.pluginManager.registerEvents(this, plugin)
        interactionManager = MenuManager(plugin)
        professionManager  = ProfessionManager()

        val questIntervalTicks = plugin.config.getLong("core-settings.tick-period.quest")
        val foodIntervalTicks  = plugin.config.getLong("core-settings.tick-period.food")
        val workIntervalTicks  = plugin.config.getLong("core-settings.tick-period.work")

        // Quest tick
        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            questManager.prepareQuest()
        }, 0, questIntervalTicks)

        // Work tick
        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            professionManager.produceProfessionItem()
        }, 0, workIntervalTicks)

        // Food tick
        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            val villagers = mutableListOf<Villager>()
            plugin.enabledWorlds.forEach { world -> villagers.addAll(world.entities.filterIsInstance<Villager>()) }

            plugin.server.scheduler.runTaskAsynchronously(plugin, { _ ->
                villagers.forEach { villager ->
                    if (villager.pose != Pose.SLEEPING) {

                        if (villager.hunger > 0) villager.hunger -= 2.5
                        else villager.hunger = 0.0

                        plugin.server.scheduler.runTaskLater(plugin, { _ ->
                            if (villager.hunger <= 17.5) {
                                villager.eat()
                            }
                        }, 5)

                    }
                }
            })

        }, 0, foodIntervalTicks)

    }

    @EventHandler
    private fun onQuestSuccessfulGenerate(event: QuestGenerateEvent) {
        val quest = event.quest
        event.villager.addQuest(quest)
        plugin.debug("Successfully generated a new quest: ${quest.questInfo} and added it to villager ${(event.villager.customName() as TextComponent).content()}.")
        plugin.debug("=== --- === --- === --- === --- === --- === --- === --- === --- ===")
    }

    @EventHandler
    private fun onPersonalVillagerDataGenerate(event: VillagerDataGenerateEvent) {
        this.savePersonalVillagerData(event.villager, event.data)
    }

    @EventHandler
    private fun onVillagerPickupItem(event: EntityPickupItemEvent) {
        (event.entity as? Villager)?.addItemToQuillInventory(event.item.itemStack)
    }

    @EventHandler
    private fun onPlayerTrade(event: PlayerTradeEvent) {

        // TODO: Квесты странствующего торговца.
        if (event.villager !is Villager)
            return

        val villager = event.villager as Villager
        val trade    = event.trade
        val player   = event.player

        villager.quests.forEach { quest ->
            if (trade.ingredients[0].isSimilar(quest.questItem) && trade.result.isSimilar(quest.rewardItem)) {
                questManager.finishQuest(player, villager, quest, trade.ingredients[0], trade.result)
            }
        }
    }

    data class PersonalVillagerData(val villagerName: String?,
                                    val sleepInterruptionMessages: MutableList<String>,
                                    val damageMessages: MutableList<String>,
                                    val joblessMessages: MutableList<String>)

    private fun savePersonalVillagerData(villager: Villager, data: PersonalVillagerData) {
        data.villagerName?.let { name ->
            villager.customName(Component.text(name))
            villager.persistentDataContainer.set(villagerPersonalDataKey, PersistentDataType.STRING, plugin.gson.toJson(data))
        }
    }

    companion object {

        private var plugin          = QuestIntelligence.pluginInstance
        private var questManager    = QuestManager(plugin)
        private var dialogueManager = DialogueManager(plugin)

        private val villagerPersonalDataKey: NamespacedKey = NamespacedKey(plugin, "personalVillagerData")
        private val villagerPersonalityKey:  NamespacedKey = NamespacedKey(plugin, "personalityType")
        private val villagerQuestDataKey:    NamespacedKey = NamespacedKey(plugin, "questData")
        private val villagerVoiceSoundKey:   NamespacedKey = NamespacedKey(plugin, "voiceSound")
        private val villagerVoicePitchKey:   NamespacedKey = NamespacedKey(plugin, "voicePitch")
        private val villagerHungerKey:       NamespacedKey = NamespacedKey(plugin, "hunger")
        private val villagerSettlementKey:   NamespacedKey = NamespacedKey(plugin, "settlement")
        val villagerInventoryKey:            NamespacedKey = NamespacedKey(plugin, "inventory")

        /** Every time someone opens a trade deal with a villager, a new trade list is created. And the old one is overwritten. */
        fun Villager.openTradeMenu(player : Player) {

            val quests = quests
            val itemsToTrade = producedItems
            val questTrades = mutableListOf<MerchantRecipe>()

            this.recipes = mutableListOf<MerchantRecipe>() // cleaning

            // Adding quest trades first
            if (quests.isNotEmpty()) {
                quests.forEach { quest ->
                    val recipe = MerchantRecipe(quest.rewardItem, 1)
                    recipe.addIngredient(quest.questItem)
                    questTrades += recipe
                }
            }

            // Adding produced items trades
            itemsToTrade.forEach { item ->

                // We skip adding identical trades
                if (recipes.find { recipe -> recipe.result.isSimilar(item) } != null) {
                    return@forEach
                }

                val price = item.calculatePrice() // Определяем цену предмета
                val emeraldBlockPrice = plugin.configurationClip.pricesConfig.getInt("EMERALD_BLOCK")
                val emeraldPrice = plugin.configurationClip.pricesConfig.getInt("EMERALD")
                val emeraldBlockAmount = price / emeraldBlockPrice
                var emeraldAmount = (price - emeraldBlockAmount * emeraldBlockPrice) / emeraldPrice
                val recipe = MerchantRecipe(item, 1)

                // We use emerald blocks in trading only if the total amount of emeralds (including emerald blocks * 9) is greater than 64. Otherwise, regular emeralds can be used.
                if (emeraldBlockAmount > 0 && emeraldBlockAmount * 9 + emeraldAmount > 64) {
                    recipe.addIngredient(ItemStack(Material.EMERALD_BLOCK, emeraldBlockAmount))
                } else emeraldAmount += emeraldBlockAmount * 9

                if (emeraldAmount > 0) {
                    recipe.addIngredient(ItemStack(Material.EMERALD, emeraldAmount))
                }

                if (recipe.ingredients.isEmpty())
                    return@forEach

                this.recipes = recipes + recipe
            }

            val sorted = recipes.toMutableList().sortedWith(compareBy({ it.result.type }, { it.result.calculatePrice() }))

            // Quest trades always first
            recipes = questTrades + sorted

            if (recipes.isEmpty()) {
                this.shakeHead()
                return
                // TODO: Добавить фразы, когда у жителей нет никаких торговых сделок.
            }

            player.openMerchant(this, false)
        }

        fun Villager.addItemToQuillInventory(vararg items: ItemStack) {
            val inventory = quillInventory

            for (item in items) {
                if (item.amount > item.maxStackSize) item.amount = item.maxStackSize
                inventory.addItem(item)
            }

            persistentDataContainer.set(villagerInventoryKey, PersistentDataType.STRING, InventorySerializer.jsonifyInventory(inventory).toString())
        }

        fun Villager.takeItemFromQuillInventory(item: ItemStack, amountToTake: Int) {
            val inventory = quillInventory
            inventory.filterNotNull().find { it.type == item.type }?.apply { amount -= amountToTake }
            persistentDataContainer.set(villagerInventoryKey, PersistentDataType.STRING, InventorySerializer.jsonifyInventory(inventory).toString())
        }

        // Reward recalculation for each quest villager have
        fun Villager.updateQuests() {
            this.quests.forEach { quest ->
                this.removeQuest(quest)
                questManager.buildQuest(quest.type, this@updateQuests, quest.questItem)?.let {
                    this.addQuest(it.setQuestInfo(quest.questInfo).build().apply { timeCreated = quest.timeCreated })
                }
            }
        }

        fun Villager.eat() {
            val villager = this
            val food = quillInventory.filterNotNull().find { it.type.isEdible } ?: return
            val sound = when (food.type) {
                Material.HONEY_BOTTLE -> Sound.ITEM_HONEY_BOTTLE_DRINK
                Material.MUSHROOM_STEW, Material.RABBIT_STEW, Material.SUSPICIOUS_STEW -> Sound.ENTITY_GENERIC_DRINK
                else -> Sound.ENTITY_GENERIC_EAT
            }

            villager.consume(food, sound, 3, period = 7) {
                villager.takeItemFromQuillInventory(food, 1)

                if (food.type.toString().contains("STEW")) {
                    villager.addItemToQuillInventory(ItemStack(Material.BOWL))
                    (food.itemMeta as? SuspiciousStewMeta)?.customEffects?.forEach { effect ->
                        villager.addPotionEffect(effect)
                    }
                }

                if (food.type == Material.HONEY_BOTTLE)
                    this.addItemToQuillInventory(ItemStack(Material.GLASS_BOTTLE))

                villager.world.playSound(villager.location, villager.voiceSound, 1F, villager.voicePitch)
                villager.world.playSound(villager.location, Sound.ENTITY_PLAYER_BURP, 1F, 1F)
                villager.hunger += 7.5

                if (hunger >= 20.0) {
                    villager.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 200, 1))
                }
            }
        }

        fun Villager.talk(player: Player, text: String?, displaySize: Float = 0.35F, followDuringDialogue: Boolean = true, interruptPreviousDialogue: Boolean = false) {
            text?.let {
                dialogueManager.startDialogue(player to this, it, size = displaySize, follow = followDuringDialogue, interrupt = interruptPreviousDialogue)
            }
        }

        val Villager.masteryLevelName : String
            get() {
                return when (this.villagerLevel) {
                    1 -> "NOVICE"
                    2 -> "APPRENTICE"
                    3 -> "JOURNEYMAN"
                    4 -> "EXPERT"
                    else -> "MASTER"
                }
            }

        var Villager.settlement : Settlement?
            get() {
                persistentDataContainer.get(villagerSettlementKey, PersistentDataType.STRING)?.let { settlementName ->
                    return settlements.find { it.data.settlementName == settlementName }
                }
                return null
            }
            set(settlement) {
                settlement?.let {
                    persistentDataContainer.set(villagerSettlementKey, PersistentDataType.STRING, it.data.settlementName)
                }
            }

        val Villager.personalData : PersonalVillagerData?
            get() {
                persistentDataContainer.get(villagerPersonalDataKey, PersistentDataType.STRING)?.let { personalVillagerData ->
                    return plugin.gson.fromJson(personalVillagerData, PersonalVillagerData::class.java)
                }
                return null
            }

        val Villager.quillInventory: Inventory
            get() {

                val jsonInventory = persistentDataContainer.get(villagerInventoryKey, PersistentDataType.STRING)
                val inventory = if (jsonInventory != null) {
                    InventorySerializer.dejsonifyInventory(jsonInventory)
                } else {
                    Bukkit.createInventory(null, 54).also {
                            persistentDataContainer.set(
                            villagerInventoryKey,
                            PersistentDataType.STRING,
                            InventorySerializer.jsonifyInventory(it).toString()
                        )
                    }
                }

                // TODO: Необходимо сделать стартовые предметы конфигурируемыми.
                if (inventory.isEmpty) {
                    inventory.addItem(ItemStack(Material.EMERALD, 18 + Random.nextInt(36)))
                    inventory.addItem(ItemStack(Material.IRON_INGOT, 50 + Random.nextInt(8)))
                    inventory.addItem(ItemStack(Material.STICK, 50 + Random.nextInt(8)))
                    inventory.addItem(ItemStack(Material.GOLD_INGOT, 8 + Random.nextInt(8)))
                    inventory.addItem(ItemStack(Material.BREAD, 12 + Random.nextInt(16)))
                    inventory.addItem(ItemStack(Material.APPLE, 8 + Random.nextInt(16)))
                    inventory.addItem(ItemStack(Material.COOKIE, 8 + Random.nextInt(16)))
                }

                return inventory
            }

        private val Villager.producedItems: List<ItemStack>
            get() {
                val itemsToProduce = plugin.config.getStringList("villager-item-producing.profession.${this.profession}.item-produce")
                return quillInventory.filterNotNull().filter { itemStack -> itemsToProduce.contains(itemStack.type.toString()) }.toList()
            }

        val Villager.foodAmount: Int
            get() {
                return quillInventory.filterNotNull().count { it.type.isEdible }
            }

        val Villager.voiceSound: Sound
            get() {
                val value = this.persistentDataContainer.get(villagerVoiceSoundKey, PersistentDataType.STRING)
                return if (value != null) {
                    Sound.valueOf(value)
                } else {
                    val sound = arrayOf(Sound.ENTITY_WANDERING_TRADER_YES, Sound.ENTITY_WANDERING_TRADER_NO, Sound.ENTITY_VILLAGER_YES, Sound.ENTITY_VILLAGER_NO, Sound.ENTITY_VINDICATOR_AMBIENT, Sound.ENTITY_VINDICATOR_CELEBRATE, Sound.ENTITY_VILLAGER_TRADE, Sound.ENTITY_PILLAGER_AMBIENT, Sound.ENTITY_WITCH_AMBIENT /* xD */).random()
                    sound.also { this.persistentDataContainer.set(villagerVoiceSoundKey, PersistentDataType.STRING, it.toString()) }
                }
            }

        val Villager.voicePitch: Float
            get() {
                return persistentDataContainer.get(villagerVoicePitchKey, PersistentDataType.FLOAT)
                    ?: (Random.nextFloat() * 1.25F + 0.75F).also { pitch ->
                        this.persistentDataContainer.set(villagerVoicePitchKey, PersistentDataType.FLOAT, pitch)
                    }
            }

        var Villager.character: CharacterType
            get() {
                val value = this.persistentDataContainer.get(villagerPersonalityKey, PersistentDataType.STRING)
                return if (value != null) {
                    CharacterType.valueOf(value)
                } else {
                    CharacterType.entries[Random.nextInt(CharacterType.entries.size)].also {
                        this.persistentDataContainer.set(villagerPersonalityKey, PersistentDataType.STRING, it.toString())
                    }
                }
            }
            set(personality) {
                this.persistentDataContainer.set(villagerPersonalityKey, PersistentDataType.STRING, personality.toString())
            }

        var Villager.hunger: Double
            get() {
                return if (persistentDataContainer.has(villagerHungerKey)) {
                    persistentDataContainer.get(villagerHungerKey, PersistentDataType.DOUBLE)!!
                } else {
                    20.0.also { persistentDataContainer.set(villagerHungerKey, PersistentDataType.DOUBLE, it) }
                }
            }
            set(value) {
                persistentDataContainer.set(villagerHungerKey, PersistentDataType.DOUBLE, value)
            }

        val Villager.quests: MutableList<VillagerQuest>
            get() {
                val serializedQuests = this.persistentDataContainer.get(villagerQuestDataKey, PersistentDataType.STRING)
                return if (serializedQuests == null) {
                    mutableListOf()
                } else {
                    val type = object : TypeToken<MutableList<VillagerQuest>>() {}.type
                    plugin.gson.fromJson(serializedQuests, type)
                }
            }

        fun Villager.addQuest(quest: VillagerQuest) {
            this.persistentDataContainer.set(villagerQuestDataKey, PersistentDataType.STRING, plugin.gson.toJson(this.quests.apply {  add(quest) }))
        }

        fun Villager.removeQuest(quest: VillagerQuest) {
            this.persistentDataContainer.set(villagerQuestDataKey, PersistentDataType.STRING, plugin.gson.toJson(this.quests.apply { removeIf { q -> q.questInfo.twoWordsDescription == quest.questInfo.twoWordsDescription} }))
        }

    }

}

enum class CharacterType {

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
    THIEF;

    companion object {
        fun getEnumValuesAsStrings(): List<String> {
            return entries.map { it.name }
        }
    }

}