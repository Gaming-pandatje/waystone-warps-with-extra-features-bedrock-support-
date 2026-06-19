package dev.mizarc.waystonewarps.infrastructure.persistence.migrations

import co.aikar.idb.Database

class Migration4_AddCoOwners : Migration {
    override val fromVersion: Int = 3
    override val toVersion: Int = 4

    override fun migrate(db: Database) {
        db.executeUpdate("""
            CREATE TABLE IF NOT EXISTS co_owners (
                warpId TEXT NOT NULL,
                playerId TEXT NOT NULL,
                PRIMARY KEY (warpId, playerId)
            );
        """.trimIndent())
    }
}
