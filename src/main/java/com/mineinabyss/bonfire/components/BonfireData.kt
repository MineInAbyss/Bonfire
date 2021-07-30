@file:UseSerializers(UUIDSerializer::class)

package com.mineinabyss.bonfire.components

import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.bonfire.data.Players
import com.mineinabyss.geary.ecs.api.autoscan.AutoscanComponent
import com.mineinabyss.geary.minecraft.store.encode
import com.mineinabyss.idofront.items.editItemMeta
import com.mineinabyss.idofront.serialization.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bukkit.Bukkit
import org.bukkit.block.Campfire
import org.bukkit.entity.ArmorStand
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import org.bukkit.block.data.type.Campfire as BlockDataTypeCampfire

@Serializable
@SerialName("bonfire:data")
@AutoscanComponent
class BonfireData(
    val uuid: UUID,
    //val players: MutableSet<UUID> = mutableSetOf()
)

fun BonfireData.updateModel() {
    val model = Bukkit.getEntity(this.uuid)
    if (model !is ArmorStand) return
    //val players = this.players
    val item = model.equipment?.helmet
    //model.equipment?.helmet = item?.editItemMeta { setCustomModelData(1 + players.size) }

    transaction {
        val playerCount = Players.select { Players.bonfireUUID eq this@updateModel.uuid }.count()

        model.equipment?.helmet = item?.editItemMeta { setCustomModelData(1 + playerCount.toInt()) }
    }
}

fun BonfireData.save() {
    val model = Bukkit.getEntity(this.uuid)
    if (model !is ArmorStand) return
    val block = model.world.getBlockAt(model.location)
    if (block.state !is Campfire) return
    val bonfire = block.state as Campfire
    val bonfireData = block.blockData as BlockDataTypeCampfire
    this.updateModel()

    transaction {
        val playerCount = Players.select { Players.bonfireUUID eq this@save.uuid }.count()
        bonfireData.isLit = playerCount > 0
    }

    bonfire.blockData = bonfireData
    bonfire.persistentDataContainer.encode(this)
    bonfire.update()
}