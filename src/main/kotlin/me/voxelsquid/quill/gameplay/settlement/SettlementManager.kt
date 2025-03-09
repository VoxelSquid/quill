package me.voxelsquid.quill.gameplay.settlement

import com.google.gson.reflect.TypeToken
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.QuestIntelligence.Companion.currentSettlement
import me.voxelsquid.quill.QuestIntelligence.Companion.gson
import me.voxelsquid.quill.gameplay.event.SettlementNameGenerateEvent
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.settlement
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.Title.Times
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.craftbukkit.entity.CraftVillager
import org.bukkit.entity.EntityType
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.persistence.PersistentDataType
import java.time.Duration
import kotlin.random.Random

class SettlementManager(val plugin: QuestIntelligence): Listener {

    private val settlementDetectionDistance = 128.0
    private val minimumOfVillagersToSettlementCreation = 5
    private val defaultSettlementName = "Default Settlement Name"

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        this.startSettlementDetectionTask()
        this.startEnteringTick()

        plugin.allowedWorlds.forEach { world ->
            settlements[world] = mutableListOf()
        }

        this.loadSettlements()

        // Adding villagers to settlements in already loaded chunks
        plugin.allowedWorlds.forEach { world ->
            world.entities.filterIsInstance<Villager>().forEach { villager ->
                villager.settlement?.villagers?.add(villager)
            }
        }
    }

    private fun startEnteringTick() {

        plugin.server.scheduler.runTaskTimerAsynchronously(plugin, { _ ->
            plugin.allowedWorlds.forEach { world ->
                world.players.forEach { player ->
                    var settlement = settlements[world]?.find { it.territory.contains(player.location.toVector()) }

                    if (settlement != null && player.currentSettlement == null && settlement.data.settlementName != "Default Settlement Name") {
                        player.showTitle(Title.title(Component.text(settlement.data.settlementName).color(TextColor.fromHexString("#FFAA00")),
                            Component.text(plugin.configManager.language.getString("settlement-entering.entering") ?: ""),
                            Times.times(Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(2))))
                        player.currentSettlement = settlement.data.settlementName
                    }

                    if (settlement == null && player.currentSettlement != null) {
                        settlement = settlements[world]?.find { it.data.settlementName == player.currentSettlement }
                        player.showTitle(Title.title(Component.text("${settlement?.data?.settlementName}").color(TextColor.fromHexString("#FFAA00")),
                            Component.text(plugin.configManager.language.getString("settlement-entering.leaving") ?: ""),
                            Times.times(Duration.ofSeconds(2), Duration.ofSeconds(2), Duration.ofSeconds(2))))
                        player.currentSettlement = null
                    }

                }
            }
        }, 0, 20)

    }

    // Таск, в котором происходит поиск жителей, которые подохдят для создания сетлментов.
    private fun startSettlementDetectionTask() {

        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            plugin.allowedWorlds.forEach { world ->

                // Проходимся по всем жителям в мире, которые нигде не "прописаны".
                world.entities.filterIsInstance<Villager>().filter { it.settlement == null }.let { villagers ->

                    plugin.server.scheduler.runTask(plugin) { _ ->
                        for (villager in villagers.shuffled()) {

                            // Создаём поселение только с теми жителями, которые нигде не "прописаны"
                            val villagersAround = villager.getNearbyEntities(settlementDetectionDistance, settlementDetectionDistance, settlementDetectionDistance)
                                .filterIsInstance<Villager>()
                                .filter { it.settlement == null }
                                .toMutableList()

                            // Добавляем итерируемого жителя в список
                            villagersAround.add(villager)

                            // Если найденные бездомные находятся на территории поселения (или недалеко от него), то автоматически прописываем их
                            settlements[world]?.forEach { settlement ->
                                villagersAround.forEach {
                                    if (settlement.data.center.distance(it.location) <= settlementDetectionDistance + settlementDetectionDistance / 2) {
                                        it.settlement = settlement
                                        (it as CraftVillager).handle.navigation.moveTo(settlement.data.center.x, settlement.data.center.y, settlement.data.center.z, 1.0)
                                    }
                                }
                            }

                            // На всякий случай чистим тех, кто мог получить прописку
                            villagersAround.removeIf { it.settlement != null }

                            // Создание поселения моментально, но название будет сгенерировано чуть позже. Возможно, что работа с локациями не может происходить вне тика сервера.
                            if (villagersAround.size >= minimumOfVillagersToSettlementCreation) {
                                this.createSettlement(villager.world, villager.location, villagersAround)
                                break
                            }

                        }
                    }

                }
            }
        }, 0, 200)
    }

    private fun createSettlement(world: World, center: Location, villagers: List<Villager>){
        plugin.geminiProvider.generateSettlementName(Settlement(Settlement.SettlementData(world.uid, defaultSettlementName, center, System.currentTimeMillis()), villagers.toMutableSet()))
    }

    @EventHandler
    private fun onSettlementNameGenerate(event: SettlementNameGenerateEvent) {
        with(event.settlement) {
            data.settlementName = event.data.townName
            villagers.forEach { villager -> villager.settlement = this }
            world.persistentDataContainer.set(settlementsWorldKey, PersistentDataType.STRING, gson.toJson(settlements[world]?.map { it.data }))
            settlements[world]?.add(this)
            plugin.debug("New settlement was generated: ${event.data.townName}.")
        }
    }

    private fun loadSettlements() {
        plugin.allowedWorlds.forEach { world ->
            world.persistentDataContainer.get(settlementsWorldKey, PersistentDataType.STRING)?.let { serializedSettlements ->
                gson.fromJson(serializedSettlements, object : TypeToken<List<Settlement.SettlementData>>() {}).let { list ->
                    list.forEach { settlementData ->
                        plugin.debug("Settlement loaded: ${settlementData.settlementName}.")
                        settlements[world]?.add(Settlement(settlementData))
                    }
                }
            }
        }
    }

    @EventHandler
    private fun onChunkLoad(event: ChunkLoadEvent) {
        event.chunk.entities.filterIsInstance<Villager>().forEach { villager ->
            villager.settlement?.villagers?.add(villager)
        }
    }

    companion object {
        val settlements: MutableMap<World, MutableList<Settlement>> = mutableMapOf()
        val settlementsWorldKey: NamespacedKey = NamespacedKey(QuestIntelligence.pluginInstance, "settlements")
    }

}