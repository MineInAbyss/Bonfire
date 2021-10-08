@file:UseSerializers(UUIDSerializer::class)

package com.mineinabyss.bonfire.ecs.components

import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.MessageQueue
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.bonfire.data.Players.playerUUID
import com.mineinabyss.bonfire.extensions.save
import com.mineinabyss.bonfire.extensions.setBonfireModel
import com.mineinabyss.bonfire.logging.BonfireLogger
import com.mineinabyss.geary.ecs.api.autoscan.AutoscanComponent
import com.mineinabyss.geary.minecraft.access.toGeary
import com.mineinabyss.idofront.items.editItemMeta
import com.mineinabyss.idofront.messaging.error
import com.mineinabyss.idofront.serialization.UUIDSerializer
import com.okkero.skedule.SynchronizationContext
import com.okkero.skedule.schedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
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
import org.bukkit.block.data.type.Campfire as BlockDataTypeCampfire

@Serializable
@SerialName("bonfire:data")
@AutoscanComponent
class BonfireData(
        var uuid: UUID,
)

fun BonfireData.updateModel() {
    val model = Bukkit.getEntity(this.uuid) ?: createModel()
    if (model !is ArmorStand) return
    val block = model.world.getBlockAt(model.location)
    if (block.state !is Campfire) return
    val bonfire = block.state as Campfire
    val bonfireData = block.blockData as BlockDataTypeCampfire
    val item = model.equipment.helmet

    transaction {
        val playerCount = Players.select { Players.bonfireUUID eq this@updateModel.uuid }.count()

        //broadcast("Updating model for bonfire at x:${model.location.x} y:${model.location.y} z:${model.location.z} for $playerCount number of players.")

        model.equipment.helmet = item?.editItemMeta { setCustomModelData(1 + playerCount.toInt()) }
        bonfireData.isLit = playerCount > 0

        val duplicates = Bonfire.select {
            (Bonfire.location eq model.location) and (Bonfire.entityUUID neq this@updateModel.uuid)
        }
        if (duplicates.any()) {
            duplicates.forEach { dupe ->
                Players.update({ Players.bonfireUUID eq dupe[Bonfire.entityUUID] }) {
                    it[bonfireUUID] = this@updateModel.uuid
                }

                Bukkit.getEntity(dupe[Bonfire.entityUUID])?.remove()
            }

            Bonfire.deleteWhere { (Bonfire.location eq model.location) and (Bonfire.entityUUID neq this@updateModel.uuid) }
        }
    }

    bonfire.blockData = bonfireData
    bonfire.update()
}

fun BonfireData.createModel(): ArmorStand? {
    @Suppress("RemoveExplicitTypeArguments")
    return transaction<ArmorStand?> {
        val bonfireRow =
                Bonfire.select { Bonfire.entityUUID eq this@createModel.uuid }.firstOrNull() ?: return@transaction null

        // Spawn armor stand
        val armorStand = bonfireRow[Bonfire.location].world.spawnEntity(
                bonfireRow[Bonfire.location].toCenterLocation().apply { this.y = floor(y) }, EntityType.ARMOR_STAND
        ) as ArmorStand
        armorStand.setGravity(false)
        armorStand.isInvulnerable = true
        armorStand.isInvisible = true
        armorStand.isPersistent = true
        armorStand.isSmall = true
        armorStand.isMarker = true
        armorStand.setBonfireModel()
        armorStand.equipment.helmet = BonfireConfig.data.modelItem.toItemStack()

        val playerCount = Players.select { Players.bonfireUUID eq this@createModel.uuid }.count()

        armorStand.equipment.helmet =
                armorStand.equipment.helmet?.editItemMeta { setCustomModelData(1 + playerCount.toInt()) }

        Bonfire.update({ Bonfire.entityUUID eq this@createModel.uuid }) {
            it[entityUUID] = armorStand.uniqueId
        }

        Players.update({ Players.bonfireUUID eq this@createModel.uuid }) {
            it[bonfireUUID] = armorStand.uniqueId
        }

        this@createModel.uuid = armorStand.uniqueId

        Players.select { Players.bonfireUUID eq this@createModel.uuid }.forEach {
            val p = Bukkit.getPlayer(it[playerUUID]) ?: return@forEach
            p.toGeary().setPersisting(BonfireEffectArea(this@createModel.uuid))
        }

        (bonfireRow[Bonfire.location].block.state as Campfire).save(BonfireData(armorStand.uniqueId))

        return@transaction armorStand
    }
}

fun BonfireData.update() {
    transaction {
        if (Players.select { Players.bonfireUUID eq this@update.uuid }.empty()) {
            Bonfire.update({ Bonfire.entityUUID eq this@update.uuid }) {
                it[stateChangedTimestamp] = LocalDateTime.now()
            }
        }
    }

    updateModel()

    updateFire()
}

fun BonfireData.updateFire() {
    val model = Bukkit.getEntity(this.uuid) ?: createModel()
    if (model !is ArmorStand) return
    val block = model.world.getBlockAt(model.location)
    if (block.state !is Campfire) return
    val bonfireData = block.blockData as BlockDataTypeCampfire

    bonfirePlugin.schedule(SynchronizationContext.ASYNC) {
        waitFor(2)
        transaction {
            Players.select { Players.bonfireUUID eq this@updateFire.uuid }.forEach {
                val player = Bukkit.getPlayer(it[Players.playerUUID])
                player?.sendBlockChange(
                        block.location,
                        (Material.SOUL_CAMPFIRE.createBlockData() as BlockDataTypeCampfire).apply {
                            this.facing = bonfireData.facing
                        })
            }
        }
    }
}

fun BonfireData.destroyBonfire(destroyBlock: Boolean) {
    val model = Bukkit.getEntity(this.uuid) as? ArmorStand

    var blockLocation = model?.location

    transaction {
        if (model == null) {
            blockLocation = Bonfire
                    .select { Bonfire.entityUUID eq this@destroyBonfire.uuid }
                    .firstOrNull()?.get(Bonfire.location)
        }

        Players
                .innerJoin(Bonfire, { bonfireUUID }, { entityUUID })
                .select { Players.bonfireUUID eq this@destroyBonfire.uuid }
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
        Bonfire.deleteWhere { Bonfire.entityUUID eq this@destroyBonfire.uuid }
        Players.deleteWhere { Players.bonfireUUID eq this@destroyBonfire.uuid }
    }

    if (destroyBlock && blockLocation != null) {
        if (blockLocation!!.block.state is Campfire) {
            blockLocation!!.block.type = Material.AIR
        }
    }

    model?.remove()
}