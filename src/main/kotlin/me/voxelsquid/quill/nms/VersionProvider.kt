package me.voxelsquid.quill.nms

import me.voxelsquid.quill.QuestIntelligence
import org.bukkit.Sound
import org.bukkit.entity.Villager
import org.bukkit.inventory.ItemStack

class VersionProvider {

    companion object {

        private val plugin = QuestIntelligence.pluginInstance

        private val bridge: AbstractVersionBridge = when {

            // 1.20.6
            plugin.server.version.contains("1.20.6") -> {
                plugin.logger.info("Using version bridge for Minecraft 1.21.1...")
                me.voxelsquid.quill.nms.v1_20_R4.VersionBridge(plugin)
            }

            // 1.21.1
            plugin.server.version.contains("1.21.1") -> {
                plugin.logger.info("Using version bridge for Minecraft 1.21.1...")
                me.voxelsquid.quill.nms.v1_21_R1.VersionBridge(plugin)
            }

            // 1.21.3
            plugin.server.version.contains("1.21.3") -> {
                plugin.logger.info("Using version bridge for Minecraft 1.21.3...")
                me.voxelsquid.quill.nms.v1_21_R2.VersionBridge(plugin)
            }

            else -> {
                plugin.logger.severe("Unsupported server version! (${plugin.server.version})")
                plugin.server.pluginManager.disablePlugin(plugin)
                me.voxelsquid.quill.nms.v1_21_R1.VersionBridge(plugin)
            }
        }

        val trims = bridge.trims
        val ominousBanner = bridge.getOminousBanner()

        fun Villager.consume(item: ItemStack, sound: Sound, duration: Int, period: Long = 20L, onDone: () -> Unit) {
            bridge.consume(this, item, sound, duration, period, onDone)
        }

    }

}