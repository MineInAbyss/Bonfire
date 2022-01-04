package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.BonfireContext
import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.MessageQueue
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.ecs.components.BonfireData
import com.mineinabyss.bonfire.ecs.components.BonfireEffectArea
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.geary.minecraft.access.toGeary
import com.mineinabyss.geary.minecraft.store.decode
import com.mineinabyss.geary.minecraft.store.encode
import com.mineinabyss.geary.minecraft.store.has
import com.mineinabyss.idofront.items.editItemMeta
import com.mineinabyss.idofront.messaging.error
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Campfire
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*
import kotlin.math.floor
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
    set(value) = bonfireData()?.updateUUID(value) ?: error()

fun Campfire.getModel(): ArmorStand? {
    return block.chunk.entities
        .filterIsInstance<ArmorStand>()
        .find { it.uniqueId == uuid } ?: return createModel()
}

fun Campfire.error(): Nothing =
    error("Bonfire data not found for campfire $this")

fun Campfire.createBonfire(newBonfireUUID: UUID, playerUUID: UUID) {
    val bonfireData = BonfireData(newBonfireUUID)
    save(bonfireData)

    transaction(BonfireContext.db) {
        Bonfire.insert {
            it[entityUUID] = newBonfireUUID
            it[location] = this@createBonfire.location
            it[ownerUUID] = playerUUID
        }
    }
}


fun Campfire.updateDisplay() {
    val block = block

    if (!block.chunk.isEntitiesLoaded) return

    val model = getModel() ?: error("Couldn't get model")

    transaction(BonfireContext.db) {
        val playerCount = Players.select { Players.bonfireUUID eq this@updateDisplay.uuid }.count()

        //broadcast("Updating model for bonfire at x:${model.location.x} y:${model.location.y} z:${model.location.z} for $playerCount number of players.")

        model.equipment.helmet = model.equipment.helmet?.editItemMeta { setCustomModelData(1 + playerCount.toInt()) }
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

            Bonfire.deleteWhere { (Bonfire.location eq model.location) and (Bonfire.entityUUID neq this@updateDisplay.uuid) }
        }
    }
}

fun Campfire.createModel(): ArmorStand? {
    @Suppress("RemoveExplicitTypeArguments")
    return transaction<ArmorStand?> {
        val bonfireRow =
            Bonfire.select { Bonfire.entityUUID eq this@createModel.uuid }.firstOrNull() ?: return@transaction null

        // Spawn armor stand
        val armorStand = (bonfireRow[Bonfire.location].world.spawnEntity(
            bonfireRow[Bonfire.location].toCenterLocation().apply { this.y = floor(y) }, EntityType.ARMOR_STAND
        ) as ArmorStand).setDefaults()

        val playerCount = Players.select { Players.bonfireUUID eq this@createModel.uuid }.count()

        armorStand.equipment.helmet =
            armorStand.equipment.helmet?.editItemMeta { setCustomModelData(playerCount.toInt()) }

        blockData = (blockData as CampfireBlockData).apply { isLit = playerCount > 0 }
        update()

        Bonfire.update({ Bonfire.entityUUID eq this@createModel.uuid }) {
            it[entityUUID] = armorStand.uniqueId
        }

        Players.update({ Players.bonfireUUID eq this@createModel.uuid }) {
            it[bonfireUUID] = armorStand.uniqueId
        }

        this@createModel.uuid = armorStand.uniqueId

        Players.select { Players.bonfireUUID eq this@createModel.uuid }.forEach {
            val p = Bukkit.getPlayer(it[Players.playerUUID]) ?: return@forEach
            p.toGeary().setPersisting(BonfireEffectArea(this@createModel.uuid))
        }

        save(BonfireData(armorStand.uniqueId))

        return@transaction armorStand
    }
}

fun Campfire.markStateChanged() {
    transaction(BonfireContext.db) {
        if (Players.select { Players.bonfireUUID eq this@markStateChanged.uuid }.empty()) {
            Bonfire.update({ Bonfire.entityUUID eq this@markStateChanged.uuid }) {
                it[stateChangedTimestamp] = LocalDateTime.now()
            }
        }
    }

    updateBonfire()
}

fun Campfire.updateBonfire() {
    if (!block.chunk.isLoaded && !block.chunk.isEntitiesLoaded) return

    updateDisplay()
    updateFire()
}

fun Campfire.updateFire() {
    val bonfireData = this.block.blockData as CampfireBlockData

    bonfirePlugin.schedule(SynchronizationContext.ASYNC) {
        waitFor(2)
        transaction(BonfireContext.db) {
            Players.select { Players.bonfireUUID eq this@updateFire.uuid }.forEach {
                val player = Bukkit.getPlayer(it[Players.playerUUID])
                player?.sendBlockChange(
                    block.location,
                    (Material.SOUL_CAMPFIRE.createBlockData() as CampfireBlockData).apply {
                        this.facing = bonfireData.facing
                    })
            }
        }
    }
}

fun Campfire.destroy(destroyBlock: Boolean) {
    val model = Bukkit.getEntity(this.uuid) as? ArmorStand

    var blockLocation = model?.location

    transaction(BonfireContext.db) {
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
        Bonfire.deleteWhere { Bonfire.entityUUID eq this@destroy.uuid }
        Players.deleteWhere { Players.bonfireUUID eq this@destroy.uuid }
    }

    if (destroyBlock && blockLocation != null) {
        if (blockLocation!!.block.state is Campfire) {
            blockLocation!!.block.type = Material.AIR
        }
    }

    model?.remove()
}