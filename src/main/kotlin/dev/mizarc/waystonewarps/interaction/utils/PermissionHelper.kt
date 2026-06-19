package dev.mizarc.waystonewarps.interaction.utils

import org.bukkit.entity.Player
import java.util.UUID

object PermissionHelper {

    fun canModifyWaystone(player: Player, waystoneOwnerId: UUID, adminPermission: String,
                          coOwnerIds: List<UUID> = emptyList()): Boolean {
        if (player.uniqueId == waystoneOwnerId) return true
        if (coOwnerIds.contains(player.uniqueId)) return true
        return player.hasPermission(adminPermission)
    }

    fun canChangeAccessControl(player: Player, waystoneOwnerId: UUID,
                               coOwnerIds: List<UUID> = emptyList()): Boolean {
        return canModifyWaystone(player, waystoneOwnerId, "waystonewarps.bypass.access_control", coOwnerIds)
    }

    fun canManageWhitelist(player: Player, waystoneOwnerId: UUID,
                           coOwnerIds: List<UUID> = emptyList()): Boolean {
        return canModifyWaystone(player, waystoneOwnerId, "waystonewarps.bypass.manage_players", coOwnerIds)
    }

    fun canRename(player: Player, waystoneOwnerId: UUID,
                  coOwnerIds: List<UUID> = emptyList()): Boolean {
        return canModifyWaystone(player, waystoneOwnerId, "waystonewarps.bypass.rename", coOwnerIds)
    }

    fun canChangeIcon(player: Player, waystoneOwnerId: UUID,
                      coOwnerIds: List<UUID> = emptyList()): Boolean {
        return canModifyWaystone(player, waystoneOwnerId, "waystonewarps.bypass.icon", coOwnerIds)
    }

    fun canRelocate(player: Player, waystoneOwnerId: UUID,
                    coOwnerIds: List<UUID> = emptyList()): Boolean {
        return canModifyWaystone(player, waystoneOwnerId, "waystonewarps.bypass.relocate", coOwnerIds)
    }

    fun canManageCoOwners(player: Player, waystoneOwnerId: UUID): Boolean {
        if (player.uniqueId == waystoneOwnerId) return true
        return player.hasPermission("waystonewarps.bypass.manage_players")
    }
}
