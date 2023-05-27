package com.mineinabyss.bonfire.extensions

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.MessageQueue
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.ecs.components.BonfireData
import com.mineinabyss.bonfire.ecs.components.BonfireEffectArea
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.geary.papermc.datastore.decode
import com.mineinabyss.geary.papermc.datastore.encode
import com.mineinabyss.geary.papermc.datastore.has
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.items.editItemMeta
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.spawning.spawn
import com.mineinabyss.idofront.time.ticks
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Campfire
import org.bukkit.entity.ItemDisplay
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*
import org.bukkit.block.data.type.Campfire as CampfireBlockData


val Campfire.isBonfire: Boolean get() = persistentDataContainer.has<BonfireData>()
fun Campfire.isBonfire(uuid: UUID): Boolean = bonfireData()?.uuid == uuid
private fun Campfire.bonfireData() = persistentDataContainer.decode<BonfireData>()
fun Campfire.save(data: BonfireData) {
    persistentDataContainer.encode(data)
    update()
}

var Campfire.uuid: UUID
    get() = bonfireData()?.uuid ?: error()
    set(value) = if (isBonfire) this.persistentDataContainer.encode(value) else error()

fun Campfire.getModel(): ItemDisplay? {
    if (!block.location.isWorldLoaded && !block.location.isChunkLoaded) return null
    return block.chunk.entities
        .filterIsInstance<ItemDisplay>()
        .find { it.uniqueId == uuid } ?: return createModel()
}

fun Campfire.error(): Nothing =
    error("Bonfire data not found for campfire at ${this.location}")

fun Campfire.createBonfire(newBonfireUUID: UUID, playerUUID: UUID) {
    val bonfireData = BonfireData(newBonfireUUID)
    save(bonfireData)

    transaction(bonfire.db) {
        Bonfire.insert {
            it[entityUUID] = newBonfireUUID
            it[location] = this@createBonfire.location
            it[ownerUUID] = playerUUID
        }
    }
}


fun Campfire.updateDisplay() {
    val location = block.location
    if (!location.isWorldLoaded || !location.isChunkLoaded) return
    if (!block.chunk.isEntitiesLoaded) return

    val model = getModel() ?: error("Couldn't get model")

    transaction(bonfire.db) {
        val playerCount = Players.select { Players.bonfireUUID eq this@updateDisplay.uuid }.count()

        model.itemStack = model.itemStack?.editItemMeta { setCustomModelData(1 + playerCount.toInt()) }
        blockData = (blockData as CampfireBlockData).apply { isLit = playerCount > 0 }
        update()

        val duplicates = Bonfire.select {
            (Bonfire.location eq model.location) and (Bonfire.entityUUID neq this@updateDisplay.uuid)
        }
        if (duplicates.any()) {
            duplicates.forEach { dupe ->
                Players.update({ Players.bonfireUUID eq dupe[Bonfire.entityUUID] }) {
                    it[bonfireUUID] = this@updateDisplay.uuid
                }

                Bukkit.getEntity(dupe[Bonfire.entityUUID])?.remove()
            }

            Bonfire.deleteWhere { (Bonfire.location eq model.location) and (entityUUID neq this@updateDisplay.uuid) }
        }
    }
}

fun Campfire.createModel(): ItemDisplay? {
    @Suppress("RemoveExplicitTypeArguments")
    return transaction<ItemDisplay?>(bonfire.db) {
        val bonfireRow = Bonfire.select { Bonfire.entityUUID eq this@createModel.uuid }.firstOrNull() ?: return@transaction null

        // Spawn Item Display
        val itemDisplay = bonfireRow[Bonfire.location].toCenterLocation().spawn<ItemDisplay> { this.setDefaults() } ?: return@transaction null

        val playerCount = Players.select { Players.bonfireUUID eq this@createModel.uuid }.count()

        itemDisplay.itemStack = itemDisplay.itemStack?.editItemMeta { setCustomModelData(playerCount.toInt()) }

        blockData = (blockData as CampfireBlockData).apply { isLit = playerCount > 0 }
        update()

        Bonfire.update({ Bonfire.entityUUID eq this@createModel.uuid }) {
            it[entityUUID] = itemDisplay.uniqueId
        }

        Players.update({ Players.bonfireUUID eq this@createModel.uuid }) {
            it[bonfireUUID] = itemDisplay.uniqueId
        }

        this@createModel.uuid = itemDisplay.uniqueId

        Players.select { Players.bonfireUUID eq this@createModel.uuid }.forEach {
            val p = Bukkit.getPlayer(it[Players.playerUUID]) ?: return@forEach
            p.toGeary().setPersisting(BonfireEffectArea(this@createModel.uuid))
        }

        save(BonfireData(itemDisplay.uniqueId))

        return@transaction itemDisplay
    }
}

fun Campfire.markStateChanged() {
    transaction(bonfire.db) {
        if (Players.select { Players.bonfireUUID eq this@markStateChanged.uuid }.empty()) {
            Bonfire.update({ Bonfire.entityUUID eq this@markStateChanged.uuid }) {
                it[stateChangedTimestamp] = LocalDateTime.now()
            }
        }
    }

    updateBonfire()
}

fun Campfire.updateBonfire() {
    if (!block.location.isWorldLoaded || !block.location.isChunkLoaded) return
    if (!block.chunk.isLoaded && !block.chunk.isEntitiesLoaded) return

    updateDisplay()
    updateFire()
}

fun Campfire.updateFire() {
    val bonfireData = this.block.blockData as CampfireBlockData
    val soulCampfire = (Material.SOUL_CAMPFIRE.createBlockData() as CampfireBlockData).apply { this.facing = bonfireData.facing }

    bonfire.plugin.launch(bonfire.plugin.asyncDispatcher) {
        delay(2.ticks)
        transaction(bonfire.db) {
            Players.select { Players.bonfireUUID eq this@updateFire.uuid }
                .forEach {
                    val player = it[Players.playerUUID].toPlayer() ?: return@forEach
                    player.sendBlockChange(block.location, soulCampfire)
                }
        }
    }
}

fun Campfire.destroy(destroyBlock: Boolean) {
    val model = Bukkit.getEntity(this.uuid) as? ItemDisplay
    var blockLocation = model?.location

    transaction(bonfire.db) {
        if (model == null) {
            blockLocation = Bonfire
                .select { Bonfire.entityUUID eq this@destroy.uuid }
                .firstOrNull()?.get(Bonfire.location)
        }

        Players
            .innerJoin(Bonfire, { bonfireUUID }, { entityUUID })
            .select { Players.bonfireUUID eq this@destroy.uuid }
            .forEach { row ->
                BonfireLogger.logRespawnUnset(row[Bonfire.location], Bukkit.getOfflinePlayer(row[Players.playerUUID]))
                val unsetMessage = "Your respawn point was unset because the bonfire was broken by the owner"
                val player = Bukkit.getPlayer(row[Players.playerUUID])
                if (player != null) {
                    player.error(unsetMessage)
                } else {
                    MessageQueue.insert {
                        it[content] = unsetMessage
                        it[playerUUID] = row[Players.playerUUID]
                    }
                }
            }
        Bonfire.deleteWhere { entityUUID eq this@destroy.uuid }
        Players.deleteWhere { bonfireUUID eq this@destroy.uuid }
    }

    if (destroyBlock && blockLocation != null) {
        if (blockLocation!!.block.state is Campfire) {
            blockLocation!!.block.type = Material.AIR
        }
    }

    model?.remove()
}
