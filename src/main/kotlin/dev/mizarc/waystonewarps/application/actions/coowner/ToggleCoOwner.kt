package dev.mizarc.waystonewarps.application.actions.coowner

import dev.mizarc.waystonewarps.domain.coowner.CoOwner
import dev.mizarc.waystonewarps.domain.coowner.CoOwnerRepository
import dev.mizarc.waystonewarps.domain.warps.WarpRepository
import java.util.UUID

class ToggleCoOwner(
    private val coOwnerRepository: CoOwnerRepository,
    private val warpRepository: WarpRepository
) {
    fun execute(
        editorPlayerId: UUID,
        warpId: UUID,
        targetPlayerId: UUID,
        bypassOwnership: Boolean = false
    ): Result<Boolean> {
        val warp = warpRepository.getById(warpId) ?: return Result.failure(Exception("Warp not found"))
        if (warp.playerId != editorPlayerId && !bypassOwnership) return Result.failure(Exception("Not authorized"))
        if (warp.playerId == targetPlayerId) return Result.failure(Exception("Cannot co-own your own waystone"))

        val result = if (coOwnerRepository.isCoOwner(warpId, targetPlayerId)) {
            coOwnerRepository.remove(warpId, targetPlayerId)
            false
        } else {
            coOwnerRepository.add(CoOwner(warpId, targetPlayerId))
            true
        }
        return Result.success(result)
    }
}
