package dev.mizarc.waystonewarps.infrastructure.persistence.coowner

import co.aikar.idb.Database
import dev.mizarc.waystonewarps.domain.coowner.CoOwner
import dev.mizarc.waystonewarps.domain.coowner.CoOwnerRepository
import dev.mizarc.waystonewarps.infrastructure.persistence.storage.Storage
import java.util.UUID

class CoOwnerRepositorySQLite(private val storage: Storage<Database>) : CoOwnerRepository {
    private val coOwnerMap = HashMap<UUID, MutableSet<UUID>>()

    init {
        preload()
    }

    override fun isCoOwner(warpId: UUID, playerId: UUID): Boolean {
        return coOwnerMap[warpId]?.contains(playerId) == true
    }

    override fun getByWarp(warpId: UUID): List<UUID> {
        return coOwnerMap[warpId]?.toList() ?: emptyList()
    }

    override fun getByPlayer(playerId: UUID): List<UUID> {
        return coOwnerMap.entries
            .filter { (_, players) -> players.contains(playerId) }
            .map { (warpId, _) -> warpId }
            .toList()
    }

    override fun add(coOwner: CoOwner) {
        coOwnerMap.computeIfAbsent(coOwner.warpId) { HashSet() }.add(coOwner.playerId)
        storage.connection.executeInsert(
            "INSERT INTO co_owners (warpId, playerId) VALUES (?, ?);",
            coOwner.warpId, coOwner.playerId
        )
    }

    override fun remove(warpId: UUID, playerId: UUID) {
        coOwnerMap[warpId]?.remove(playerId)
        if (coOwnerMap[warpId]?.isEmpty() == true) coOwnerMap.remove(warpId)
        storage.connection.executeUpdate(
            "DELETE FROM co_owners WHERE warpId=? AND playerId=?", warpId, playerId
        )
    }

    override fun removeByWarp(warpId: UUID) {
        coOwnerMap.remove(warpId)
        storage.connection.executeUpdate("DELETE FROM co_owners WHERE warpId=?", warpId)
    }

    private fun preload() {
        val results = storage.connection.getResults("SELECT * FROM co_owners;")
        for (result in results) {
            val warpId = UUID.fromString(result.getString("warpId"))
            val playerId = UUID.fromString(result.getString("playerId"))
            coOwnerMap.computeIfAbsent(warpId) { HashSet() }.add(playerId)
        }
    }
}
