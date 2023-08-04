package com.mineinabyss.bonfire.extensions

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot
import com.comphenix.protocol.wrappers.Pair
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.protocolManager
import com.mineinabyss.geary.papermc.datastore.has
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.tracking.items.gearyItems
import org.bukkit.Bukkit
import org.bukkit.Material
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
 * Toggles the bonfire state for all players.
 */
fun Bonfire.toggleBonfireState(baseEntity: ItemDisplay) {
    Bukkit.getOnlinePlayers().forEach { player ->
        val packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT)
        packet.integers.write(0, baseEntity.entityId)
        packet.slotStackPairLists.write(
            0,
            listOf(Pair(ItemSlot.MAINHAND, (
                    when {
                        this.bonfirePlayers.isEmpty() -> gearyItems.createItem(this.states.unlit)
                        player.uniqueId in this.bonfirePlayers -> gearyItems.createItem(this.states.set)
                        else -> gearyItems.createItem(this.states.lit)
                    } ?: ItemStack(Material.AIR))
                )
            )
        )

        runCatching {
            protocolManager.sendServerPacket(player, packet)
        }
    }
}

val OFFLINE_MESSAGE_FILE =
    bonfire.plugin.dataFolder.resolve("offlineMessenger.txt").let { if (!it.exists()) it.createNewFile(); it }

fun UUID.addToOfflineMessager() = OFFLINE_MESSAGE_FILE.appendText("$this\n")
fun UUID.removeFromOfflineMessager() =
    OFFLINE_MESSAGE_FILE.writeText(OFFLINE_MESSAGE_FILE.readLines().filter { it != this.toString() }.joinToString("\n"))
