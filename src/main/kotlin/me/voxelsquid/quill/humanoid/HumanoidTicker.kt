package me.voxelsquid.quill.humanoid

import com.google.common.reflect.TypeToken
import io.papermc.paper.event.player.PlayerTradeEvent
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.event.QuestGenerateEvent
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidEntityExtension.getVoicePitch
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidEntityExtension.getVoiceSound
import me.voxelsquid.quill.humanoid.race.HumanoidRaceManager.Companion.race
import me.voxelsquid.quill.quest.QuestManager
import me.voxelsquid.quill.quest.data.VillagerQuest
import me.voxelsquid.quill.settlement.Settlement
import me.voxelsquid.quill.settlement.SettlementManager.Companion.settlements
import me.voxelsquid.quill.util.InventorySerializer
import me.voxelsquid.quill.villager.ProfessionManager
import me.voxelsquid.quill.villager.ReputationManager
import me.voxelsquid.quill.villager.interaction.DialogueManager
import me.voxelsquid.quill.villager.interaction.InteractionMenuManager
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
import kotlin.random.Random

// TODO: Необходимо заново генерировать PVD, когда малой взрослеет (лол)
class HumanoidTicker : Listener {

    private val interactionManager = InteractionMenuManager(plugin)
    private val professionManager  = ProfessionManager()
    private val reputationManager  = ReputationManager()
    private val tradeHandler       = HumanoidTradeHandler()

    private val questIntervalTicks = plugin.config.getLong("core-settings.tick-period.quest")
    private val foodIntervalTicks  = plugin.config.getLong("core-settings.tick-period.food")
    private val workIntervalTicks  = plugin.config.getLong("core-settings.tick-period.work")

    init {

        plugin.server.pluginManager.registerEvents(this, plugin)

        // Quest tick
        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            questManager.prepareQuest()
        }, 0, if (plugin.debug) 200 else questIntervalTicks)

        // Work tick
        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            professionManager.produceProfessionItem()
        }, 0, if (plugin.debug) 200 else workIntervalTicks)

        // Food tick
        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            val villagers = mutableListOf<Villager>()
            plugin.enabledWorlds.forEach { world -> villagers.addAll(world.entities.filterIsInstance<Villager>()) }

            plugin.server.scheduler.runTaskAsynchronously(plugin, { _ ->
                villagers.shuffled().forEachIndexed { index, villager ->
                    if (villager.pose != Pose.SLEEPING) {

                        if (villager.hunger > 0) villager.hunger -= 2.5
                        else villager.hunger = 0.0

                        plugin.server.scheduler.runTaskLater(plugin, { _ ->
                            if (villager.hunger <= 17.5) {
                                villager.eat()
                            }
                        }, 5 + (index * 2L).coerceAtMost(40) + Random.nextInt(250))

                    }
                }
            })

        }, 0, foodIntervalTicks)

    }

    @EventHandler
    private fun onQuestGenerate(event: QuestGenerateEvent) {
        val quest = event.quest
        event.villager.addQuest(quest)
        plugin.debug("Successfully generated a new quest: ${quest.questInfo} and added it to villager ${event.villager.customName}.")
        plugin.debug("=== --- === --- === --- === --- === --- === --- === --- === --- ===")
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

        // Сперва проверяем, если это попытка завершения квеста
        villager.quests.forEach { quest ->
            if (trade.ingredients[0].isSimilar(quest.questItem) && trade.result.isSimilar(quest.rewardItem)) {
                questManager.finishQuest(player, villager, quest, trade.ingredients[0], trade.result)
                return
            }
        }

        if (!event.isCancelled) {
            villager.takeItemFromQuillInventory(trade.result, trade.result.amount)
        }

    }

    companion object {

        private var plugin          = QuestIntelligence.pluginInstance
        private var questManager    = QuestManager(plugin)
        private var dialogueManager = DialogueManager(plugin)

        private val villagerQuestDataKey:    NamespacedKey = NamespacedKey(plugin, "questData")
        private val villagerHungerKey:       NamespacedKey = NamespacedKey(plugin, "hunger")
        private val villagerSettlementKey:   NamespacedKey = NamespacedKey(plugin, "settlement")
        val villagerInventoryKey:            NamespacedKey = NamespacedKey(plugin, "inventory")

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
            inventory.filterNotNull().find { it.isSimilar(item) }?.apply { amount -= amountToTake }
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

        fun Villager.consume(
            item: ItemStack,
            sound: Sound,
            duration: Int,
            period: Long = 5L,
            onDone: () -> Unit,
        ) {
            val nmsVillager = (this as CraftVillager).handle
            val nmsItem = CraftItemStack.asNMSCopy(item)

            var i = 0

            plugin.server.scheduler.runTaskTimer(plugin, { task ->

                nmsVillager.isNoAi = true
                nmsVillager.setItemSlot(EquipmentSlot.MAINHAND, nmsItem)
                this.world.playSound(this.location, sound, 1F, 1F)

                if (i++ >= duration) {
                    nmsVillager.setItemSlot(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY)
                    onDone.invoke()
                    nmsVillager.isNoAi = false
                    task.cancel()
                }

            }, 0, period)
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

                villager.world.playSound(villager.location, villager.getVoiceSound(), 1F, villager.getVoicePitch())
                villager.world.playSound(villager.location, Sound.ENTITY_PLAYER_BURP, 1F, 1F)
                villager.hunger += 7.5

                if (hunger >= 20.0) {
                    villager.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 200, 1))
                }
            }
        }

        fun LivingEntity.talk(player: Player, text: String?, displaySize: Float = plugin.config.getDouble("core-settings.dialogue-text-display.default-size").toFloat(), followDuringDialogue: Boolean = true, interruptPreviousDialogue: Boolean = false) {
            text?.let {
                dialogueManager.startDialogue(player to this, it, size = displaySize, follow = followDuringDialogue, interrupt = interruptPreviousDialogue)
            }
        }

        val Villager.professionLevelName : String
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
                    return settlements[this.world]?.find { it.data.settlementName == settlementName }
                }
                return null
            }
            set(settlement) {
                settlement?.let {
                    persistentDataContainer.set(villagerSettlementKey, PersistentDataType.STRING, it.data.settlementName)
                }
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

                if (race != null && inventory.isEmpty) {
                    race!!.spawnItems.forEach { item ->
                        inventory.addItem(item.build())
                    }
                }

                return inventory
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

