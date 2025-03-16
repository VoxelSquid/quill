package me.voxelsquid.quill.base

import me.voxelsquid.quill.QuestIntelligence.Companion.pluginInstance
import me.voxelsquid.quill.base.command.CommandController
import me.voxelsquid.quill.base.config.ConfigurationAccessor
import me.voxelsquid.quill.base.config.ConfigurationManager
import me.voxelsquid.quill.gameplay.util.GeyserSupportProvider
import me.voxelsquid.quill.gameplay.villager.interaction.DialogueManager.DialogueFormat
import org.bukkit.Bukkit

class PluginRuntimeController {

    val configurationManager = ConfigurationManager()
    var geyserProvider       = if (pluginInstance.server.pluginManager.isPluginEnabled("Geyser-Spigot")) GeyserSupportProvider() else null

    val apiKey        = ConfigurationAccessor(path = "core.api-key", defaultValue = "GEMINI_API_KEY", comments = mutableListOf("Get your API key FOR FREE: https://aistudio.google.com/app/apikey")).get()
    val language      = ConfigurationAccessor(path = "core.language", defaultValue = "LITERARY ENGLISH", comments = mutableListOf("Language of plugin messages, quests, villager names and phrases. (supported languages depends on AI)")).get()
    val translation   = ConfigurationAccessor(path = "core.generative-translation", defaultValue = true, comments = mutableListOf("The plugin messages will be automatically translated into the language specified above when plugin enables. But you can disable this and configure everything yourself.")).get()
    val allowedWorlds = ConfigurationAccessor(path = "core.allowed-worlds", defaultValue = listOf("world"), comments = mutableListOf("Specify the names of the worlds where you want QuestIntelligence to work.")).get()
    val humanoids     = ConfigurationAccessor(path = "core.humanoid-villagers", defaultValue = true, comments = mutableListOf("By default, all villagers are replaced by player models that have skins and races with their own unique attributes.", "You can disable this feature or customize it in races.yml and skins.yml.")).get()
    val namingStyle   = ConfigurationAccessor(path = "core.naming-style", defaultValue = "Dark Fantasy", comments = mutableListOf("The naming style affects the style in which the AI will generate stuff.", "Dark Fantasy is set by default, but you can specify really anything here, from space dwarfs to the Warhammer, or even Japanese anime names. すげえ！")).get()
    val swearing      = ConfigurationAccessor(path = "core.swearing", defaultValue = true, comments = mutableListOf("Some personality types will swear in their phrases. You may not like this, so I've added the option to disable it.")).get()
    val format        = ConfigurationAccessor(path = "core.default-dialogue-format", defaultValue = DialogueFormat.IMMERSIVE, comments = mutableListOf("The standard format of villagers' dialogs for every player on the server.", "IMMERSIVE: the message from the villager will be received in a cOoL dIaLoGuE wInDoW.", "CHAT: the message from the villager will be received via chat, classic.", "BOTH: well... there's no need to explain, I hope?..")).get()
    val messagePrefix = ConfigurationAccessor(path = "text-formatting.chat.message-prefix", defaultValue = "&7&l.q&8&l/ &4>&c> &7", comments = mutableListOf("The prefix of messages sent by the plugin.")).get()

    init {
        this.checkBeforeStartup()
    }

    private fun checkBeforeStartup() {

        val pluginManager = pluginInstance.server.pluginManager

        // Check for Gemini API key.
        if (apiKey == "GEMINI_API_KEY") {
            pluginInstance.logger.severe("The plugin must be configured before it can be used. You need to replace the value of ‘core.api-key’ with a real Gemini API key.")
            pluginInstance.logger.severe("QuestIntelligence will be disabled.")
            Bukkit.getServer().pluginManager.disablePlugin(pluginInstance)
        }

        // Check for incompatible plugins.
        val incompatiblePlugins = listOf("RealisticVillagers")
        incompatiblePlugins.forEach { name ->
            if (pluginManager.isPluginEnabled(name)) {
                pluginInstance.logger.severe("QuestIntelligence detected incompatible plugin: $name.")
                pluginInstance.logger.severe("QuestIntelligence will be disabled.")
                pluginManager.disablePlugin(pluginInstance)
                return
            }
        }

    }

    fun isOk() : Boolean = pluginInstance.isEnabled
    fun setupCommands() = CommandController()

}