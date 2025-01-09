package me.voxelsquid.quill.illager

import me.voxelsquid.quill.QuestIntelligence
import me.voxelsquid.quill.event.IllagerCommonData
import me.voxelsquid.quill.event.IllagerCommonDataGenerateEvent
import me.voxelsquid.quill.illager.IllagerManager.IllagerPartyGoal.Companion.parties
import me.voxelsquid.quill.nms.VersionProvider.Companion.ominousBanner
import me.voxelsquid.quill.util.BannerHelmetHandler
import me.voxelsquid.quill.villager.ReputationManager.Companion.fame
import me.voxelsquid.quill.villager.VillagerManager.Companion.talk
import net.minecraft.world.entity.ai.goal.Goal
import org.bukkit.GameMode
import org.bukkit.craftbukkit.entity.CraftIllager
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.world.ChunkLoadEvent

@Suppress("DEPRECATION")
class IllagerManager : Listener {

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.server.pluginManager.registerEvents(BannerHelmetHandler(), plugin)
    }

    @EventHandler
    private fun onIllagerCommonDataGenerate(event: IllagerCommonDataGenerateEvent) {
        illagerCommonData = event.data
    }

    @EventHandler
    private fun onInfamousPlayerDamageIllager(event: EntityDamageByEntityEvent) {
        (event.damageSource.causingEntity as? Player)?.let { damager ->
            (event.entity as? Illager)?.let { illager ->
                if (damager.fame <= -40) {
                    illager.target = damager
                    illager.talk(damager, illagerCommonData?.illagerHurtPhrases?.random(), followDuringDialogue = false, interruptPreviousDialogue = true)
                }
            }
        }
    }

    @EventHandler
    private fun onIllagerSpawn(event: EntitySpawnEvent) {
        when (val entity = event.entity) {
            is Illager -> { // TODO maybe i should add witches?..
                event.entity.customName = illagerCommonData?.illagerNameList?.random()
                (event.entity as CraftIllager).handle.goalSelector.addGoal(1, IllagerPartyGoal(entity))
            }
        }
    }

    @EventHandler
    private fun onIllagerLoad(event: ChunkLoadEvent) {
        event.chunk.entities.filterIsInstance<Illager>().forEach { illager ->
            (illager as CraftIllager).handle.goalSelector.addGoal(1, IllagerPartyGoal(illager))
        }
    }

    @EventHandler
    private fun rememberPartyLeaderLastAttackedTarget(event: EntityDamageByEntityEvent) {
        if (event.damageSource.causingEntity is Player && parties.containsKey(event.damageSource.causingEntity) && event.entity !is Raider) {
            parties[event.damager]?.lastDamagedEntityByPartyLeader = event.entity as LivingEntity
        }
    }

    @EventHandler
    private fun handleIllagerDeath(event: EntityDeathEvent) {
        (event.entity as? Illager)?.let { illager ->
            parties.values.find { it.illagerMembers.contains(illager) }?.let {
                it.illagerMembers.remove(illager)

                // Send message about illager death
                plugin.language?.let { language ->
                    language.getString("illager-party.illager-died")?.let { message ->
                        it.partyLeader.sendMessage(QuestIntelligence.messagePrefix + message.replace("{illagerName}", illager.customName ?: ""))
                    }
                }

            }
        }
    }


    @EventHandler
    private fun handleIllagerTargeting(event: EntityTargetLivingEntityEvent) {
        (event.entity as? Raider)?.let { raider ->
            (event.target as? Player)?.let { targetPlayer: Player ->
                if (targetPlayer.fame <= -40) {
                    event.isCancelled = true

                    val nearbyEntities = raider.getNearbyEntities(20.0, 10.0, 20.0)

                    // Перенаправляем таргетинг из-за ошибки приоритетов, сперва на железных големов
                    nearbyEntities.filterIsInstance<IronGolem>().firstOrNull()?.let { ironGolem ->
                        raider.target = ironGolem
                        return
                    }

                    // Потом на жителей
                    nearbyEntities.filterIsInstance<Villager>().firstOrNull()?.let { villager ->
                        raider.target = villager
                        return
                    }

                    // Потом на существ, которые хотят убить игрока. Илладжеры будут защищать любых игроков, у которых плохая репутация, а не только тех, у кого баннер на голове.
                    nearbyEntities.filter { it !is Illager && it != targetPlayer }.filterIsInstance<Monster>().firstOrNull { it.target == targetPlayer || it.target is Raider }?.let { raider.target = it }

                }
            }
        }
    }

    @EventHandler
    private fun handleIllagerInteraction(event: PlayerInteractAtEntityEvent) {
        event.player.let { player ->
            (event.rightClicked as? Illager)?.let { illager ->
                if (player.fame <= -40) {
                    if (illager.target == null) {
                        if (player.inventory.helmet?.isSimilar(ominousBanner) == true) {
                            illager.talk(player, illagerCommonData?.partyLeaderInteraction?.random(), interruptPreviousDialogue = false)
                        } else illager.talk(player, illagerCommonData?.illagerInteractionPhrases?.random(), interruptPreviousDialogue = false)
                    }
                }
            }
        }
    }

    private class IllagerParty(val partyLeader: Player) {

        var lastDamagedEntityByPartyLeader : LivingEntity? = null
        val desiredDistanceFromLeader = 5.0
        var illagerMembers = mutableListOf<Illager>()

    }

    private class IllagerPartyGoal(private val illager: Illager) : Goal() {

        companion object {
            val parties = mutableMapOf<Player, IllagerParty>()
        }

        private var party : IllagerParty? = null
        private val mob = (illager as CraftIllager).handle

        override fun canUse(): Boolean {

            if (party != null) return true

            // If party is null, look for infamous players with ominous banner as helmet
            illager.getNearbyEntities(7.5, 7.5, 7.5)
                .filterIsInstance<Player>()
                .firstOrNull { it.fame <= -40 && it.gameMode == GameMode.SURVIVAL && it.inventory.helmet?.isSimilar(ominousBanner) ?: false }
                ?.let { leader ->

                    // Send message about new party member
                    plugin.language?.let { language ->
                        language.getString("illager-party.illager-decides-to-join")?.let { message ->
                            leader.sendMessage(QuestIntelligence.messagePrefix + message.replace("{illagerName}", illager.customName ?: ""))
                        }
                    }

                    parties[leader]?.let { existingParty ->
                        this.party = existingParty
                    } ?: run {
                        val party = IllagerParty(leader)
                        parties[leader] = party
                        this.party = party
                    }

                    parties[leader]?.let { it.illagerMembers += illager }
                    return true
                }


            return false
        }

        override fun canContinueToUse(): Boolean {

            party?.let { party ->
                val leader = party.partyLeader
                if (leader.fame <= -40 && leader.gameMode == GameMode.SURVIVAL && leader.inventory.helmet?.isSimilar(ominousBanner) == true) {
                    return true
                } else {
                    if (parties.containsKey(leader)) {
                        parties.remove(leader)

                        plugin.language?.let { language ->
                            language.getString("illager-party.party-disbanded")?.let { message ->
                                leader.sendMessage(QuestIntelligence.messagePrefix + message)
                            }
                        }

                    }
                    this.party = null
                }
            }

            return false
        }

        override fun tick() {

            party?.let { party ->

                val leader = party.partyLeader

                // Сбрасываем цель, если она мертва
                if (party.lastDamagedEntityByPartyLeader?.isDead == true) {
                    party.lastDamagedEntityByPartyLeader = null
                    illager.target = null
                }

                // Переключение цели
                if (illager.target != party.lastDamagedEntityByPartyLeader) {
                    party.lastDamagedEntityByPartyLeader?.let {
                        illager.target = it
                        return
                    }
                }

                // Если нет цели для атаки
                if (illager.target == null) {

                    val leaderLocation = (leader as CraftPlayer).handle.position()
                    val illagerLocation = mob.position()

                    // Следование за лидером в случае отсутсвия целей для атаки
                    val directionToLeader = leaderLocation.subtract(illagerLocation).normalize()
                    val desiredPosition = leaderLocation.subtract(directionToLeader.scale(party.desiredDistanceFromLeader))
                    mob.navigation.moveTo(desiredPosition.x, desiredPosition.y, desiredPosition.z, 1.0)
                }
            }

        }
    }

    companion object {

        private val plugin = QuestIntelligence.pluginInstance
        var illagerCommonData : IllagerCommonData? = null

    }

}