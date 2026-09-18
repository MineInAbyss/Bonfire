package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireRemoved
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.withGeary
import com.nexomc.nexo.api.NexoFurniture
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player

fun Iterable<Entity>.forEachBonfire(action: (ItemDisplay) -> Unit) {
    for (element in this.filterIsBonfire()) action(element)
}
fun Array<Entity>.forEachBonfire(action: (ItemDisplay) -> Unit) {
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



/**
 * Updates the bonfire state for all players.
 *
 * Only the lighting is set here, the item each player sees comes from [BonfireItemOverride] whenever
 * Nexo sends the furniture, which is what the refresh below asks it to do
 */
fun ItemDisplay.updateBonfireState() {
    withGeary {
        val bonfire = toGearyOrNull()?.get<Bonfire>() ?: return

        brightness = when {
            bonfire.bonfirePlayers.isEmpty() -> runCatching {
                NexoFurniture.furnitureMechanic(this@updateBonfireState)?.properties?.brightness
            }.getOrNull()
            else -> Display.Brightness(15, 15)
        }

        NexoFurniture.refreshFurnitureItem(this@updateBonfireState)
    }
}
