package dev.mizarc.waystonewarps.interaction.menus.management

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.AnvilGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import com.github.stefvanschie.inventoryframework.pane.util.Slot
import dev.mizarc.waystonewarps.application.actions.world.CreateWarp
import dev.mizarc.waystonewarps.application.results.CreateWarpResult
import dev.mizarc.waystonewarps.infrastructure.mappers.toPosition3D
import dev.mizarc.waystonewarps.interaction.localization.LocalizationKeys
import dev.mizarc.waystonewarps.interaction.localization.LocalizationProvider
import dev.mizarc.waystonewarps.interaction.menus.Menu
import dev.mizarc.waystonewarps.interaction.menus.MenuNavigator
import dev.mizarc.waystonewarps.interaction.messaging.PrimaryColourPalette
import dev.mizarc.waystonewarps.interaction.utils.lore
import dev.mizarc.waystonewarps.interaction.utils.name
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WarpNamingMenu(
    private val player: Player,
    private val menuNavigator: MenuNavigator,
    private val location: Location
) : Menu, KoinComponent {
    private val createWarp: CreateWarp by inject()
    private val localizationProvider: LocalizationProvider by inject()
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
                .title("Name your Waystone")
                .input("Enter name here", "")
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
        val title = localizationProvider.get(player.uniqueId, LocalizationKeys.MENU_WARP_NAMING_TITLE)
        val gui = AnvilGui(title)
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
            .name("", PrimaryColourPalette.INFO.color!!)
            .lore(localizationProvider.get(
                player.uniqueId,
                LocalizationKeys.MENU_WARP_NAMING_ITEM_WARP_LORE,
                location.blockX.toString(),
                location.blockY.toString(),
                location.blockZ.toString()
            ))
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
        val belowLocation = location.clone().subtract(0.0, 1.0, 0.0)
        val result = createWarp.execute(
            player.uniqueId,
            inputName,
            location.toPosition3D(),
            location.world.uid,
            location.world.getBlockAt(belowLocation).type.name
        )

        when (result) {
            is CreateWarpResult.Success -> {
                location.world.playSound(
                    player.location,
                    Sound.BLOCK_VAULT_OPEN_SHUTTER,
                    SoundCategory.BLOCKS,
                    1.0f,
                    1.0f
                )
                menuNavigator.openMenu(WarpManagementMenu(player, menuNavigator, result.warp))
            }
            is CreateWarpResult.LimitExceeded -> {
                val msg = localizationProvider.get(player.uniqueId, LocalizationKeys.CONDITION_NAMING_LIMIT, PrimaryColourPalette.FAILED.color!!)
                if (gui != null && secondPane != null) showErrorMessage(gui, secondPane, msg)
                else player.sendMessage("§c$msg")
            }
            is CreateWarpResult.NameAlreadyExists -> {
                val msg = localizationProvider.get(player.uniqueId, LocalizationKeys.CONDITION_NAMING_EXISTING, PrimaryColourPalette.FAILED.color!!)
                if (gui != null && secondPane != null) showErrorMessage(gui, secondPane, msg)
                else {
                    player.sendMessage("§c$msg")
                    openBedrockForm()
                }
            }
            is CreateWarpResult.NameCannotBeBlank -> {
                val msg = localizationProvider.get(player.uniqueId, LocalizationKeys.CONDITION_NAMING_BLANK, PrimaryColourPalette.FAILED.color!!)
                if (gui != null && secondPane != null) showErrorMessage(gui, secondPane, msg)
                else {
                    player.sendMessage("§c$msg")
                    openBedrockForm()
                }
            }
        }
    }

    private fun showErrorMessage(gui: AnvilGui, pane: StaticPane, message: String) {
        val paperItem = ItemStack(Material.PAPER).name(message)
        val guiPaperItem = GuiItem(paperItem) {
            pane.removeItem(0, 0)
            isConfirming = true
            gui.update()
        }
        pane.addItem(guiPaperItem, 0, 0)
        isConfirming = true
        gui.update()
    }
}