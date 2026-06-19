package dev.mizarc.waystonewarps.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Mask
import com.github.stefvanschie.inventoryframework.pane.util.Slot
import dev.mizarc.waystonewarps.application.actions.coowner.GetCoOwners
import dev.mizarc.waystonewarps.application.actions.coowner.ToggleCoOwner
import dev.mizarc.waystonewarps.domain.warps.Warp
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import dev.mizarc.waystonewarps.interaction.utils.createHead
import dev.mizarc.waystonewarps.interaction.utils.lore
import dev.mizarc.waystonewarps.interaction.utils.name
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WarpCoOwnerMenu(
    private val player: Player,
    private val menuNavigator: MenuNavigator,
    private val warp: Warp,
    private val localizationProvider: LocalizationProvider
) : Menu, KoinComponent {
    private val getCoOwners: GetCoOwners by inject()
    private val toggleCoOwner: ToggleCoOwner by inject()
    private val plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("WaystoneWarps")!!

    private var playerNameSearch: String = ""
    private var page = 1

    override fun open() {
        val gui = ChestGui(6, "Co-Owners: ${warp.name}")
        gui.setOnTopClick { guiEvent -> guiEvent.isCancelled = true }
        gui.setOnBottomClick { guiEvent ->
            if (guiEvent.click == ClickType.SHIFT_LEFT ||
                guiEvent.click == ClickType.SHIFT_RIGHT) guiEvent.isCancelled = true
        }

        // Outline
        val outlinePane = OutlinePane(9, 5)
        val dividerItem = ItemStack(Material.BLACK_STAINED_GLASS_PANE).name(" ")
        outlinePane.applyMask(Mask(
            "111111111",
            "100000001",
            "100000001",
            "100000001",
            "111111111"
        ))
        outlinePane.addItem(GuiItem(dividerItem) { it.isCancelled = true })
        outlinePane.setRepeat(true)
        gui.addPane(Slot.fromXY(0, 1), outlinePane)

        // Controls
        val controlsPane = StaticPane(9, 1)
        gui.addPane(Slot.fromXY(0, 0), controlsPane)

        // Back button
        val backItem = ItemStack(Material.NETHER_STAR)
            .name(localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_COMMON_ITEM_BACK_NAME), PrimaryColourPalette.CANCELLED.color!!)
        controlsPane.addItem(GuiItem(backItem) { menuNavigator.goBack() }, 0, 0)

        // Add co-owner button
        val addItem = ItemStack(Material.NAME_TAG).name("§aAdd Co-Owner")
            .lore("Search for a player to add as co-owner")
        controlsPane.addItem(GuiItem(addItem) {
            openSearchInput()
        }, 3, 0)

        // Display current co-owners
        val coOwnerIds = getCoOwners.execute(warp.id)
        val coOwners = coOwnerIds.map { Bukkit.getOfflinePlayer(it) }.sortedBy { it.name }

        val playerPane = PaginatedPane(7, 3)
        var currentPagePane = OutlinePane(7, 3)
        var counter = 0

        for (coOwner in coOwners) {
            val playerItem = createHead(coOwner)
                .name("§e${coOwner.name ?: "Unknown"}")
                .lore("§cClick to remove as co-owner")

            currentPagePane.addItem(GuiItem(playerItem) {
                toggleCoOwner.execute(
                    editorPlayerId = player.uniqueId,
                    warpId = warp.id,
                    targetPlayerId = coOwner.uniqueId,
                    bypassOwnership = player.hasPermission("waystonewarps.bypass.manage_players")
                )
                open()
            })
            counter++
            if (counter >= 21) {
                playerPane.addPage(Slot.fromXY(0, 0), currentPagePane)
                currentPagePane = OutlinePane(7, 3)
                counter = 0
            }
        }
        if (counter > 0) playerPane.addPage(Slot.fromXY(0, 0), currentPagePane)
        if (coOwners.isEmpty()) playerPane.addPage(Slot.fromXY(0, 0), OutlinePane(7, 3))
        gui.addPane(Slot.fromXY(1, 2), playerPane)

        gui.show(player)
    }

    override fun passData(data: Any?) {
        if (data is String) {
            val targetPlayer = Bukkit.getOfflinePlayerIfCached(data)
                ?: Bukkit.getOfflinePlayer(data)
            val result = toggleCoOwner.execute(
                editorPlayerId = player.uniqueId,
                warpId = warp.id,
                targetPlayerId = targetPlayer.uniqueId,
                bypassOwnership = player.hasPermission("waystonewarps.bypass.manage_players")
            )
            result.onSuccess { added ->
                if (added) {
                    player.sendMessage("§a${targetPlayer.name} has been added as a co-owner.")
                } else {
                    player.sendMessage("§c${targetPlayer.name} has been removed as a co-owner.")
                }
            }
            result.onFailure { error ->
                player.sendMessage("§c${error.message}")
            }
        }
        open()
    }

    private fun openSearchInput() {
        val isBedrockPlayer = try {
            org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgatePlayer(player.uniqueId)
        } catch (e: Exception) { false }

        if (isBedrockPlayer) {
            try {
                val floodgateApi = org.geysermc.floodgate.api.FloodgateApi.getInstance()
                val formBuilder = org.geysermc.cumulus.form.CustomForm.builder()
                    .title("Add Co-Owner")
                    .input("Player Name", "Enter player name here")
                    .validResultHandler { response ->
                        val input = response.asInput(0)?.trim() ?: return@validResultHandler
                        if (input.isBlank()) return@validResultHandler
                        plugin.server.scheduler.runTask(plugin, Runnable {
                            passData(input)
                        })
                    }
                    .build()
                floodgateApi.sendForm(player.uniqueId, formBuilder)
            } catch (e: Exception) {
                openChatInput()
            }
        } else {
            menuNavigator.openMenu(PlayerSearchMenu(player, menuNavigator))
        }
    }

    private fun openChatInput() {
        player.closeInventory()
        player.sendMessage("§6Type the name of the player to add as co-owner (or type §ccancel§6 to abort)")
        val listener = object : org.bukkit.event.Listener {
            @org.bukkit.event.EventHandler
            fun onChat(event: org.bukkit.event.player.AsyncPlayerChatEvent) {
                if (event.player.uniqueId != player.uniqueId) return
                event.isCancelled = true
                org.bukkit.event.HandlerList.unregisterAll(this)
                val input = event.message.trim()
                if (input.equals("cancel", ignoreCase = true)) {
                    plugin.server.scheduler.runTask(plugin, Runnable { open() })
                    return
                }
                plugin.server.scheduler.runTask(plugin, Runnable {
                    passData(input)
                })
            }
        }
        plugin.server.pluginManager.registerEvents(listener, plugin)
    }
}
