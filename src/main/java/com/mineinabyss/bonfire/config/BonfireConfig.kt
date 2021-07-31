package com.mineinabyss.bonfire.config

import com.mineinabyss.bonfire.bonfirePlugin
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
        var timeUntilCampfireDespawn: TimeSpan = 1.weeks,
        var campfireDestroyCheckInterval: TimeSpan = 1.days,
    )

    override fun ReloadScope.load() {
        "Registering bonfire recipe" {
            data.bonfireRecipe.toCraftingRecipe().register()
        }
    }
}