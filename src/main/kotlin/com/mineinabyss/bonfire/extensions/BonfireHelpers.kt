package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.geary.papermc.datastore.has
import com.mineinabyss.geary.papermc.tracking.entities.toGearyOrNull
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import java.util.UUID

val ItemStack.isBonfire: Boolean
    get() = itemMeta?.persistentDataContainer?.has<Bonfire>() == true

val Entity.isBonfire: Boolean
    get() = this is ItemDisplay && this.toGearyOrNull()?.has<Bonfire>() == true

/**
* Calling loc.getChunk() will crash a Paper 1.19 build 62-66 (possibly more) Server if the Chunk does not exist.
* Instead, get Chunk location and check with World.isChunkLoaded() if the Chunk is loaded.
*/
fun Entity.isBonfireLoaded() =
    location.isWorldLoaded && location.isChunkLoaded

val OFFLINE_MESSAGE_FILE = bonfire.plugin.dataFolder.resolve("offlineMessager.txt").let { if (!it.exists()) it.createNewFile(); it }
fun UUID.addToOfflineMessager() = OFFLINE_MESSAGE_FILE.appendText("$this\n")
fun UUID.removeFromOfflineMessager() = OFFLINE_MESSAGE_FILE.writeText(OFFLINE_MESSAGE_FILE.readLines().filter { it != this.toString() }.joinToString("\n"))
