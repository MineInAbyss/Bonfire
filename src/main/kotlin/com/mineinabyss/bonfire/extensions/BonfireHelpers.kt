package com.mineinabyss.bonfire.extensions

import com.comphenix.protocol.events.PacketContainer
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.ticks
import com.mineinabyss.blocky.api.BlockyFurnitures.isModelEngineFurniture
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireRespawn
import com.mineinabyss.geary.papermc.datastore.has
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import com.mineinabyss.geary.papermc.tracking.items.gearyItems
import com.mineinabyss.idofront.entities.toPlayer
import com.mineinabyss.idofront.messaging.broadcastVal
import com.mineinabyss.idofront.messaging.logError
import com.mineinabyss.idofront.nms.nbt.WrappedPDC
import com.mineinabyss.protocolburrito.dsl.sendTo
import kotlinx.coroutines.delay
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.craftbukkit.v1_20_R1.CraftServer
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack
import org.bukkit.craftbukkit.v1_20_R1.persistence.CraftPersistentDataContainer
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import java.nio.file.Files
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
fun ItemDisplay.updateBonfireState() {
    val bonfire = toGearyOrNull()?.get<Bonfire>() ?: return


    when {// Set the base-furniture item to the correct state
        bonfire.bonfirePlayers.isEmpty() ->
            gearyItems.createItem(bonfire.states.unlit)?.let { itemStack = it }
        else -> {
            gearyItems.createItem(bonfire.states.lit)?.let { itemStack = it }

            // Set state via packets to 'set' for all online players currently at the bonfire
            runCatching {
                val stateItem = gearyItems.createItem(bonfire.states.set) ?: return
                val metadataPacket = ClientboundSetEntityDataPacket(entityId,
                    listOf(SynchedEntityData.DataValue(22, EntityDataSerializers.ITEM_STACK, CraftItemStack.asNMSCopy(stateItem)))
                )


                com.mineinabyss.bonfire.bonfire.plugin.launch {
                    delay(3.ticks)
                   bonfire.bonfirePlayers.mapNotNull { it.toPlayer() }.forEach {
                        PacketContainer.fromPacket(metadataPacket).sendTo(it)
                   }
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }
}

val OFFLINE_MESSAGE_FILE =
    bonfire.plugin.dataFolder.resolve("offlineMessenger.txt").let { if (!it.exists()) it.createNewFile(); it }

fun UUID.addToOfflineMessager() = OFFLINE_MESSAGE_FILE.appendText("$this\n")
fun UUID.removeFromOfflineMessager() =
    OFFLINE_MESSAGE_FILE.writeText(OFFLINE_MESSAGE_FILE.readLines().filter { it != this.toString() }.joinToString("\n"))

/**
 * Gets the PlayerData from file for this UUID.
 */
internal fun UUID.getOfflinePlayerData(): CompoundTag? = (Bukkit.getServer() as CraftServer).handle.playerIo.getPlayerData(this.toString())

/**
 * Gets a copy of the WrappedPDC for this OfflinePlayer.
 * Care should be taken to ensure that the player is not online when this is called.
 */
fun OfflinePlayer.getOfflinePDC() : WrappedPDC? {
    if (isOnline) return WrappedPDC((player!!.persistentDataContainer as CraftPersistentDataContainer).toTagCompound())
    val baseTag = uniqueId.getOfflinePlayerData()?.getCompound("BukkitValues") ?: return null
    return WrappedPDC(baseTag)
}

/**
 * Saves the given WrappedPDC to the OfflinePlayer's PlayerData file.
 * Care should be taken to ensure that the player is not online when this is called.
 * @return true if successful, false otherwise.
 */
fun OfflinePlayer.saveOfflinePDC(pdc: WrappedPDC): Boolean {
    if (isOnline) return false
    val worldNBTStorage = (Bukkit.getServer() as CraftServer).server.playerDataStorage
    val tempFile = File(worldNBTStorage.playerDir, "$uniqueId.dat.tmp")
    val playerFile = File(worldNBTStorage.playerDir, "$uniqueId.dat")

    val mainPDc = uniqueId.getOfflinePlayerData() ?: return false
    mainPDc.put("BukkitValues", pdc.compoundTag) ?: return false
    runCatching {
        Files.newOutputStream(tempFile.toPath()).use { outStream ->
            NbtIo.writeCompressed(mainPDc, outStream)
            if (playerFile.exists() && !playerFile.delete()) logError("Failed to delete player file $uniqueId")
            if (!tempFile.renameTo(playerFile)) logError("Failed to rename player file $uniqueId")
        }
    }.onFailure {
        logError("Failed to save player file $uniqueId")
        it.printStackTrace()
        return false
    }
    return true
}
