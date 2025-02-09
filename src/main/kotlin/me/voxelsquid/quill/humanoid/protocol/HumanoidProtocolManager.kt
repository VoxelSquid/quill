package me.voxelsquid.quill.humanoid.protocol

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent
import com.github.retrooper.packetevents.protocol.attribute.Attributes
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.player.GameMode
import com.github.retrooper.packetevents.protocol.player.SkinSection
import com.github.retrooper.packetevents.protocol.player.TextureProperty
import com.github.retrooper.packetevents.protocol.player.UserProfile
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import com.github.retrooper.packetevents.wrapper.play.server.*
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.event.HumanoidInitializationEvent
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidController
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidController.HumanoidNamespace.personalDataKey
import me.voxelsquid.quill.humanoid.HumanoidManager.HumanoidController.PersonalHumanoidData
import me.voxelsquid.quill.humanoid.race.HumanoidRaceManager.Companion.race
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.craftbukkit.entity.CraftEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class HumanoidProtocolManager(private val humanoidRegistry: HashMap<LivingEntity, HumanoidController>) : SimplePacketListenerAbstract(), Listener {

    companion object {

        private val plugin = QuestIntelligence.pluginInstance

        // Predicate for removing invalid and necessary EntityData (especially VILLAGER_DATA, which causes a protocol error on the client).
        private val MUST_BE_REMOVED: (EntityData) -> Boolean = { it ->
            it.index == 15 || it.index == 16 || it.index == 17 && it.type != EntityDataTypes.BYTE || it.type == EntityDataTypes.VILLAGER_DATA
        }

    }

    @EventHandler
    private fun onPlayerQuit(event: PlayerQuitEvent) {
        humanoidRegistry.values.forEach { provider ->
            provider.subscribers.remove(event.player)
        }
    }

    @EventHandler
    private fun onHumanoidInitialization(event: HumanoidInitializationEvent) {

        val player   = event.player
        val entity   = event.entity
        val provider = event.humanoidProvider
        val metadata = event.metadata.toMutableList().also {
            it.add(EntityData(17, EntityDataTypes.BYTE, SkinSection.ALL.mask))
        }

        // Applying base attributes (I'm doing it here because I don't know where else it should be)
        entity.race?.let { race ->
            race.attributes.forEach { (attribute, value) ->
                entity.getAttribute(attribute)?.baseValue = value
            }
        }

        // Before sending SPAWN_ENTITY packet with player data, we MUST send PLAYER_INFO.
        // Note the modification of the metadata list. We add data with index 17 to display all skin layers.
        val info = WrapperPlayServerPlayerInfoUpdate.PlayerInfo(provider.profile, false, 20, GameMode.SURVIVAL, null, null);
        val playerInfoPacket = WrapperPlayServerPlayerInfoUpdate(EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER, WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED, WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LATENCY, WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_GAME_MODE, WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME), info)
        val spawnEntityPacket = WrapperPlayServerSpawnEntity(entity.entityId, provider.profile.uuid, EntityTypes.PLAYER, entity.location.toPacketEventsLocation(), entity.location.yaw, 0, null)

        player.sendMessage(" §b> Sending spawn packet and cached metadata. §7[id ${entity.entityId}]")
        player.sendPacket(playerInfoPacket)
        player.sendPacket(spawnEntityPacket)
        player.sendPacket(WrapperPlayServerEntityMetadata(entity.entityId, metadata))
        player.sendPacket(WrapperPlayServerUpdateAttributes(entity.entityId,
            listOf(WrapperPlayServerUpdateAttributes.Property(Attributes.SCALE, entity.getAttribute(Attribute.SCALE)?.value ?: 1.0, emptyList())))
        )

        // Delete the information about the fake player after a second, so that the skin has time to load and the player list doesn't show non-existent nicknames.
        plugin.server.scheduler.runTaskLater(plugin, { _ ->
            player.sendPacket(WrapperPlayServerPlayerInfoRemove(provider.profile.uuid))
        }, 20L)

        // Don't forget to collect the garbage.
        humanoidRegistry.keys.filter { !it.isValid }.forEach { garbage ->
            humanoidRegistry.remove(garbage)
        }

    }

    // We are currently listening for the sending of four packets: SPAWN_ENTITY, ENTITY_METADATA, ENTITY_HEAD_LOOK, and DESTROY_ENTITIES.
    override fun onPacketPlaySend(event: PacketPlaySendEvent) {

        when (event.packetType) {

            // To avoid showing the villagers' real nosy model, we cancel this packet until a packet with a fake entity is sent to the player.
            PacketType.Play.Server.SPAWN_ENTITY -> {

                val player = event.getPlayer<Player>()
                val world  = player.world
                val packet = WrapperPlayServerSpawnEntity(event)
                val entity = SpigotConversionUtil.getEntityById(world, packet.entityId) ?: return

                when (entity) {
                    is Villager -> {

                        player.sendMessage(" §2> Trying to spawn a villager!")

                        val humanoidProvider = humanoidRegistry[entity] ?: run {
                            plugin.logger.info("Preventing villager with ID ${packet.entityId} from showing.")
                            event.isCancelled = true
                            return
                        }

                        if (!humanoidProvider.subscribers.contains(player)) {
                            plugin.logger.info("Preventing disguised villager with ID ${packet.entityId} from undisguising.")
                            event.isCancelled = true
                        }

                    }
                }
            }

            // To display arrows in the body, burning, potion effects, and other metadata of player-disguised villagers, the ENTITY_METADATA packet handling must be changed.
            // If the packet being sent is a villager metadata, cancel it, modify some data (if you don't do this, the player will be kicked because of a protocol error), and resend.
            // Besides, a mechanism of lazy initialization is implemented here. If a villager has no HumanoidProvider, it means only one thing — the player sees it for the FIRST time.
            PacketType.Play.Server.ENTITY_METADATA -> {

                val player = event.getPlayer<Player>()
                val world  = player.world
                val packet = PacketWrapper(event, false)
                val entity = SpigotConversionUtil.getEntityById(world, packet.readVarInt()) ?: return

                if (entity is Villager) {

                    val humanoidProvider = humanoidRegistry[entity]
                    val metadata = packet.readEntityMetadata()

                    val registered  = humanoidProvider != null
                    val subscribed  = humanoidProvider?.subscribers?.contains(player) ?: false
                    val fixedPacket = metadata.removeIf(MUST_BE_REMOVED)

                    if (fixedPacket) {
                        player.sendMessage(" §c> Preventing wrong villager metadata. §7[id ${entity.entityId}]")
                        event.isCancelled = true
                    }

                    when {

                        !registered && fixedPacket -> {
                            HumanoidController(entity, UserProfile(entity.uniqueId, "HideMyName")).also { createdProvider ->
                                humanoidRegistry[entity] = createdProvider
                                createdProvider.profile.textureProperties = listOf(entity.race?.skins?.randomOrNull() ?: TextureProperty("textures", "", ""))
                                createdProvider.subscribers.add(player)

                                // Load PHD
                                entity.persistentDataContainer.get(personalDataKey, PersistentDataType.STRING)?.let { data ->
                                    createdProvider.personalData = plugin.gson.fromJson(data, PersonalHumanoidData::class.java)
                                }

                                plugin.logger.info("Added a new villager with ID ${entity.entityId} at ${entity.location} to client entities registry.")
                                player.sendMessage(" §3> Calling HumanoidInitializationEvent for a new villager. §7[id ${entity.entityId}]")
                                plugin.server.scheduler.runTask(plugin) { _ ->
                                    plugin.server.pluginManager.callEvent(HumanoidInitializationEvent(player, entity, createdProvider, metadata))
                                }
                            }
                        }

                        registered && fixedPacket && !subscribed -> {
                            humanoidProvider!!.subscribers.add(player)
                            plugin.server.scheduler.runTask(plugin) { _ ->
                                player.sendMessage(" §3> Calling HumanoidInitializationEvent for an existing villager. §7[id ${entity.entityId}]")
                                plugin.server.pluginManager.callEvent(HumanoidInitializationEvent(player, entity, humanoidProvider, metadata))
                            }
                        }

                        registered && fixedPacket && subscribed -> {
                            player.sendMessage(" §e> Sending fixed villager metadata. §7[id ${entity.entityId}]")
                            player.sendPacket(WrapperPlayServerEntityMetadata(entity.entityId, metadata))
                        }

                    }

                    if (!event.isCancelled) {
                        player.sendMessage(" §6> Received metadata packet! §7[id ${entity.entityId}]")
                    }

                }
            }

            // To prevent the head of a villager disguised as a player from spinning 360 degrees without a body, we need to send the right packet.
            PacketType.Play.Server.ENTITY_HEAD_LOOK -> {

                val player   = event.getPlayer<Player>()
                val world    = player.world
                val packet   = WrapperPlayServerEntityHeadLook(event)
                val entity   = SpigotConversionUtil.getEntityById(world, packet.entityId) ?: return
                val location = entity.location

                if (humanoidRegistry.keys.contains(entity)) {
                    player.sendPacket(WrapperPlayServerEntityRelativeMoveAndRotation(entity.entityId, 0.0, 0.0, 0.0, packet.headYaw, location.pitch, false))
                }

            }

            // It is very important to handle this packet, as low range distance players may “unseen” disguised villagers, although they still remain in memory.
            // It also makes it easy to remove fake players from the registry when they are unloaded from memory.
            PacketType.Play.Server.DESTROY_ENTITIES -> {

                val player = event.getPlayer<Player>()
                val world  = player.world
                val packet = WrapperPlayServerDestroyEntities(event)

                for (entityId in packet.entityIds) {
                    val entity = SpigotConversionUtil.getEntityById(world, entityId) ?: return
                    val humanoidProvider = humanoidRegistry[entity] ?: return

                    if (entity is Villager) {
                        player.sendMessage(" §4> Destroying disguised villager. §7[id $entityId]")
                        humanoidProvider.subscribers.remove(player)
                    }
                }

            }

            else -> { /* ... */ }
        }

    }

    override fun onPacketPlayReceive(event: PacketPlayReceiveEvent) {

        when (event.packetType) {

            // If you don't cancel INTERACT_ENTITY packet with the disguised villager, the player will be kicked due to a protocol error.
            PacketType.Play.Client.INTERACT_ENTITY -> {

                val packet = WrapperPlayClientInteractEntity(event)
                val action = packet.action

                if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK)
                    return

                val player = event.getPlayer<Player>()
                val entity = SpigotConversionUtil.getEntityById(player.world, packet.entityId) ?: return

                if (humanoidRegistry.containsKey(entity)) {
                    event.isCancelled = true
                    plugin.server.scheduler.runTask(plugin) { _ ->
                        plugin.server.pluginManager.callEvent(PlayerInteractEntityEvent(player, entity))
                    }
                }
            }

            else -> { /* ... */ }
        }

    }

    private fun Location.toPacketEventsLocation() = SpigotConversionUtil.fromBukkitLocation(this)

    private fun Player.channel() : Any = PacketEvents.getAPI().playerManager.getChannel(this)

    private fun Player.sendPacket(packet: PacketWrapper<*>) {
        PacketEvents.getAPI().protocolManager.sendPacket(this.channel(), packet)
    }

}