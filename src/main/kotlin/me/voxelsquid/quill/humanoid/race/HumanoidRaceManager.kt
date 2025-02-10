package me.voxelsquid.quill.humanoid.race

import com.github.retrooper.packetevents.protocol.player.TextureProperty
import me.voxelsquid.quill.QuestIntelligence
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
            val defaultReputation  = section.getDouble("default-reputation")

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

            val description = section.getString("race-description") ?: ""
            plugin.logger.info("Loading $name race with ${textures.size} skin amount.")
            plugin.logger.info("$name description: $description")
            racesRegistry[name] = Race(
                name,
                EntityType.valueOf(targetEntityType),
                targetVillagerType,
                defaultReputation,
                voices,
                hurtSound,
                deathSound,
                spawnItems,
                attributes,
                textures,
                description
            )

        }
    }

    data class PitchedSound(val sound: Sound, val min: Double, val max: Double)

    data class Race(val name: String,
                    val targetEntityType: EntityType,
                    val targetVillagerType: Villager.Type,
                    val defaultReputation: Double,
                    val voiceSounds: List<PitchedSound>,
                    val hurtSound: PitchedSound,
                    val deathSound: PitchedSound,
                    val spawnItems: List<SpawnItemStack>,
                    val attributes: Map<Attribute, Double>,
                    val skins: List<TextureProperty>,
                    val description: String = "") {

        // A predicate that simplifies the verification of an entity that can be racially labeled.
        val matching: (LivingEntity) -> Boolean = { entity ->
            entity is Villager && entity.villagerType == targetVillagerType || entity !is Villager && entity.type == targetEntityType
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
                return racesRegistry.values.find { race ->
                    race.matching(this)
                }
            }
    }

}