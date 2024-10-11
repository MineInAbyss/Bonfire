package com.mineinabyss.bonfire.extensions

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.blocky.components.core.BlockyFurniture
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireRemoved
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.tracking.items.gearyItems
import com.mineinabyss.idofront.entities.toPlayer
import kotlinx.coroutines.delay
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import kotlin.math.pow

fun Iterable<Entity>.forEachBonfire(action: (ItemDisplay) -> Unit): Unit {
    for (element in this.filterIsBonfire()) action(element)
}
fun Array<Entity>.forEachBonfire(action: (ItemDisplay) -> Unit): Unit {
    for (element in this.filterIsBonfire()) action(element)
}
fun Iterable<Entity>.filterIsBonfire() = mapNotNull { it.takeIf { it.isBonfire } as? ItemDisplay }
fun Array<Entity>.filterIsBonfire() = mapNotNull { it.takeIf { it.isBonfire } as? ItemDisplay }

val Entity.isBonfire: Boolean
    get() = this is ItemDisplay && this.toGearyOrNull()?.has<Bonfire>() == true

fun Player.canBreakBonfire(bonfireData: Bonfire): Boolean {
    return bonfireData.bonfirePlayers.isEmpty() || bonfireData.bonfireOwner == this.uniqueId || hasPermission(BonfirePermissions.REMOVE_BONFIRE_PERMISSION)
}

/**
 * Gets the current bonfire based on players BonfireRespawn component.
 * Then tries to get the entity if chunk is loaded/loads.
 * Then removes player from said bonfire's Bonfire component
 * Intended to be used for syncing the bonfire if a player swaps to another bonfire
 */
fun Player.removeOldBonfire() {
    toGeary().remove<BonfireRemoved>()
    val bonfireRespawn = toGeary().get<BonfireRespawn>() ?: return
    bonfireRespawn.bonfireLocation.world.getChunkAtAsync(bonfireRespawn.bonfireLocation).thenAccept { chunk ->
        chunk.entities.find { it.isBonfire && it.uniqueId == bonfireRespawn.bonfireUuid }?.toGearyOrNull()?.let { geary ->
            geary.get<Bonfire>()?.let { it.bonfirePlayers -= uniqueId }
        }
    }
}

private val bonfireTrackingRadius = (Bukkit.getServer().simulationDistance * 16.0).pow(2)

/**
 * Updates the bonfire state for all players.
 */
fun ItemDisplay.updateBonfireState() {
    val bonfire = toGearyOrNull()?.get<Bonfire>() ?: return


    when {// Set the base-furniture item to the correct state
        bonfire.bonfirePlayers.isEmpty() -> {
            this.brightness = toGearyOrNull()?.get<BlockyFurniture>()?.properties?.brightness
            gearyItems.createItem(bonfire.states.unlit)?.let { setItemStack(it) }
        }
        else -> {
            this.brightness = Display.Brightness(15, 15)
            gearyItems.createItem(bonfire.states.lit)?.let { setItemStack(it) }

            // Set state via packets to 'set' for all online players currently at the bonfire
            val stateItem = gearyItems.createItem(bonfire.states.set) ?: return
            val metadataPacket = ClientboundSetEntityDataPacket(entityId,
                listOf(SynchedEntityData.DataValue(23, EntityDataSerializers.ITEM_STACK, CraftItemStack.asNMSCopy(stateItem)))
            )

            com.mineinabyss.bonfire.bonfire.plugin.launch(com.mineinabyss.bonfire.bonfire.plugin.minecraftDispatcher) {
                delay(3.ticks)
                bonfire.bonfirePlayers.mapNotNull { it.toPlayer() }.filter { it.world.uid == world.uid && it.location.distanceSquared(location) < bonfireTrackingRadius }.forEach {
                    (it as CraftPlayer).handle.connection.send(metadataPacket)
                }
            }
        }
    }
}
