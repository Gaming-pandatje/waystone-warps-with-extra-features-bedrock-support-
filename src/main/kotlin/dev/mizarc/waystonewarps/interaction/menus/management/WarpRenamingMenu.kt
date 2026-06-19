package dev.mizarc.waystonewarps.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Slot
import dev.mizarc.waystonewarps.application.actions.management.UpdateWarpName
import dev.mizarc.waystonewarps.application.actions.coowner.GetCoOwners
import dev.mizarc.waystonewarps.application.results.UpdateWarpNameResult
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import dev.mizarc.waystonewarps.interaction.utils.PermissionHelper
import dev.mizarc.waystonewarps.interaction.utils.lore
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

class WarpRenamingMenu(
    private val player: Player,
    private val menuNavigator: MenuNavigator,
    private val warp: Warp,
    private val localizationProvider: LocalizationProvider
) : Menu, KoinComponent {
    private val updateWarpName: UpdateWarpName by inject()
    private val getCoOwners: GetCoOwners by inject()
    private val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("WaystoneWarps")!!

    private var name = ""
    private var isConfirming = false

    private fun isBedrockPlayer(): Boolean {
        return try {
            org.geysermc.floodgate.api.FloodgateApi.getInstance()
                .isFloodgatePlayer(player.uniqueId)
        } catch (e: Exception) {
            false
        }
    }

    override fun open() {
        val canRename = PermissionHelper.canRename(player, warp.playerId, getCoOwners.execute(warp.id))
        if (!canRename) {
            player.sendMessage("§c${localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_MANAGEMENT_COMMON_NO_PERMISSION)}")
            menuNavigator.goBack()
            return
        }

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
                .title("Rename Waystone")
                .input("Enter new name here", "")
                .validResultHandler { response ->
                    val input = response.asInput(0)?.trim() ?: return@validResultHandler
                    plugin.server.scheduler.runTask(plugin, Runnable { submitName(input, null, null) })
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
                    plugin.server.scheduler.runTask(plugin, Runnable { submitName(input, null, null) })
                }
            }
            plugin.server.pluginManager.registerEvents(listener, plugin)
        }
    }

    private fun openAnvilGui() {
        val gui = AnvilGui(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_RENAMING_TITLE))
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT ||
                guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true
        }
        gui.setOnNameInputChanged { newName ->
            if (!isConfirming) {
                name = newName
            } else {
                isConfirming = false
            }
        }

        val firstPane = StaticPane(1, 1)
        val lodestoneItem = ItemStack(Material.LODESTONE)
            .name(warp.name)
            .lore("${warp.position.x}, ${warp.position.y}, ${warp.position.z}")
        val guiItem = GuiItem(lodestoneItem) { guiEvent -> guiEvent.isCancelled = true }
        firstPane.addItem(guiItem, 0, 0)
        gui.firstItemComponent.addPane(Slot.fromXY(0, 0), firstPane)

        val secondPane = StaticPane(1, 1)
        gui.secondItemComponent.addPane(Slot.fromXY(0, 0), secondPane)

        val thirdPane = StaticPane(1, 1)
        val confirmItem = ItemStack(Material.NETHER_STAR)
            .name(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_COMMON_ITEM_CONFIRM_NAME), PrimaryColourPalette.SUCCESS.color!!)
        val confirmGuiItem = GuiItem(confirmItem) { _ ->
            submitName(name, gui, secondPane)
        }

        thirdPane.addItem(confirmGuiItem, 0, 0)
        gui.resultComponent.addPane(Slot.fromXY(0, 0), thirdPane)
        gui.show(player)
    }

    private fun submitName(inputName: String, gui: AnvilGui?, secondPane: StaticPane?) {
        if (inputName == warp.name) {
            menuNavigator.goBack()
            return
        }

        val result = updateWarpName.execute(
            warpId = warp.id,
            editorPlayerId = player.uniqueId,
            name = inputName,
            bypassOwnership = player.hasPermission("waystonewarps.bypass.rename")
                    || getCoOwners.execute(warp.id).contains(player.uniqueId),
        )

        when (result) {
            UpdateWarpNameResult.SUCCESS -> menuNavigator.goBack()
            UpdateWarpNameResult.WARP_NOT_FOUND -> {
                val msg = localizationProvider.get(player.uniqueId, LocalizationKeys.CONDITION_NAMING_NOT_FOUND)
                if (gui != null && secondPane != null) {
                    val paperItem = ItemStack(Material.PAPER).name(msg, PrimaryColourPalette.FAILED.color!!)
                    secondPane.addItem(GuiItem(paperItem), 0, 0)
                    isConfirming = true
                    gui.update()
                } else player.sendMessage("§c$msg")
            }
            UpdateWarpNameResult.NAME_ALREADY_TAKEN -> {
                val msg = localizationProvider.get(player.uniqueId, LocalizationKeys.CONDITION_NAMING_EXISTING, inputName)
                if (gui != null && secondPane != null) {
                    val paperItem = ItemStack(Material.PAPER).name(msg, PrimaryColourPalette.FAILED.color!!)
                    val guiPaperItem = GuiItem(paperItem) { _ ->
                        secondPane.removeItem(0, 0)
                        isConfirming = true
                        gui.update()
                    }
                    secondPane.addItem(guiPaperItem, 0, 0)
                    isConfirming = true
                    gui.update()
                } else {
                    player.sendMessage("§c$msg")
                    openBedrockForm()
                }
            }
            UpdateWarpNameResult.NAME_BLANK -> {
                if (gui == null) {
                    player.sendMessage("§cName cannot be blank.")
                    openBedrockForm()
                } else menuNavigator.goBack()
            }
            UpdateWarpNameResult.NOT_AUTHORIZED -> {
                player.sendMessage("§c${localizationProvider.get(player.uniqueId, LocalizationKeys.CONDITION_NAMING_NO_PERMISSION)}")
                menuNavigator.goBack()
            }
        }
    }
}