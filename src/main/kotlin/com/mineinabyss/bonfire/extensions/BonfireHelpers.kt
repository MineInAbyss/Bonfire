package com.mineinabyss.bonfire.extensions

import com.comphenix.protocol.events.PacketContainer
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.geary.papermc.datastore.has
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.tracking.items.gearyItems
import com.mineinabyss.protocolburrito.dsl.sendTo
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*


val ItemStack.isBonfire: Boolean
    get() = itemMeta?.persistentDataContainer?.has<Bonfire>() == true

val Entity.isBonfire: Boolean
    get() = this is ItemDisplay && this.toGearyOrNull()?.has<Bonfire>() == true

/**
 * Gets the current bonfire based on players BonfireRespawn component.
 * Then tries to get the entity if chunk is loaded/loads.
 * Then removes player from said bonfire's Bonfire component
 * Intended to be used for syncing the bonfire if a player swaps to another bonfire
 */
fun Player.removeOldBonfire() {
    toGeary().get<BonfireRespawn>()?.let {
        if (it.bonfireLocation.isChunkLoaded || it.bonfireLocation.chunk.load()) {
            Bukkit.getEntity(it.bonfireUuid)?.toGearyOrNull()?.let { geary ->
                geary.get<Bonfire>()?.let { bonfire ->
                    geary.setPersisting(bonfire.copy(bonfirePlayers = bonfire.bonfirePlayers - uniqueId))
                }
            }
        }
    }
}

/**
 * Updates the bonfire state for all players.
 */
fun ItemDisplay.updateBonfireState(player: Player? = null) {
    val bonfire = toGearyOrNull()?.get<Bonfire>() ?: return

    fun createPacket(player: Player) {
        runCatching {
            val stateItem = gearyItems.createItem(
                when {
                    bonfire.bonfirePlayers.isEmpty() -> bonfire.states.unlit
                    player.uniqueId !in bonfire.bonfirePlayers -> bonfire.states.lit
                    else -> bonfire.states.set
                }
            ) ?: return

            val metadataPacket = ClientboundSetEntityDataPacket(
                entityId, listOf(SynchedEntityData.DataValue(22, EntityDataSerializers.ITEM_STACK, CraftItemStack.asNMSCopy(stateItem)))
            )

            PacketContainer.fromPacket(metadataPacket).sendTo(player)
        }.onFailure {
            it.printStackTrace()
        }
    }

    player?.let { createPacket(it) } ?: Bukkit.getOnlinePlayers().forEach(::createPacket)
}

val OFFLINE_MESSAGE_FILE =
    bonfire.plugin.dataFolder.resolve("offlineMessenger.txt").let { if (!it.exists()) it.createNewFile(); it }

fun UUID.addToOfflineMessager() = OFFLINE_MESSAGE_FILE.appendText("$this\n")
fun UUID.removeFromOfflineMessager() =
    OFFLINE_MESSAGE_FILE.writeText(OFFLINE_MESSAGE_FILE.readLines().filter { it != this.toString() }.joinToString("\n"))
