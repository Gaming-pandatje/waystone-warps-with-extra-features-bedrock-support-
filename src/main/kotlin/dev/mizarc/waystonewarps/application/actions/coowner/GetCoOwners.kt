package dev.mizarc.waystonewarps.application.actions.coowner

import dev.mizarc.waystonewarps.domain.coowner.CoOwnerRepository
import java.util.UUID

class GetCoOwners(private val coOwnerRepository: CoOwnerRepository) {
    fun execute(warpId: UUID): List<UUID> = coOwnerRepository.getByWarp(warpId)
}
