package me.voxelsquid.quill.humanoid.race

import com.github.retrooper.packetevents.protocol.player.TextureProperty
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidEntityExtension.HUMANOID_VILLAGERS_ENABLED
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import java.io.File
import kotlin.random.Random

@Suppress("DEPRECATION")
class HumanoidRaceManager {

    fun load() {
        plugin.saveResource("races.yml", true)
        plugin.saveResource("skins.yml", true)
        val config = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "races.yml"))
        val skins  = YamlConfiguration.loadConfiguration(File(plugin.dataFolder, "skins.yml"))
        config.getKeys(false).forEach { name ->

            val section            = config.getConfigurationSection(name)?: return
            val targetEntityType   = section.getString("target-entity-type") ?: return
            val targetVillagerType = section.getString("target-villager-type")?.let { Registry.VILLAGER_TYPE.get(NamespacedKey.minecraft(it.lowercase())) } ?: Registry.VILLAGER_TYPE.get(NamespacedKey.minecraft("plains"))

            val spawnItems = mutableListOf<SpawnItemStack>()
            section.getStringList("spawn-items").let { items ->
                items.forEach { item ->
                    val (material, min, max) = item.split("-")
                    spawnItems.add(SpawnItemStack(Material.valueOf(material), min.toInt(), max.toInt()))
                }
            }

            val attributes = mutableMapOf<Attribute, Double>()
            section.getStringList("basic-attributes").let { items ->
                items.forEach { item ->
                    val (attribute, value) = item.split("-")
                    attributes[Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attribute.lowercase()))!!] = value.toDouble()
                }
            }

            val textures = mutableListOf<TextureProperty>()
            skins.getKeys(false).forEach { skin ->
                skins.getConfigurationSection(skin)?.let { data ->
                    val race = data.getString("race")
                    val texture = data.getString("texture")
                    val signature = data.getString("signature")
                    if (race == name && texture != null && signature != null) {
                        textures.add(TextureProperty("textures", texture, signature))
                    }
                }
            }

            val voices = mutableListOf<PitchedSound>()
            section.getStringList("sound.voice").forEach { voice ->
                val (sound, min, max) = voice.split("-")
                voices.add(PitchedSound(Sound.valueOf(sound), min.toDouble(), max.toDouble()))
            }

            val hurtSound = section.getString("sound.hurt")!!.let {
                val (sound, min, max) = it.split("-")
                PitchedSound(Sound.valueOf(sound), min.toDouble(), max.toDouble())
            }

            val deathSound = section.getString("sound.death")!!.let {
                val (sound, min, max) = it.split("-")
                PitchedSound(Sound.valueOf(sound), min.toDouble(), max.toDouble())
            }

            val normalCurrency = Material.valueOf(section.getString("normal-currency")!!)
            val specialCurrency = Material.valueOf(section.getString("special-currency")!!)

            val description = section.getString("race-description") ?: ""
            plugin.logger.info("Loading $name race with ${textures.size} skin amount.")
            plugin.logger.info("$name description: $description")
            racesRegistry[name] = Race(
                name,
                EntityType.valueOf(targetEntityType),
                targetVillagerType,
                voices,
                hurtSound,
                deathSound,
                spawnItems,
                attributes,
                textures,
                description,
                normalCurrency,
                specialCurrency
            )

        }
    }

    data class PitchedSound(val sound: Sound, val min: Double, val max: Double)

    data class Race(val name: String,
                    val targetEntityType: EntityType,
                    val targetVillagerType: Villager.Type,
                    val voiceSounds: List<PitchedSound>,
                    val hurtSound: PitchedSound,
                    val deathSound: PitchedSound,
                    val spawnItems: List<SpawnItemStack>,
                    val attributes: Map<Attribute, Double>,
                    val skins: List<TextureProperty>,
                    val description: String = "",
                    val normalCurrency: Material,
                    val specialCurrency: Material) {

        // A predicate that simplifies the verification of an entity that can be racially labeled.
        val matching: (LivingEntity) -> Boolean = { entity ->
            entity is Villager && entity.villagerType == targetVillagerType || entity !is Villager && entity.type == targetEntityType
        }

        companion object {
            // Default villager race will be used if humanoid-villagers in config.yml is false.
            val VILLAGER_RACE = Race("villager",
                EntityType.VILLAGER,
                Villager.Type.PLAINS,
                listOf( Sound.ENTITY_WANDERING_TRADER_YES, Sound.ENTITY_WANDERING_TRADER_NO, Sound.ENTITY_VILLAGER_YES, Sound.ENTITY_VILLAGER_NO, Sound.ENTITY_VINDICATOR_AMBIENT, Sound.ENTITY_VINDICATOR_CELEBRATE, Sound.ENTITY_VILLAGER_TRADE, Sound.ENTITY_PILLAGER_AMBIENT, Sound.ENTITY_WITCH_AMBIENT ).map { PitchedSound(it, 0.9, 1.05) },
                PitchedSound(Sound.ENTITY_VILLAGER_HURT, 0.95, 1.05),
                PitchedSound(Sound.ENTITY_VILLAGER_DEATH, 0.95, 1.05),
                listOf(SpawnItemStack(Material.EMERALD, 32, 64), SpawnItemStack(Material.IRON_INGOT, 32, 64), SpawnItemStack(Material.LEATHER, 32, 64), SpawnItemStack(Material.DIAMOND, 2, 4), SpawnItemStack(Material.BREAD, 32, 64), SpawnItemStack(Material.STICK, 16, 32), SpawnItemStack(Material.APPLE, 32, 64)), mapOf(), listOf(), "", Material.EMERALD, Material.EMERALD_BLOCK)
        }

    }

    data class SpawnItemStack(private val material: Material, private val min: Int, private val max: Int) {
        fun build(): ItemStack = ItemStack(material, Random.nextInt(min, max))
    }

    companion object {

        private val plugin  = QuestIntelligence.pluginInstance
        private val raceKey = NamespacedKey(plugin, "race")
        private val racesRegistry = hashMapOf<String, Race>()

        val LivingEntity.race: Race?
            get() {
                return if (HUMANOID_VILLAGERS_ENABLED) return racesRegistry.values.find { race ->
                    race.matching(this)
                } else Race.VILLAGER_RACE
            }
    }

}