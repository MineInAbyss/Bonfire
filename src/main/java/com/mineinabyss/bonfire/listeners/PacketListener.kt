package com.mineinabyss.bonfire.listeners

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.geary.minecraft.access.geary
import com.mineinabyss.idofront.messaging.broadcastVal


object ChatPacketAdapter: PacketAdapter(bonfirePlugin, ListenerPriority.NORMAL, PacketType.Play.Server.GAME_STATE_CHANGE) {
//    override fun onPacketSending(event: PacketEvent) {
//        // 0 = No bed found message
//        if(event.packet.structures.read(0).integers.read(0) == 0) {
//            geary(event.player).get(RespawnLocation::class) ?: return
//            event.isCancelled = true
//        }
//    }
}