package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.ecs.components.BonfireEffectArea
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.geary.minecraft.access.toGeary
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import org.bukkit.OfflinePlayer
import org.bukkit.block.Campfire
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

fun OfflinePlayer.setRespawnLocation(bonfireUUID: UUID) {
    val playerUUID = uniqueId

    transaction {
        val playerRow = Players
            .select { Players.playerUUID eq playerUUID }
            .firstOrNull()

        val newBonfire = Bonfire
            .select { Bonfire.entityUUID eq bonfireUUID }
            .firstOrNull() ?: return@transaction
        val newCampfire = newBonfire[Bonfire.location].block.state as? Campfire ?: return@transaction
        newCampfire.isBonfire || return@transaction

        if (Players.select { Players.bonfireUUID eq bonfireUUID }.empty()) {
            val newTimeUntilDestroy = Duration.between(
                LocalDateTime.now(),
                newBonfire[Bonfire.stateChangedTimestamp] + newBonfire[Bonfire.timeUntilDestroy]
            )

            if (newTimeUntilDestroy.isNegative) {
                newCampfire.destroy(true)
                this@setRespawnLocation.player?.error("The bonfire has expired and turned to ash")
                return@transaction
            } else {
                Bonfire.update({ Bonfire.entityUUID eq bonfireUUID }) {
                    it[timeUntilDestroy] = newTimeUntilDestroy
                }
            }
        }

        if (playerRow != null && playerRow[Players.bonfireUUID] != bonfireUUID) {
            val oldBonfireBlock = Bonfire
                .select { Bonfire.entityUUID eq playerRow[Players.bonfireUUID] }
                .firstOrNull()?.get(Bonfire.location)?.block?.state as? Campfire

            Players.update({ Players.playerUUID eq playerRow[Players.playerUUID] }) {
                it[Players.bonfireUUID] = bonfireUUID
            }

            if(oldBonfireBlock != null) {
                BonfireLogger.logRespawnUnset(oldBonfireBlock.location, this@setRespawnLocation)

                if (oldBonfireBlock.chunk.isEntitiesLoaded) oldBonfireBlock.updateBonfire() // update old bonfire model
            }
        } else if (playerRow == null) {
            Players.insert {
                it[Players.playerUUID] = playerUUID
                it[Players.bonfireUUID] = bonfireUUID
            }
        }

        newCampfire.updateBonfire()
        this@setRespawnLocation.player?.let { BonfireConfig.data.respawnSetSound.playSound(it) }
        this@setRespawnLocation.player?.success("Respawn point set")
        val p = this@setRespawnLocation.player
        p?.toGeary()?.setPersisting(BonfireEffectArea(newCampfire.uuid))

        BonfireLogger.logRespawnSet(newCampfire.location, this@setRespawnLocation)
    }
}


fun OfflinePlayer.removeBonfireSpawnLocation(bonfireUUID: UUID): Boolean {
    return transaction{
        val dbPlayer = Players
            .select{Players.playerUUID eq this@removeBonfireSpawnLocation.uniqueId}
            .firstOrNull() ?: return@transaction true

        if(dbPlayer[Players.bonfireUUID] != bonfireUUID){
            return@transaction false
        }

        val deleteCode = Players.deleteWhere {
            (Players.playerUUID eq this@removeBonfireSpawnLocation.uniqueId) and
                    (Players.bonfireUUID eq bonfireUUID)
        }
        if (deleteCode == 0) return@transaction false

        this@removeBonfireSpawnLocation.player?.let { BonfireConfig.data.respawnUnsetSound.playSound(it) }
        this@removeBonfireSpawnLocation.player?.error("Respawn point has been removed")

        val p = this@removeBonfireSpawnLocation.player
        p?.toGeary()?.remove<BonfireEffectArea>()

        Bonfire
            .select { Bonfire.entityUUID eq bonfireUUID }
            .firstOrNull()?.get(Bonfire.location)?.let {
                BonfireLogger.logRespawnUnset(it, this@removeBonfireSpawnLocation)
            }

        val bonfire = Bonfire
            .select { Bonfire.entityUUID eq dbPlayer[Players.bonfireUUID] }
            .firstOrNull()?.get(Bonfire.location)?.block?.state as? Campfire
        bonfire?.updateBonfire()
        return@transaction true
    }
}