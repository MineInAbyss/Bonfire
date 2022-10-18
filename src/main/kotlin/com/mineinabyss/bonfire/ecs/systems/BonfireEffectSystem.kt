package com.mineinabyss.bonfire.ecs.systems

import com.mineinabyss.bonfire.config.bonfireConfig
import com.mineinabyss.bonfire.ecs.components.BonfireEffectArea
import com.mineinabyss.bonfire.extensions.isBonfireModel
import com.mineinabyss.geary.annotations.AutoScan
import com.mineinabyss.geary.systems.RepeatingSystem
import com.mineinabyss.geary.systems.accessors.TargetScope
import com.mineinabyss.idofront.time.ticks
import org.bukkit.Particle
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player

@AutoScan
class BonfireEffectSystem : RepeatingSystem(1.ticks) {
    private val TargetScope.player by get<Player>()
    private val TargetScope.effect by get<BonfireEffectArea>()

    override fun TargetScope.tick() {
        // Check if still near a bonfire
        player.location.getNearbyLivingEntities(bonfireConfig.effectRadius).firstOrNull {
            it is ArmorStand && it.isBonfireModel() && it.uniqueId == effect.uuid
        }?.let {
            player.location.world.spawnParticle(
                listOf(Particle.SOUL, Particle.SOUL_FIRE_FLAME).random(),
                player.location, 1, 0.5, 1.0, 0.5, 0.0
            )

            player.saturation = bonfireConfig.effectStrength
            player.saturatedRegenRate = bonfireConfig.effectRegenRate
        }
    }
}
