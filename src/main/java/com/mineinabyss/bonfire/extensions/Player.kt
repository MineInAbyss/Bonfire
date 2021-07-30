package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.components.save
import com.mineinabyss.bonfire.components.updateModel
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.idofront.messaging.success
import org.bukkit.block.Campfire
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun Player.setRespawnLocation(bonfireUUID: UUID) {
    val playerUUID = uniqueId

    transaction {
        val playerRow = Players
            .select { Players.playerUUID eq playerUUID }
            .firstOrNull()

        if (playerRow != null && playerRow[Players.bonfireUUID] != bonfireUUID) {
            val oldBonfireBlock = Bonfire
                .select { Players.bonfireUUID eq playerRow[Players.bonfireUUID] }
                .first()[Bonfire.location]
                .block.state as? Campfire ?: return@transaction

            if (!oldBonfireBlock.chunk.isLoaded) oldBonfireBlock.chunk.load()

            Players.update({ Players.playerUUID eq playerRow[Players.playerUUID] }) {
                it[Players.bonfireUUID] = bonfireUUID
            }

            val oldBonfire = oldBonfireBlock.bonfireData()
            if (oldBonfire != null) {
                oldBonfire.updateModel()
                oldBonfire.save()
            }
        } else if (playerRow == null) {
            Players.insert {
                it[Players.playerUUID] = playerUUID
                it[Players.bonfireUUID] = bonfireUUID
            }
            val newBonfire = Bonfire
                .select { Bonfire.entityUUID eq bonfireUUID }
                .first()[Bonfire.location]
                .block.state as? Campfire ?: return@transaction
            newBonfire.bonfireData()?.save() ?: return@transaction
        }
    }


//    val oldSpawn = geary(this).get(RespawnLocation::class)
//    if (oldSpawn != null) {
//        val block = oldSpawn.location.block.state as? Campfire
//        if(block != null) {
//            if(!block.chunk.isLoaded) block.chunk.load()
//            val bonfire = block.bonfireData();
//            if (bonfire != null) {
//                bonfire.players.remove(this.uniqueId)
//                bonfire.updateModel()
//                bonfire.save()
//            }
//        }
//    }
//
//    val newSpawn = spawn.location.block.state as? Campfire ?: return
//    val bonfire = newSpawn.bonfireData() ?: return;
//    success("Respawn point set")
//    bonfire.players.add(this.uniqueId)
//    bonfire.save()
//    geary(this).setPersisting(spawn)
//
//    transaction {
//        val player = DataPlayer.select { DataPlayer.playerUUID eq uniqueId }.single()
//        println(player)
////        if(player == null) {
////            val insertData = DataPlayer.insert {  }
////        }
//    }
}


fun Player.removeBonfireSpawnLocation(bonfireUUID: UUID): Boolean {
    val playerUUID = uniqueId
    transaction {
        val deleteReturnCode = Players
            .deleteWhere(limit = 1) {
                (Players.playerUUID eq playerUUID) and (Players.bonfireUUID eq bonfireUUID)
            }
        if (deleteReturnCode == 0) return@transaction false
        success("Respawn point has been removed")
        val bonfire =
            Bonfire.select { Bonfire.entityUUID eq bonfireUUID }.first()[Bonfire.location].block.state as? Campfire
                ?: return@transaction false
        bonfire.bonfireData()?.save() ?: return@transaction false
    }
    return true
//    val gearyPlayer = geary(this)
//    val respawn = gearyPlayer.get(RespawnLocation::class) ?: return false
//    val block = respawn.location.block.state as? Campfire ?: return false
//    val bonfire = block.bonfireData() ?: return false
//    if(bonfire.uuid != uuid) return false
//    success("Respawn point has been removed")
//    gearyPlayer.remove(RespawnLocation::class)
//    bonfire.players.remove(this.uniqueId)
//    bonfire.save()
//    return true
}