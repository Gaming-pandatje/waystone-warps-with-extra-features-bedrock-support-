package dev.mizarc.waystonewarps.domain.coowner

import java.util.UUID

interface CoOwnerRepository {
    fun isCoOwner(warpId: UUID, playerId: UUID): Boolean
    fun getByWarp(warpId: UUID): List<UUID>
    fun getByPlayer(playerId: UUID): List<UUID>
    fun add(coOwner: CoOwner)
    fun remove(warpId: UUID, playerId: UUID)
    fun removeByWarp(warpId: UUID)
}
