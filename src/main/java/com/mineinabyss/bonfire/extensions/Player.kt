package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.components.RespawnLocation
import com.mineinabyss.bonfire.components.save
import com.mineinabyss.bonfire.components.updateModel
import com.mineinabyss.geary.minecraft.access.geary
import com.mineinabyss.idofront.messaging.success
import org.bukkit.block.Campfire
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import com.mineinabyss.bonfire.data.Player as DataPlayer

fun Player.setRespawnLocation(spawn: RespawnLocation) {
    val oldSpawn = geary(this).get(RespawnLocation::class)
    if (oldSpawn != null) {
        val block = oldSpawn.location.block.state as? Campfire
        if(block != null) {
            if(!block.chunk.isLoaded) block.chunk.load()
            val bonfire = block.bonfireData();
            if (bonfire != null) {
                bonfire.players.remove(this.uniqueId)
                bonfire.updateModel()
                bonfire.save()
            }
        }
    }

    val newSpawn = spawn.location.block.state as? Campfire ?: return
    val bonfire = newSpawn.bonfireData() ?: return;
    success("Respawn point set")
    bonfire.players.add(this.uniqueId)
    bonfire.save()
    geary(this).setPersisting(spawn)

    transaction {
        val player = DataPlayer.select { DataPlayer.playerUUID eq uniqueId }.single()
        println(player)
//        if(player == null) {
//            val insertData = DataPlayer.insert {  }
//        }
    }
}


fun Player.removeBonfireSpawnLocation(uuid: UUID): Boolean {
    val gearyPlayer = geary(this)
    val respawn = gearyPlayer.get(RespawnLocation::class) ?: return false
    val block = respawn.location.block.state as? Campfire ?: return false
    val bonfire = block.bonfireData() ?: return false
    if(bonfire.uuid != uuid) return false
    success("Respawn point has been removed")
    gearyPlayer.remove(RespawnLocation::class)
    bonfire.players.remove(this.uniqueId)
    bonfire.save()
    return true
}