package com.mineinabyss.bonfire.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bukkit.entity.Player

@Serializable
@SerialName("sound")
data class SoundEffect(
    val sound: String,
    val volume: Float,
    val pitch: Float,
){
    fun playSound(player: Player){
        player.playSound(player.location, sound, volume, pitch)
    }
}