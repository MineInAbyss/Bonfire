package com.mineinabyss.bonfire.ecs.systems

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.ecs.components.BonfireEffectArea
import com.mineinabyss.bonfire.extensions.isBonfireModel
import com.mineinabyss.geary.ecs.accessors.ResultScope
import com.mineinabyss.geary.ecs.api.autoscan.AutoScan
import com.mineinabyss.geary.ecs.api.systems.TickingSystem
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player

class BonfireEffectSystem : TickingSystem(1) {
    private val ResultScope.player by get<Player>()
    private val ResultScope.effect by get<BonfireEffectArea>()

    override fun ResultScope.tick() {
        // Check if still near a bonfire
        player.location.getNearbyLivingEntities(BonfireConfig.data.effectRadius).firstOrNull {
            it is ArmorStand && it.isBonfireModel() && it.uniqueId == effect.uuid
        }?.let {
            player.location.world.spawnParticle(
                listOf(Particle.SOUL, Particle.SOUL_FIRE_FLAME).random(),
                player.location,
                1,
                0.5,
                1.0,
                0.5,
                0.0
            )

            player.saturation = BonfireConfig.data.effectStrength
            player.saturatedRegenRate = BonfireConfig.data.effectRegenRate
        }
    }
}
