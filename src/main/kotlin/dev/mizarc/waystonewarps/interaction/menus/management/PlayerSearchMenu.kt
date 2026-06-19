package dev.mizarc.waystonewarps.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Slot
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import dev.mizarc.waystonewarps.interaction.utils.name
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class PlayerSearchMenu(
    private val player: Player,
    private val menuNavigator: MenuNavigator
): Menu, KoinComponent {
    private val localizationProvider: LocalizationProvider by inject()
    private val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("WaystoneWarps")!!

    private fun isBedrockPlayer(): Boolean {
        return try {
            org.geysermc.floodgate.api.FloodgateApi.getInstance()
                .isFloodgatePlayer(player.uniqueId)
        } catch (e: Exception) {
            false
        }
    }

    override fun open() {
        if (isBedrockPlayer()) {
            openBedrockForm()
        } else {
            openAnvilGui()
        }
    }

    private fun openBedrockForm() {
        try {
            val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
            val form = org.geysermc.cumulus.form.CustomForm.builder()
                .title("Search Player")
                .input("Enter player name here", "")
                .validResultHandler { response ->
                    val input = response.asInput(0)?.trim() ?: return@validResultHandler
                    plugin.server.scheduler.runTask(plugin, Runnable { menuNavigator.goBackWithData(input) })
                }
                .build()
            floodgateApi.sendForm(player.uniqueId, form)
        } catch (e: Exception) {
            player.closeInventory()
            player.sendMessage("§6Type in chat (or type §ccancel§6 to abort)")
            val listener = object : Listener {
                @EventHandler
                fun onChat(event: AsyncPlayerChatEvent) {
                    if (event.player.uniqueId != player.uniqueId) return
                    event.isCancelled = true
                    HandlerList.unregisterAll(this)
                    val input = event.message.trim()
                    if (input.equals("cancel", ignoreCase = true)) {
                        plugin.server.scheduler.runTask(plugin, Runnable { menuNavigator.goBack() })
                        return
                    }
                    plugin.server.scheduler.runTask(plugin, Runnable { menuNavigator.goBackWithData(input) })
                }
            }
            plugin.server.pluginManager.registerEvents(listener, plugin)
        }
    }

    private fun openAnvilGui() {
        val title = localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_PLAYER_SEARCH_TITLE)
        val gui = com.github.stefvanschie.inventoryframework.gui.type.AnvilGui(title)
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent -> if (guiEvent.click == ClickType.SHIFT_LEFT ||
            guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true }

        val firstPane = com.github.stefvanschie.inventoryframework.pane.StaticPane(1, 1)
        val headItem = ItemStack(Material.PLAYER_HEAD).name("")
        val guiHeadItem = com.github.stefvanschie.inventoryframework.gui.GuiItem(headItem) { guiEvent -> guiEvent.isCancelled = true }
        firstPane.addItem(guiHeadItem, 0, 0)
        gui.firstItemComponent.addPane(com.github.stefvanschie.inventoryframework.pane.util.Slot.fromXY(0, 0), firstPane)

        val thirdPane = com.github.stefvanschie.inventoryframework.pane.StaticPane(1, 1)
        val confirmItem = ItemStack(Material.NETHER_STAR).name(
            localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_COMMON_ITEM_CONFIRM_NAME),
            PrimaryColourPalette.SUCCESS.color!!
        )
        val confirmGuiItem = com.github.stefvanschie.inventoryframework.gui.GuiItem(confirmItem) { _ ->
            menuNavigator.goBackWithData(gui.renameText)
        }
        thirdPane.addItem(confirmGuiItem, 0, 0)
        gui.resultComponent.addPane(com.github.stefvanschie.inventoryframework.pane.util.Slot.fromXY(0, 0), thirdPane)

        gui.show(player)
    }
}