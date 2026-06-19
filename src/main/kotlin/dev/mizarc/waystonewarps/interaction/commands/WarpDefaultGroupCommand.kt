package dev.mizarc.waystonewarps.interaction.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Name
import co.aikar.commands.annotation.Optional
import dev.mizarc.waystonewarps.application.actions.groups.GetAllWarpGroups
import dev.mizarc.waystonewarps.application.services.ConfigService
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@CommandAlias("warpdefaultgroup")
@CommandPermission("waystonewarps.admin.manage_groups")
class WarpDefaultGroupCommand : BaseCommand(), KoinComponent {
    private val configService: ConfigService by inject()
    private val getAllWarpGroups: GetAllWarpGroups by inject()
    private val plugin: Plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("WaystoneWarps")!!

    @Default
    fun onSetDefaultGroup(sender: CommandSender, @Optional @Name("group") groupName: String?) {
        // Clear default group
        if (groupName == null || groupName.equals("none", ignoreCase = true)) {
            configService.setDefaultGroupId(null)
            plugin.saveConfig()
            sender.sendMessage("§aDefault group cleared. New waystones will not be auto-assigned to a group.")
            return
        }

        // Find group by name
        val group = getAllWarpGroups.execute().firstOrNull {
            it.name.equals(groupName, ignoreCase = true)
        }

        if (group == null) {
            sender.sendMessage("§cGroup '§e$groupName§c' not found. Use §e/warpgroups§c to see available groups.")
            return
        }

        configService.setDefaultGroupId(group.id.toString())
        plugin.saveConfig()
        sender.sendMessage("§aDefault group set to §e${group.name}§a. All new waystones will be auto-assigned to this group.")
    }
}
