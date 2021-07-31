package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.components.RespawnLocation
import com.mineinabyss.bonfire.components.save
import com.mineinabyss.bonfire.components.updateModel
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.geary.minecraft.access.geary
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.messaging.success
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Campfire
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

fun Player.setRespawnLocation(spawn: RespawnLocation) {
    val oldSpawn = geary(this).get(RespawnLocation::class)
    if (oldSpawn != null) {
        val block = oldSpawn.location.block.state as? Campfire
        if (block != null) {
            if (!block.chunk.isLoaded) block.chunk.load()
            val bonfire = block.bonfireData()
            if (bonfire != null) {
                bonfire.players.remove(this.uniqueId)
                bonfire.updateModel()
                bonfire.save()
            }
        }
    }

    val newSpawn = spawn.location.block.state as? Campfire ?: return
    val bonfire = newSpawn.bonfireData() ?: return

    if (bonfire.players.size == 0) {
        transaction {
            val dbBonfire = Bonfire.select { Bonfire.uuid eq bonfire.uuid }.first()
            val newTimeUntilDestroy = Duration.between(
                LocalDateTime.now(),
                dbBonfire[Bonfire.stateChangedTimestamp] + dbBonfire[Bonfire.timeUntilDestroy]
            )

            if (newTimeUntilDestroy.isNegative && dbBonfire[Bonfire.location].block.state is Campfire) {
                dbBonfire[Bonfire.location].block.type = Material.AIR //FIXME: is this the correct way to destroy a block?
                Bukkit.getEntity(dbBonfire[Bonfire.uuid])?.remove() //FIXME: does the ECS need cleanup?

                Bonfire.deleteWhere { Bonfire.uuid eq dbBonfire[Bonfire.uuid] }

                error("The campfire has expired, and degrades to dust")

                return@transaction
            } else {
                Bonfire.update({ Bonfire.uuid eq bonfire.uuid }) {
                    it[timeUntilDestroy] = newTimeUntilDestroy
                }
            }
        }
    }

    success("Respawn point set")
    bonfire.players.add(this.uniqueId)
    bonfire.save()
    geary(this).setPersisting(spawn)

//    transaction {
//        val player = DataPlayer.select { DataPlayer.playerUUID eq uniqueId }.single()
//        println(player)
////        if(player == null) {
////            val insertData = DataPlayer.insert {  }
////        }
//    }
}


fun Player.removeBonfireSpawnLocation(uuid: UUID): Boolean {
    val gearyPlayer = geary(this)
    val respawn = gearyPlayer.get(RespawnLocation::class) ?: return false
    val block = respawn.location.block.state as? Campfire ?: return false
    val bonfire = block.bonfireData() ?: return false
    if (bonfire.uuid != uuid) return false
    success("Respawn point has been removed")
    gearyPlayer.remove(RespawnLocation::class)
    bonfire.players.remove(this.uniqueId)
    bonfire.save()
    return true
}