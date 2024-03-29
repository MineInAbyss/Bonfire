package com.mineinabyss.bonfire.listeners

import com.comphenix.protocol.events.PacketContainer
import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mineinabyss.blocky.helpers.FurnitureUUID
import com.mineinabyss.blocky.helpers.GenericHelpers.toBlockCenterLocation
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.Bonfire
import com.mineinabyss.bonfire.components.BonfireDebug
import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import com.mineinabyss.idofront.entities.toOfflinePlayer
import com.mineinabyss.idofront.textcomponents.miniMsg
import com.mineinabyss.protocolburrito.dsl.sendTo
import it.unimi.dsi.fastutil.ints.IntList
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.Vec3
import org.bukkit.Color
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerToggleSneakEvent
import java.util.*
import kotlin.time.Duration.Companion.seconds

class DebugListener : Listener {

    @EventHandler
    fun PlayerToggleSneakEvent.onDebugToggle() {
        player.toGeary().has<BonfireDebug>() || return
        player.getNearbyEntities(10.0,10.0,10.0).filterIsInstance<ItemDisplay>().filter { it.isBonfire }.forEach {
            if (isSneaking) player.sendDebugTextDisplay(it)
            else removeDebugTextDisplay(player)
        }
    }

    private val debugIdMap = mutableMapOf<UUID, MutableMap<FurnitureUUID, Int>>()
    private fun Player.sendDebugTextDisplay(baseEntity: ItemDisplay) {
        val entityIds = debugIdMap.computeIfAbsent(uniqueId) { mutableMapOf(baseEntity.uniqueId to Entity.nextEntityId()) }
        val entityId = entityIds.computeIfAbsent(baseEntity.uniqueId) { Entity.nextEntityId() }
        val loc = baseEntity.location.clone().toBlockCenterLocation().add(bonfire.config.debugTextOffset)
        val textDisplayPacket = ClientboundAddEntityPacket(
            entityId, UUID.randomUUID(),
            loc.x, loc.y, loc.z, loc.pitch, loc.yaw,
            EntityType.TEXT_DISPLAY, 0, Vec3.ZERO, 0.0
        )

        PacketContainer.fromPacket(textDisplayPacket).sendTo(this)
        bonfire.plugin.launch {
            do {
                this@sendDebugTextDisplay.sendDebugText(baseEntity, entityId)
                delay(1.seconds)
            } while (isSneaking)
            removeDebugTextDisplay(this@sendDebugTextDisplay)
        }
    }

    val DEBUG_TEXT: String = """
        <yellow>Bonfire-size <gold><size>
        <gray>Players: <players>
    """.trimIndent()

    private suspend fun Player.sendDebugText(baseEntity: ItemDisplay, entityId: Int) {
        val tagResolver = TagResolver.resolver(
            TagResolver.resolver("size", Tag.inserting(baseEntity.toGeary().get<Bonfire>()!!.let { "${it.bonfirePlayers.size}/${it.maxPlayerCount}" }.miniMsg())),
            TagResolver.resolver("players", Tag.inserting(baseEntity.toGeary().get<Bonfire>()!!.bonfirePlayers.joinToString { it.toOfflinePlayer().name.toString() }.miniMsg())),
        )
        val text = Component.Serializer.fromJson(GsonComponentSerializer.gson().serialize(DEBUG_TEXT.trimIndent().miniMsg(tagResolver))) ?: Component.empty()

        // Set flags using bitwise operations
        var bitmask = 0
        bitmask = bitmask or 0x01 // Set bit 0 (Has shadow)
        bitmask = bitmask or (0 and 0x0F shl 3) // Set alignment to CENTER (0)

        withContext(bonfire.plugin.asyncDispatcher) {
            PacketContainer.fromPacket(
                ClientboundSetEntityDataPacket(
                    entityId, listOf(
                        SynchedEntityData.DataValue(15, EntityDataSerializers.BYTE, 1), // Billboard
                        SynchedEntityData.DataValue(23, EntityDataSerializers.COMPONENT, text),
                        SynchedEntityData.DataValue(
                            25,
                            EntityDataSerializers.INT,
                            Color.fromARGB(0, 0, 0, 0).asARGB()
                        ), // Transparent background
                        SynchedEntityData.DataValue(27, EntityDataSerializers.BYTE, bitmask.toByte())
                    )
                )
            ).sendTo(this@sendDebugText)
        }
    }

    private fun removeDebugTextDisplay(player: Player) =
        debugIdMap[player.uniqueId]?.values?.let {
            PacketContainer.fromPacket(ClientboundRemoveEntitiesPacket(IntList.of(*it.toIntArray()))).sendTo(player)
        }
}
