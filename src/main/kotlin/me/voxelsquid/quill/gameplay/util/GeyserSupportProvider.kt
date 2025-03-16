package me.voxelsquid.quill.gameplay.util

import me.voxelsquid.quill.QuestIntelligence.Companion.pluginInstance
import me.voxelsquid.quill.base.config.ConfigurationAccessor
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.getPersonalHumanoidData
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.quests
import me.voxelsquid.quill.gameplay.humanoid.HumanoidManager.HumanoidEntityExtension.settlement
import me.voxelsquid.quill.gameplay.humanoid.HumanoidTradeHandler.Companion.openTradeMenu
import me.voxelsquid.quill.gameplay.settlement.ReputationManager.Companion.Reputation
import me.voxelsquid.quill.gameplay.settlement.ReputationManager.Companion.getPlayerReputationStatus
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.geyser.api.GeyserApi

class GeyserSupportProvider {

    companion object {
        val plugin = pluginInstance
        val dialogueBoxTextBaseColor        = ConfigurationAccessor("text-formatting.dialogue-box.text.base-color", "&f", mutableListOf("Standard color of common words in dialogue boxes.")).get()
        val dialogueBoxTextImportantColor   = ConfigurationAccessor("text-formatting.dialogue-box.text.important-color", "&5", mutableListOf("Color of important words in dialogue boxes that the AI will try to pay attention to.")).get()
        val dialogueBoxTextInterestingColor = ConfigurationAccessor("text-formatting.dialogue-box.text.interesting-color", "&6", mutableListOf("Color of interesting words in dialogue boxes that may be interesting to the player.")).get()
    }

    init {
        plugin.logger.info("Geyser usage detected. Support for Bedrock Edition players will be provided.")
    }

    fun checkGeyserPlayer(player: Player) : Boolean = try {
        GeyserApi.api().connectionByUuid(player.uniqueId) != null
    } catch (exception: Exception) {
        false
    }

    // QI automatically detects if the player is playing through Geyser, and if true, selects a menu from Form. Suddenly, Bedrock Edition has one cool feature — the ability to create your own GUI.
    fun openInteractionMenu(player: Player, villager: Villager) {

        val questList = SimpleForm.builder()
            .title(plugin.configManager.language.getString("interaction-menu.quests-button")!!)

        villager.quests.forEach { quest ->
            questList.button(quest.questInfo.questName)
        }

        questList.button(plugin.configManager.language.getString("interaction-menu.close-button")!!)
        questList.validResultHandler { response ->

            val buttonName = response.clickedButton().text()
            if (buttonName == plugin.configManager.language.getString("interaction-menu.close-button")!!) return@validResultHandler

            // Looking for a quest description.
            val questDescription = villager.quests.find { it.questInfo.questName == response.clickedButton().text() }?.let { quest ->
                (villager.settlement?.let { settlement ->
                    when (player.getPlayerReputationStatus(settlement)) {
                        Reputation.EXALTED -> quest.questInfo.reputationBasedQuestDescriptions[7]
                        Reputation.REVERED -> quest.questInfo.reputationBasedQuestDescriptions[6]
                        Reputation.HONORED -> quest.questInfo.reputationBasedQuestDescriptions[5]
                        Reputation.FRIENDLY -> quest.questInfo.reputationBasedQuestDescriptions[4]
                        Reputation.NEUTRAL -> quest.questInfo.reputationBasedQuestDescriptions[3]
                        Reputation.UNFRIENDLY -> quest.questInfo.reputationBasedQuestDescriptions[2]
                        Reputation.HOSTILE -> quest.questInfo.reputationBasedQuestDescriptions[1]
                        Reputation.EXILED -> quest.questInfo.reputationBasedQuestDescriptions[0]
                    }
                } ?: quest.questInfo.reputationBasedQuestDescriptions[3]).replace("%playerName%", player.name)
            } ?: "ERROR! NO QUEST DESCRIPTION."

            // Markdown parsing.
            val formattedQuestDescription = dialogueBoxTextBaseColor + questDescription.replace(Regex("\\*\\*(.*?)\\*\\*")) { matchResult ->
                "${dialogueBoxTextImportantColor}${matchResult.groupValues[1]}${dialogueBoxTextBaseColor}"
            }.replace(Regex("\\*(.*?)\\*")) { matchResult ->
                "${dialogueBoxTextInterestingColor}${matchResult.groupValues[1]}${dialogueBoxTextBaseColor}"
            }.replace("\\\"", "\"")

            // Menu with quest description.
            val questDescriptionMenu = ModalForm.builder()
                .title(response.clickedButton().text())
                .content(formattedQuestDescription)
                .button1(plugin.configManager.language.getString("interaction-menu.trade-button")!!)
                .button2(plugin.configManager.language.getString("interaction-menu.close-button")!!)
                .validResultHandler { responseData ->
                    if (responseData.clickedButtonText() == plugin.configManager.language.getString("interaction-menu.trade-button")!!) {
                        plugin.server.scheduler.runTask(plugin) { _ ->
                            villager.openTradeMenu(player)
                        }
                    }
                }

            GeyserApi.api().sendForm(player.uniqueId, questDescriptionMenu.build())
        }

        val interactionMenu = SimpleForm.builder()
            .title(villager.getPersonalHumanoidData()?.villagerName ?: "Villager")
            .button(plugin.configManager.language.getString("interaction-menu.quests-button")!!)
            .button(plugin.configManager.language.getString("interaction-menu.trade-button")!!)
            .button(plugin.configManager.language.getString("interaction-menu.actions-button")!!)
            .button(plugin.configManager.language.getString("interaction-menu.close-button")!!)
            .validResultHandler { responseData ->
                if (responseData.clickedButton().text() == plugin.configManager.language.getString("interaction-menu.quests-button")!!) {
                    GeyserApi.api().sendForm(player.uniqueId, questList.build())
                }
                if (responseData.clickedButton().text() == plugin.configManager.language.getString("interaction-menu.trade-button")!!) {
                    plugin.server.scheduler.runTask(plugin) { _ ->
                        villager.openTradeMenu(player)
                    }
                }
            }

        GeyserApi.api().sendForm(player.uniqueId, interactionMenu.build())

    }

}