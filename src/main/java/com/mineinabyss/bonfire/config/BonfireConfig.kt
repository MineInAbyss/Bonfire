package com.mineinabyss.bonfire.config

import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.bonfire.components.SoundEffect
import com.mineinabyss.idofront.config.IdofrontConfig
import com.mineinabyss.idofront.config.ReloadScope
import com.mineinabyss.idofront.recpies.register
import com.mineinabyss.idofront.serialization.SerializableRecipe
import com.mineinabyss.idofront.time.TimeSpan
import com.mineinabyss.idofront.time.days
import com.mineinabyss.idofront.time.weeks
import kotlinx.serialization.Serializable

object BonfireConfig : IdofrontConfig<BonfireConfig.Data>(bonfirePlugin, Data.serializer()) {
    @Serializable
    data class Data(
        var bonfireRecipe: SerializableRecipe,
        var maxPlayerCount: Int = 4,
        var bonfireExpirationTime: TimeSpan = 1.weeks,
        var expirationCheckInterval: TimeSpan = 1.days,
        var respawnSetSound : SoundEffect,
        var respawnUnsetSound : SoundEffect,
        var minFallDist: Int,
    )

    override fun ReloadScope.load() {
        "Registering bonfire recipe" {
            data.bonfireRecipe.toCraftingRecipe().register()
        }
    }
}