package me.voxelsquid.quill.settlement

import com.google.gson.reflect.TypeToken
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.event.SettlementNameGenerateEvent
import me.voxelsquid.quill.villager.VillagerManager.Companion.settlement
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.persistence.PersistentDataType

/*
TODO Кодинг
 1. Генерация названия для поселения с помощью ИИ. Необходимо создать промпт и учитывать биом.
 2. Необходимо написать механизм сериализации поселений, чтобы информация сохранялась между сессиями.
    Значения будут храниться в PDC мира, но для сохранения даты в JSON нужно будет создать сериализаторы. Скорее всего.
 3. GUI для отображения статистики (кол-во блоков, колоколов, жители, профессии, настроение, еда, изумруды?..), управления и прочего.
 4. Я решил нагло спиздить код симуляции жителей-игроков из RealisticVillagers. В том плагине куча лишнего говна, так что я сделаю свой вариант. Насколько я помню, там своя имплементация NMS жителя и фейкование пакетов.
TODO Рабочий прототип
 1. Игрок приходит в деревню. Во-первых.. с помощью тайлтла отображается название?
    После этого квесты и прочее. По сути, игрок не сразу понимает, что деревня является самостоятельным субъектом. Это будет связано с повышением репутации.
    Получается, всё упирается в BoundingBox? Как я вообще хочу тестировать эту фичу?
*/
class SettlementManager(val plugin: QuestIntelligence): Listener {

    private val settlementDetectionDistance = 128.0
    private val minimumOfVillagersToSettlementCreation = 5
    private val defaultSettlementName = "Default Settlement Name"

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        this.startSettlementDetectionTask()

        // Отображаем границы поселения каждые 20 тиков.
        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            settlements.forEach { settlement ->
                settlement.world.players.forEach { player ->
                    settlement.visualizeSettlementTerritory(player)
                }
            }
        }, 0, 20)
    }

    // Таск, в котором происходит поиск жителей, которые подохдят для создания сетлментов.
    private fun startSettlementDetectionTask() {

        plugin.server.scheduler.runTaskTimer(plugin, { _ ->
            plugin.enabledWorlds.forEach { world ->

                // Проходимся по всем жителям в мире, которые нигде не "прописаны".
                world.entities.filterIsInstance<Villager>().filter { it.settlement == null }.let { villagers ->

                    // Основная логика происходит асинхронно в целях оптимизации.
                    plugin.server.scheduler.runTask(plugin) { _ ->
                        for (villager in villagers.shuffled()) {

                            // Создаём поселение только с теми жителями, которые нигде не "прописаны".
                            val villagersAround = villager.getNearbyEntities(settlementDetectionDistance, settlementDetectionDistance, settlementDetectionDistance)
                                .filterIsInstance<Villager>()
                                .filter { it.settlement == null }
                                .toMutableList()

                            // Добавляем итерируемого жителя в список.
                            villagersAround.add(villager)

                            // Создание поселения моментально, но название будет сгенерировано чуть позже. Возможно, что работа с локациями не может происходить вне тика сервера.
                            if (villagersAround.size >= minimumOfVillagersToSettlementCreation) {
                                plugin.logger.info("Trying to create a new settlement!")
                                this.createSettlement(villager.world, villager.location, villagersAround)
                                break
                            }

                        }
                    }

                }
            }
        }, 0, 200)
    }

    private fun createSettlement(world: World, center: Location, villagers: List<Villager>) : Settlement {
        val data = Settlement.SettlementData(world.uid, defaultSettlementName, center, null, System.currentTimeMillis())
        return Settlement(data, villagers.toMutableSet()).also { settlement ->
            plugin.questGenerator.generateSettlementName(settlement)
            settlements.add(settlement)
        }
    }

    @EventHandler
    private fun onSettlementNameGenerate(event: SettlementNameGenerateEvent) {
        event.settlement.data.settlementName = event.data.townName
        event.settlement.world.players.forEach { player -> player.sendMessage("§8[Quill Debug] §7Settlement §6${event.data.townName} §7has been created!") }
        event.settlement.villagers.forEach { villager -> villager.settlement = event.settlement }
        event.settlement.world.persistentDataContainer.set(settlementsWorldKey, PersistentDataType.STRING, plugin.gson.toJson(settlements.map { it.data }))
    }

    @EventHandler
    private fun onPlayerInteract(event: PlayerInteractEvent) {
        event.clickedBlock?.let { block ->
            if (block.type == Material.BELL) {
                settlements.forEach { settlement ->
                    if (settlement.territory.contains(block.location.toVector())) {
                        settlement.openControlPanelMenu(event.player)
                        return
                    }
                }
            }
        }

    }

    @EventHandler
    private fun onWorldLoad(event: WorldLoadEvent) {
        event.world.persistentDataContainer.get(settlementsWorldKey, PersistentDataType.STRING)?.let { serializedSettlements ->
            plugin.gson.fromJson(serializedSettlements, object : TypeToken<List<Settlement.SettlementData>>() {}).let { list ->
                list.forEach { settlementData ->
                    settlements.add(Settlement(settlementData))
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
        val settlements: MutableList<Settlement> = mutableListOf()
        val settlementsWorldKey: NamespacedKey = NamespacedKey(QuestIntelligence.pluginInstance, "settlements")
    }

}