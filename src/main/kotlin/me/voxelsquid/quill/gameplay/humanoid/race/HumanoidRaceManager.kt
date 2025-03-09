package me.voxelsquid.quill.gameplay.humanoid.race

import com.github.retrooper.packetevents.protocol.player.TextureProperty
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.HUMANOID_VILLAGERS_ENABLED
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

@Suppress("DEPRECATION")
class HumanoidRaceManager {

    fun load() {
        val races = plugin.configManager.races
        val skins = plugin.configManager.skins
        races.getKeys(false).forEach { name ->

            val section            = races.getConfigurationSection(name)?: return
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

            val maleSkins = mutableListOf<TextureProperty>()
            val femaleSkins = mutableListOf<TextureProperty>()
            skins.getKeys(false).forEach { skin ->
                skins.getConfigurationSection(skin)?.let { data ->
                    val race      = data.getString("race")
                    val texture   = data.getString("texture")
                    val signature = data.getString("signature")
                    val gender    = HumanoidManager.HumanoidGender.valueOf(data.getString("gender")!!)
                    if (race == name && texture != null && signature != null) {

                        when (gender) {
                            HumanoidManager.HumanoidGender.MALE -> maleSkins.add(TextureProperty("textures", texture, signature))
                            HumanoidManager.HumanoidGender.FEMALE -> femaleSkins.add(TextureProperty("textures", texture, signature))
                        }

                    }
                }
            }

            val maleVoices = mutableListOf<PitchedSound>()
            section.getStringList("sound.male.voice").forEach { voice ->
                val (sound, min, max) = voice.split("-")
                maleVoices.add(PitchedSound(Sound.valueOf(sound), min.toDouble(), max.toDouble()))
            }

            val maleHurtSound = section.getString("sound.male.hurt")!!.let {
                val (sound, min, max) = it.split("-")
                PitchedSound(Sound.valueOf(sound), min.toDouble(), max.toDouble())
            }

            val maleDeathSound = section.getString("sound.male.death")!!.let {
                val (sound, min, max) = it.split("-")
                PitchedSound(Sound.valueOf(sound), min.toDouble(), max.toDouble())
            }

            val femaleVoices = mutableListOf<PitchedSound>()
            section.getStringList("sound.female.voice").forEach { voice ->
                val (sound, min, max) = voice.split("-")
                femaleVoices.add(PitchedSound(Sound.valueOf(sound), min.toDouble(), max.toDouble()))
            }

            val femaleHurtSound = section.getString("sound.female.hurt")!!.let {
                val (sound, min, max) = it.split("-")
                PitchedSound(Sound.valueOf(sound), min.toDouble(), max.toDouble())
            }

            val femaleDeathSound = section.getString("sound.female.death")!!.let {
                val (sound, min, max) = it.split("-")
                PitchedSound(Sound.valueOf(sound), min.toDouble(), max.toDouble())
            }

            val normalCurrency = Material.valueOf(section.getString("normal-currency")!!)
            val specialCurrency = Material.valueOf(section.getString("special-currency")!!)

            val description = section.getString("race-description") ?: ""
            plugin.debug("Loading $name race with ${maleSkins.size} male skin variations.")
            plugin.debug("Loading $name race with ${femaleSkins.size} female skin variations.")
            racesRegistry[name] = Race(
                name,
                EntityType.valueOf(targetEntityType),
                targetVillagerType,
                maleVoices,
                femaleVoices,
                maleHurtSound,
                maleDeathSound,
                femaleHurtSound,
                femaleDeathSound,
                spawnItems,
                attributes,
                maleSkins,
                femaleSkins,
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
                    val maleVoices: List<PitchedSound>,
                    val femaleVoices: List<PitchedSound>,
                    val maleHurtSound: PitchedSound,
                    val maleDeathSound: PitchedSound,
                    val femaleHurtSound: PitchedSound,
                    val femaleDeathSound: PitchedSound,
                    val spawnItems: List<SpawnItemStack>,
                    val attributes: Map<Attribute, Double>,
                    val maleSkins: List<TextureProperty>,
                    val femaleSkins: List<TextureProperty>,
                    val description: String = "",
                    val normalCurrency: Material,
                    val specialCurrency: Material) {

        // A predicate that simplifies the verification of an entity that can be racially labeled.
        val matching: (LivingEntity) -> Boolean = { entity ->
            entity is Villager && entity.villagerType == targetVillagerType || entity !is Villager && entity.type == targetEntityType
        }

        companion object {

            private val voices = listOf( Sound.ENTITY_WANDERING_TRADER_YES, Sound.ENTITY_WANDERING_TRADER_NO, Sound.ENTITY_VILLAGER_YES, Sound.ENTITY_VILLAGER_NO, Sound.ENTITY_VINDICATOR_AMBIENT, Sound.ENTITY_VINDICATOR_CELEBRATE, Sound.ENTITY_VILLAGER_TRADE, Sound.ENTITY_PILLAGER_AMBIENT, Sound.ENTITY_WITCH_AMBIENT ).map { PitchedSound(it, 0.9, 1.05) }

            // Default villager race will be used if humanoid-villagers in config.yml is false.
            val VILLAGER_RACE = Race("villager",
                EntityType.VILLAGER,
                Villager.Type.PLAINS,
                voices, voices,
                PitchedSound(Sound.ENTITY_VILLAGER_HURT, 0.95, 1.05),
                PitchedSound(Sound.ENTITY_VILLAGER_DEATH, 0.95, 1.05),
                PitchedSound(Sound.ENTITY_VILLAGER_HURT, 0.95, 1.05),
                PitchedSound(Sound.ENTITY_VILLAGER_DEATH, 0.95, 1.05),
                listOf(SpawnItemStack(Material.EMERALD, 32, 64), SpawnItemStack(Material.IRON_INGOT, 32, 64), SpawnItemStack(Material.LEATHER, 32, 64), SpawnItemStack(Material.DIAMOND, 2, 4), SpawnItemStack(Material.BREAD, 32, 64), SpawnItemStack(Material.STICK, 16, 32), SpawnItemStack(Material.APPLE, 32, 64)), mapOf(), listOf(), listOf(), "", Material.EMERALD, Material.EMERALD_BLOCK)
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