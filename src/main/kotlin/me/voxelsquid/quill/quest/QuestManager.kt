package me.voxelsquid.quill.quest

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.getOminousBanner
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidController.HumanoidNamespace.characterKey
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidController.HumanoidNamespace.personalDataKey
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidEntityExtension.getCharacterType
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidEntityExtension.getPersonalHumanoidData
import me.voxelsquid.quill.quest.data.QuestType
import me.voxelsquid.quill.quest.data.VillagerQuest
import me.voxelsquid.quill.util.InventorySerializer
import me.voxelsquid.quill.util.ItemStackCalculator.Companion.calculatePrice
import me.voxelsquid.quill.util.ItemStackCalculator.Companion.getMaterialPrice
import me.voxelsquid.quill.villager.ReputationManager
import me.voxelsquid.quill.villager.ReputationManager.Companion.fame
import me.voxelsquid.quill.villager.ReputationManager.Companion.fameLevel
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.addItemToQuillInventory
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.consume
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.eat
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.hunger
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.quests
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.quillInventory
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.removeQuest
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.takeItemFromQuillInventory
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.talk
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.updateQuests
import me.voxelsquid.quill.humanoid.HumanoidTicker.Companion.villagerInventoryKey
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.entity.Villager.Profession
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BundleMeta
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionType
import kotlin.random.Random

class QuestManager(private val plugin: QuestIntelligence) {

    private val professionItems = mutableMapOf<Profession, Map<Material, Pair<Int, Int>>>()
    private val allowedQuests   = arrayOf(QuestType.PROFESSION_ITEM_GATHERING, QuestType.MUSIC_DISC, QuestType.OMINOUS_BANNER, QuestType.BOOZE)

    init {
        this.initializeProfessionItems()
    }

    private fun initializeProfessionItems() {
        Registry.VILLAGER_PROFESSION.forEach { profession ->

            if (profession == Profession.NONE)
                return@forEach

            // Создаем карту для хранения предметов
            val items = mutableMapOf<Material, Pair<Int, Int>>()

            // Извлекаем и обрабатываем список предметов
            plugin.config.getStringList("villager-item-producing.profession.$profession.item-priority").forEach { line ->

                val (materialName, amountRange) = line.split("~")
                val (min, max) = amountRange.split("-").map(String::toInt).let { range ->
                    if (range.size == 1) range[0] to range[0] else range[0] to range[1]
                }

                val amount = min to max
                if (materialName.contains('@')) {
                    Material.entries.filter { material: Material -> material.toString().contains(materialName.removePrefix("@")) }.forEach {
                        items[it] = amount
                    }
                } else items[Material.valueOf(materialName)] = amount
            }

            professionItems[profession] = items
        }
    }

    fun prepareQuest() {

        plugin.debug("=== --- === --- === --- === --- === --- === --- === --- === --- ===")
        plugin.debug("Time for a new quest! Generating...")

        if (plugin.enabledWorlds.isEmpty()) {
            plugin.logger.severe("No target worlds in config.yml! Disabling the plugin.")
            plugin.server.pluginManager.disablePlugin(plugin)
        }

        plugin.debug("Looking for a random villager...")
        val villager = this.getRandomVillager() ?: run {
            plugin.debug("Can't find a villager. Cancelling.")
            return
        }

        // Old quest cleaning
        villager.quests.forEach { quest ->
            if ((System.currentTimeMillis() - quest.timeCreated) / 1000 * 20 > plugin.config.getLong("core-settings.quest-time-limit")) {
                villager.removeQuest(quest)
            }
        }

        if (villager.quests.size >= villager.villagerLevel) {
            plugin.debug("Found villager has too much quests (${villager.quests.size}). Cancelling.")
            return
        }

        plugin.debug("Found a villager!")
        if (villager.persistentDataContainer.get(personalDataKey, PersistentDataType.STRING) == null) {
            plugin.logger.info("Villager at ${villager.location}.")
            plugin.debug("Found villager has no personal data. Generating it!")
            plugin.questGenerator.generatePersonalHumanoidData(villager)
            return
        }

        if (villager.profession == Profession.NONE) {
            plugin.debug("Villager without profession can't have any quests!")
            return
        }

        val questType = this.determineQuestType(villager)

        plugin.debug("Quest type is $questType. Selecting the quest item!")
        val questItem = when (questType) {
            QuestType.MUSIC_DISC -> ItemStack(discs.random())
            QuestType.OMINOUS_BANNER -> getOminousBanner()
            QuestType.BOOZE -> this.randomPotion()
            QuestType.ENCHANTED_BOOK -> this.randomEnchantedBook(villager)
            QuestType.SMITHING_TEMPLATE -> this.randomSmithingTemplate(villager)
            QuestType.FOOD -> this.randomFood()
            QuestType.TREASURE_HUNT -> this.randomTreasureItem(villager)
            else -> villager.prioritizedItem
        }

        plugin.debug("Quest item is ${questItem.type}. Building a quest...")
        val quest = this.buildQuest(questType, villager, questItem) ?: return
        this.requestQuestData(villager, quest)
    }

    fun buildQuest(questType: QuestType, villager: Villager, questItem: ItemStack): VillagerQuest.Builder? {

        val quest = VillagerQuest.Builder().apply {
            this.setQuestType(questType)
            this.setQuestItem(questItem)
        }

        plugin.debug("Generating a quest reward...")
        var rewardPrice = when (questType) {
            QuestType.OMINOUS_BANNER -> plugin.configurationClip.promptsConfig.getInt("ominous-banner-quest.reward-points")
            QuestType.BOOZE -> plugin.configurationClip.promptsConfig.getInt("booze-quest.reward-points")
            QuestType.ENCHANTED_BOOK -> plugin.configurationClip.promptsConfig.getInt("enchanted-book-quest.reward-points")
            else -> questItem.calculatePrice()
        }

        // If villager needs food, they will pay thrice TODO: make it configurable
        if (questType == QuestType.FOOD)
            rewardPrice *= 3

        val villagerItems = villager.quillInventory.filterNotNull().filter {
            it.calculatePrice() > 0 && !isProfessionItem(villager.profession, it)
        }
        val inventoryPrice = villagerItems.calculatePrice()

        plugin.debug("Item price: $rewardPrice.")
        plugin.debug("Inventory price: $inventoryPrice.")

        if (rewardPrice > inventoryPrice) {
            plugin.debug("Item price is bigger than inventory price. Cancelling quest generation.")
            return null
        }

        val barterItems = this.generateQuestReward(questType, questItem, inventoryPrice, villagerItems)
        val questReward = when {
            barterItems.isEmpty() -> {
                plugin.debug("Villager has no items to use them as a reward. Cancelling quest generation.")
                return null
            }

            barterItems.size > 1 -> this.bundle(barterItems)
            else -> barterItems[0]
        }


        plugin.debug("Quest reward is: [${questReward.type}, amount is ${questReward.amount}]!")
        return quest.setRewardItem(questReward).setRewardPrice(rewardPrice)
    }

    private fun determineQuestType(villager: Villager): QuestType {

        val types = this.allowedQuests.clone().toMutableList()

        // Some quests are profession specific
        when (villager.profession) {
            Profession.ARMORER -> types += QuestType.SMITHING_TEMPLATE
            Profession.LIBRARIAN -> { types += QuestType.ENCHANTED_BOOK; types += QuestType.TREASURE_HUNT }
            Profession.CARTOGRAPHER -> types += QuestType.TREASURE_HUNT
            else -> {}
        }

        // Villagers can't have two quests of the same type
        types.apply {
            removeIf { type ->
                villager.quests.find { it.type == type } != null
            }
        }

        return when {
            villager.hunger <= 10 -> QuestType.FOOD
            else -> types.random()
        }
    }

    fun finishQuest(player: Player, villager: Villager, quest: VillagerQuest, questItem: ItemStack, rewardItem: ItemStack) {

        // Уменьшаем кол-во предмета, который был выдан в качестве награды
        val inventory = villager.quillInventory
        var slotIndex: Int

        if (rewardItem.type == Material.BUNDLE) {
            val bundleMeta = rewardItem.itemMeta as BundleMeta
            bundleMeta.items.forEach { reward ->
                slotIndex = inventory.indexOf(inventory.first { item -> item != null && item.type == reward.type })
                inventory.getItem(slotIndex)?.let { item -> item.amount -= reward.amount }
            }
        } else {
            slotIndex = inventory.indexOf(inventory.first { item -> item != null && item.type == rewardItem.type })
            inventory.getItem(slotIndex)?.let { item -> item.amount -= quest.rewardItem.amount }
        }

        // Добавляем предмет в инвентарь жителя и сохраняем его
        inventory.addItem(questItem)
        villager.persistentDataContainer.set(villagerInventoryKey, PersistentDataType.STRING, InventorySerializer.jsonifyInventory(inventory).toString())

        // TODO: Добавляем игроку в стату +1 выполненный квест
        player.fame += 0.5

        // Выдаём экспу игроку и жителю
        player.giveExp(quest.rewardPrice / 20, true)
        villager.villagerExperience += quest.rewardPrice / 250

        // Закрываем инвентарь через один тик, чтобы избежать багов
        plugin.server.scheduler.runTaskLater(plugin, { _ ->
            player.closeInventory()
            if (quest.type != QuestType.BOOZE) villager.talk(player, when (player.fameLevel) {
                ReputationManager.Companion.Fame.INFAMOUS -> quest.questInfo.rewardTextForInfamousPlayer
                ReputationManager.Companion.Fame.NEUTRAL  -> quest.questInfo.rewardTextForNeutralPlayer
                ReputationManager.Companion.Fame.FAMOUS   -> quest.questInfo.rewardTextForFamousPlayer
            })
            villager.removeQuest(quest)
            villager.updateQuests()
        }, 1L)

        when (quest.type) {
            QuestType.BOOZE -> this.finishBrewQuest(villager, questItem) {
                villager.talk(player, when (player.fameLevel) {
                    ReputationManager.Companion.Fame.INFAMOUS -> quest.questInfo.rewardTextForInfamousPlayer
                    ReputationManager.Companion.Fame.NEUTRAL  -> quest.questInfo.rewardTextForNeutralPlayer
                    ReputationManager.Companion.Fame.FAMOUS   -> quest.questInfo.rewardTextForFamousPlayer
                })
            }

            QuestType.FOOD -> villager.eat()

            else -> { /* 42 */ }
        }

        return
    }

    private fun finishBrewQuest(villager: Villager, potion: ItemStack, reply: () -> Unit) {
        villager.consume(potion, Sound.ENTITY_GENERIC_DRINK, 6) {
            villager.takeItemFromQuillInventory(potion, 1)
            villager.addItemToQuillInventory(ItemStack(Material.GLASS_BOTTLE))
            villager.addPotionEffect((potion.itemMeta as PotionMeta).basePotionType!!.potionEffects.first())
            reply.invoke()
        }
    }

    fun getTreasureItemDescription(item: ItemStack): String {
        return this.treasureItems.find { it.first == item.type }?.third ?: ""
    }

    private fun randomTreasureItem(villager: Villager): ItemStack {
        val (material, range) = treasureItems.filter { !villager.quillInventory.contains(it.first) }.random()
        val amount = (range.first + Random.nextInt(range.second)).apply { if (this != 1 && this % 2 != 0) this.inc() }
        return ItemStack(material, amount)
    }

    private val treasureItems: MutableList<Triple<Material, Pair<Int, Int>, String>> =
        mutableListOf<Triple<Material, Pair<Int, Int>, String>>().apply {
            val data = plugin.configurationClip.promptsConfig.getStringList("treasure-hunt-quest.allowed-items")
            for (line in data) {
                val (materialName, amount, description) = line.split("~")
                val (min, max) = amount.split("-")
                this.add(Triple(Material.valueOf(materialName), min.toInt() to max.toInt(), description))
            }
        }

    private fun randomPotion(): ItemStack {
        val allowedPotionTypes = plugin.configurationClip.promptsConfig.getStringList("booze-quest.allowed-potion-types")
        return ItemStack(Material.POTION).apply {
            itemMeta = (this.itemMeta as PotionMeta).apply {
                this.basePotionType = PotionType.valueOf(allowedPotionTypes.random())
            }
        }
    }

    private val enchantmentRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
    private fun randomEnchantedBook(villager: Villager): ItemStack {

        val allowedEnchantments = plugin.configurationClip.promptsConfig.getStringList("enchanted-book-quest.allowed-enchantments")
        allowedEnchantments.removeIf { enchantName ->
            villager.quillInventory.contents.filterNotNull().any { item ->
                item.itemMeta is EnchantmentStorageMeta && (item.itemMeta as EnchantmentStorageMeta).hasStoredEnchant(enchantName)
            }
        }

        val defaultEnchantment = Enchantment.UNBREAKING
        val enchantment = enchantmentRegistry.get(NamespacedKey.minecraft(allowedEnchantments.random().lowercase())) ?: defaultEnchantment

        return ItemStack(Material.ENCHANTED_BOOK).apply {
            itemMeta = (itemMeta as EnchantmentStorageMeta).apply {
                addStoredEnchant(enchantment, enchantment.maxLevel, false)
            }
        }
    }

    private fun EnchantmentStorageMeta.hasStoredEnchant(key: String): Boolean {
        val enchantment = enchantmentRegistry.get(NamespacedKey.minecraft(key.lowercase())) ?: return false
        return this.hasStoredEnchant(enchantment)
    }

    private fun randomSmithingTemplate(villager: Villager): ItemStack =
        ItemStack(Material.entries.filter { it.toString().contains("SMITHING_TEMPLATE") && !villager.quillInventory.contains(it) }.random())

    private fun randomFood(): ItemStack =
        ItemStack(Material.valueOf(plugin.configurationClip.promptsConfig.getStringList("food-quest.allowed-types").random()), 10 + Random.nextInt(6))

    private fun getRandomVillager(): Villager? {
        val villagers = plugin.enabledWorlds.random().entities.filterIsInstance<Villager>()
        return villagers.randomOrNull()
    }

    private fun requestQuestData(villager: Villager, quest: VillagerQuest.Builder) {
        plugin.server.scheduler.runTaskAsynchronously(plugin) { _ ->
            plugin.questGenerator.generateQuestData(this, villager, quest)
        }
    }

    private fun isProfessionItem(profession: Profession, item: ItemStack): Boolean =
        item.type in professionItems[profession]?.keys.orEmpty()

    /**
     * Создает мешочек с заданным списком итемов внутри.
     *
     * @param items Список предметов, которые будут упакованы в мешочек.
     */
    private fun bundle(items: List<ItemStack>): ItemStack {
        return ItemStack(Material.BUNDLE, 1).apply {
            itemMeta = (itemMeta as BundleMeta).apply {
                items.forEach(::addItem)
            }
        }
    }

    private fun generateQuestReward(
        questType: QuestType,
        questItem: ItemStack,
        inventoryPrice: Int,
        villagerItems: List<ItemStack>
    ): MutableList<ItemStack> {

        val questItemPrice = when (questType) {
            QuestType.OMINOUS_BANNER -> plugin.configurationClip.promptsConfig.getInt("ominous-banner-quest.reward-points")
            QuestType.BOOZE -> plugin.configurationClip.promptsConfig.getInt("booze-quest.reward-points")
            QuestType.ENCHANTED_BOOK -> plugin.configurationClip.promptsConfig.getInt("enchanted-book-quest.reward-points")
            else -> questItem.type.getMaterialPrice()
        }

        val sortedInventory = villagerItems.toMutableList().apply {
            removeIf { it.type.isEdible || it.type == Material.ENCHANTED_BOOK || it.isSimilar(getOminousBanner())} // villagers won't use food for trading (and enchanted books as well, im too lazy to implement that logic right now)
            sortedBy { if (it.type == Material.EMERALD) 0 else 1 } // emeralds > other stuff
        }

        if (questItemPrice == 0) {
            plugin.logger.severe("${questItem.type} price is zero! Fix it and reload the plugin!")
            return mutableListOf()
        }

        val possibleAmount      = inventoryPrice / questItemPrice
        val requiredRewardPrice = minOf(questItem.amount, possibleAmount) * questItemPrice
        val rewardItems         = mutableListOf<ItemStack>()

        // Проходимся по инвентарю с целью найти предметы которыми можно заплатить за questItem
        for (iterableItem in sortedInventory) {

            // Пропускаем не подходящие по цене предметы и исключаем "одинаковые сделки"
            if (iterableItem.type.getMaterialPrice() > requiredRewardPrice || iterableItem.type == questItem.type)
                continue

            // Считаем поштучно
            for (i in 1..iterableItem.amount) {

                val currentRewardPrice = rewardItems.calculatePrice()
                if (currentRewardPrice >= requiredRewardPrice)
                    return rewardItems

                val existingItem = rewardItems.find { it.type == iterableItem.type }
                if (existingItem != null) {
                    existingItem.amount = i
                } else {
                    rewardItems.add(iterableItem.clone().apply { amount = i })
                }

            }

        }

        return rewardItems
    }

    private val Villager.prioritizedItem: ItemStack
        get() {
            val inventory = quillInventory
            if (profession != Profession.NONE)
                professionItems[profession]?.let { prioritizedItems ->
                    for ((material, range) in prioritizedItems.toList().shuffled().toMap()) {
                        if (!inventory.contains(material, range.second))
                            return ItemStack(material, if (range.first != range.second) Random.nextInt(range.first, range.second) else 1).apply { if (amount > 1 && amount % 2 != 0) amount++ }
                    }
                }
            return ItemStack(Material.AIR)
        }

    companion object {
        private val discs = Material.entries.filter { material: Material -> material.isRecord }
    }

}