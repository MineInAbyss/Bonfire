package com.mineinabyss.bonfire.systems

import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.BonfireEffectArea
import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.systems.query.Query
import com.mineinabyss.idofront.time.ticks
import org.bukkit.Particle
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player

fun Geary.bonfireEffectSystem() = system(object : Query(this) {
    val player by get<Player>()
    val effect by get<BonfireEffectArea>()
}).every(10.ticks).exec { q ->
    // Check if still near a bonfire
    q.player.location.getNearbyEntitiesByType(ItemDisplay::class.java, bonfire.config.effectRadius).firstOrNull {
        it.isBonfire && it.uniqueId == q.effect.uuid
    }?.let {
        q.player.location.world.spawnParticle(
            listOf(Particle.SOUL, Particle.SOUL_FIRE_FLAME).random(),
            q.player.location, 1, 0.5, 1.0, 0.5, 0.0
        )

        q.player.saturation = bonfire.config.effectStrength
        q.player.saturatedRegenRate = bonfire.config.effectRegenRate
    }
}
