package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.components.BonfireData
import com.mineinabyss.bonfire.data.Bonfire
import com.mineinabyss.geary.minecraft.store.decode
import com.mineinabyss.geary.minecraft.store.encode
import com.mineinabyss.geary.minecraft.store.has
import com.mineinabyss.idofront.messaging.broadcastVal
import org.bukkit.block.Campfire
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*


val Campfire.isBonfire: Boolean get() = persistentDataContainer.has<BonfireData>()
fun Campfire.isBonfire(uuid: UUID): Boolean = bonfireData()?.uuid == uuid
fun Campfire.bonfireData(): BonfireData? = persistentDataContainer.decode()
fun Campfire.save(data: BonfireData) = persistentDataContainer.encode(data)

fun Campfire.isCooking(): Boolean {
    if(getItem(0) != null) return true
    if(getItem(1) != null) return true
    if(getItem(2) != null) return true
    if(getItem(3) != null) return true
    return false
}

fun Campfire.makeBonfire(newBonfireUUID: UUID){
    val bonfireData = BonfireData(newBonfireUUID)
    this.save(bonfireData)

    transaction{
        Bonfire.insert{
            it[entityUUID] = newBonfireUUID
            it[location] = this@makeBonfire.location
        }
    }
}