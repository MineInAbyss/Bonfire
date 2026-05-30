package com.mineinabyss.bonfire.systems

import com.mineinabyss.bonfire.BonfireConfig
import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.components.BonfireEffectArea
import com.mineinabyss.bonfire.extensions.isBonfire
import com.mineinabyss.dependencies.get
import com.mineinabyss.geary.modules.WorldScoped
import com.mineinabyss.geary.systems.query.query
import com.mineinabyss.idofront.time.ticks
import org.bukkit.Particle
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.Player

fun WorldScoped.bonfireEffectSystem(config: BonfireConfig = get()) = system(query<Player, BonfireEffectArea>())
    .every(10.ticks)
    .exec { (player, effect) ->
        // Check if still near a bonfire
        player.location.getNearbyEntitiesByType(ItemDisplay::class.java, config.effectRadius).firstOrNull {
            it.isBonfire && it.uniqueId == effect.uuid
        }?.let {
            player.location.world.spawnParticle(
                listOf(Particle.SOUL, Particle.SOUL_FIRE_FLAME).random(),
                player.location, 1, 0.5, 1.0, 0.5, 0.0
            )

            player.saturation = config.effectStrength
            player.saturatedRegenRate = config.effectRegenRate
        }
    }
