package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.components.destroyBonfire
import com.mineinabyss.bonfire.components.save
import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Campfire
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

fun Player.setRespawnLocation(bonfireUUID: UUID) {
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

        if(Players.select { Players.bonfireUUID eq bonfireUUID }.empty()){
            val newTimeUntilDestroy = Duration.between(
                LocalDateTime.now(),
                newBonfire[Bonfire.stateChangedTimestamp] + newBonfire[Bonfire.timeUntilDestroy]
            )

            if(newTimeUntilDestroy.isNegative){
                newBonfireData.destroyBonfire(true)
                error("The campfire has expired and degrades to dust")
                return@transaction
            }else{
                Bonfire.update({ Bonfire.entityUUID eq bonfireUUID }) {
                    it[timeUntilDestroy] = newTimeUntilDestroy
                }
            }
        }

        if (playerRow != null && playerRow[Players.bonfireUUID] != bonfireUUID) {
            val oldBonfireBlock = Bonfire
                .select { Bonfire.entityUUID eq playerRow[Players.bonfireUUID] }
                .first()[Bonfire.location]
                .block.state as? Campfire ?: return@transaction

            Players.update({ Players.playerUUID eq playerRow[Players.playerUUID] }) {
                it[Players.bonfireUUID] = bonfireUUID
            }

            oldBonfireBlock.bonfireData()?.save()
        } else if (playerRow == null) {
            Players.insert {
                it[Players.playerUUID] = playerUUID
                it[Players.bonfireUUID] = bonfireUUID
            }
        }

        newBonfireData.save()
        BonfireConfig.data.respawnSetSound.playSound(this@setRespawnLocation)
        success("Respawn point set")
    }
}


fun Player.removeBonfireSpawnLocation(bonfireUUID: UUID): Boolean {
    val playerUUID = uniqueId
    transaction {
        val deleteReturnCode = Players
            .deleteWhere {
                (Players.playerUUID eq playerUUID) and (Players.bonfireUUID eq bonfireUUID)
            }
        if (deleteReturnCode == 0) return@transaction false

        BonfireConfig.data.respawnUnsetSound.playSound(this@removeBonfireSpawnLocation)
        success("Respawn point has been removed")

        val bonfire = Bonfire
            .select { Bonfire.entityUUID eq bonfireUUID }
            .first()[Bonfire.location].block.state as? Campfire ?: return@transaction false
        bonfire.bonfireData()?.save() ?: return@transaction false
    }
    return true
}