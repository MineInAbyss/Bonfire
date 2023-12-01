package com.mineinabyss.bonfire.extensions

import com.comphenix.protocol.events.PacketContainer
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.blocky.api.BlockyFurnitures
import com.mineinabyss.blocky.components.core.BlockyFurniture
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.tracking.items.gearyItems
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.messaging.broadcast
import com.mineinabyss.protocolburrito.dsl.sendTo
import kotlinx.coroutines.delay
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import org.bukkit.craftbukkit.v1_20_R2.inventory.CraftItemStack
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import kotlin.time.Duration.Companion.seconds

val Entity.isBonfire: Boolean
    get() = this is ItemDisplay && this.toGearyOrNull()?.has<Bonfire>() == true

/**
 * Gets the current bonfire based on players BonfireRespawn component.
 * Then tries to get the entity if chunk is loaded/loads.
 * Then removes player from said bonfire's Bonfire component
 * Intended to be used for syncing the bonfire if a player swaps to another bonfire
 */
fun Player.removeOldBonfire() {
    val bonfireRespawn = toGeary().get<BonfireRespawn>() ?: return
    bonfireRespawn.bonfireLocation.world.getChunkAtAsync(bonfireRespawn.bonfireLocation).thenAccept { chunk ->
        chunk.entities.find { it.isBonfire && it.uniqueId == bonfireRespawn.bonfireUuid }?.toGearyOrNull()?.let { geary ->
            val bonfire = geary.get<Bonfire>() ?: return@let
            geary.setPersisting(bonfire.copy(bonfirePlayers = bonfire.bonfirePlayers - uniqueId))
        }
    }
}

/**
 * Updates the bonfire state for all players.
 */
fun ItemDisplay.updateBonfireState() {
    val bonfire = toGearyOrNull()?.get<Bonfire>() ?: return


    when {// Set the base-furniture item to the correct state
        bonfire.bonfirePlayers.isEmpty() -> {
            this.brightness = toGearyOrNull()?.get<BlockyFurniture>()?.properties?.brightness
            gearyItems.createItem(bonfire.states.unlit)?.let { itemStack = it }
        }
        else -> {
            this.brightness = Display.Brightness(15, 15)
            gearyItems.createItem(bonfire.states.lit)?.let { itemStack = it }

            // Set state via packets to 'set' for all online players currently at the bonfire
            val stateItem = gearyItems.createItem(bonfire.states.set) ?: return
            val metadataPacket = ClientboundSetEntityDataPacket(entityId,
                listOf(SynchedEntityData.DataValue(24, EntityDataSerializers.ITEM_STACK, CraftItemStack.asNMSCopy(stateItem)))
            )

            bonfire.bonfirePlayers.mapNotNull { it.toPlayer() }.filter { it.world == world && it.location.distanceSquared(location) < 8 }.forEach {
                PacketContainer.fromPacket(metadataPacket).sendTo(it)
            }
        }
    }
}
