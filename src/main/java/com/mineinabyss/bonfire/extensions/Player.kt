package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.ecs.components.BonfireEffectArea
import com.mineinabyss.bonfire.ecs.components.destroyBonfire
import com.mineinabyss.bonfire.ecs.components.save
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.geary.minecraft.access.geary
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
        val newBonfireBlock = newBonfire[Bonfire.location].block.state as? Campfire ?: return@transaction
        val newBonfireData = newBonfireBlock.bonfireData() ?: return@transaction

        if (Players.select { Players.bonfireUUID eq bonfireUUID }.empty()) {
            val newTimeUntilDestroy = Duration.between(
                LocalDateTime.now(),
                newBonfire[Bonfire.stateChangedTimestamp] + newBonfire[Bonfire.timeUntilDestroy]
            )

            if (newTimeUntilDestroy.isNegative) {
                newBonfireData.destroyBonfire(true)
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

            if(oldBonfireBlock != null){
                BonfireLogger.logRespawnUnset(oldBonfireBlock.location, this@setRespawnLocation)

                oldBonfireBlock.bonfireData()?.save() // update old bonfire model
            }
        } else if (playerRow == null) {
            Players.insert {
                it[Players.playerUUID] = playerUUID
                it[Players.bonfireUUID] = bonfireUUID
            }
        }

        newBonfireData.save()
        this@setRespawnLocation.player?.let { BonfireConfig.data.respawnSetSound.playSound(it) }
        this@setRespawnLocation.player?.success("Respawn point set")
        val p = this@setRespawnLocation.player;
        if (p != null) {
            geary(p).setPersisting(BonfireEffectArea(newBonfireData.uuid))
        }

        BonfireLogger.logRespawnSet(newBonfireBlock.location, this@setRespawnLocation)
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
        if (p != null) {
            geary(p).remove<BonfireEffectArea>()
        }

        Bonfire
            .select { Bonfire.entityUUID eq bonfireUUID }
            .firstOrNull()?.get(Bonfire.location)?.let {
                BonfireLogger.logRespawnUnset(it, this@removeBonfireSpawnLocation)
            }

        val bonfire = Bonfire
            .select { Bonfire.entityUUID eq dbPlayer[Players.bonfireUUID] }
            .firstOrNull()?.get(Bonfire.location)?.block?.state as? Campfire
        bonfire?.bonfireData()?.save()
        return@transaction true
    }
}