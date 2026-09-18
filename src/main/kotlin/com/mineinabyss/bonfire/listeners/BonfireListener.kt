package com.mineinabyss.bonfire.listeners

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireEffectArea
import com.mineinabyss.bonfire.components.BonfireRemoved
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.geary.papermc.datastore.encode
import com.mineinabyss.geary.papermc.datastore.encodeComponentsTo
import com.mineinabyss.geary.papermc.datastore.remove
import com.mineinabyss.geary.papermc.gearyPaper
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.withGeary
import com.mineinabyss.idofront.entities.toOfflinePlayer
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.location.up
import com.mineinabyss.idofront.nms.nbt.editOfflinePDC
import com.nexomc.nexo.api.NexoFurniture
import org.bukkit.entity.ItemDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent

class BonfireListener : Listener {

    @EventHandler
    fun BlockBreakEvent.onBreakBlock() { // Cancel block-break if it is below a bonfire
        val loc = block.location.toCenterLocation().up(1)
        if (block.world.getNearbyEntities(loc, 1.0, 1.0, 1.0).none { it.isBonfire }) return
        isCancelled = true
    }

    /**
     * When a bonfire is marked for removal, either via commands or being broken in any way
     * The below listener will handle completely removing it and all assosiacted playerdata
     * Since /kill commands wouldn't trigger NexoFurnitureBreakEvent then the main logic should be done here
     */
    @EventHandler
    fun EntityRemoveFromWorldEvent.onRemoveBonfire() {
        val itemDisplay = entity as? ItemDisplay ?: return
        val bonfireData = itemDisplay.takeIf { entity.isDead }?.toGearyOrNull()?.get<Bonfire>() ?: return

        NexoFurniture.remove(itemDisplay)

        bonfireData.bonfirePlayers.map { it.toOfflinePlayer() to it.toPlayer() }.forEach { (offline, online) ->
            if (online != null) with(online.toGeary()) {
                remove<BonfireEffectArea>()
                remove<BonfireRespawn>()
                online.withGeary { encodeComponentsTo(online) }
            } else offline.editOfflinePDC {
                with(gearyPaper.worldManager.global) {
                    encode(BonfireRemoved())
                    remove<BonfireRespawn>()
                }
            }
        }
    }
}