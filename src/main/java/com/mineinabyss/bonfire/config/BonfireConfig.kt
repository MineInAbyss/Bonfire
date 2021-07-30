package com.mineinabyss.bonfire.config

import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.idofront.config.IdofrontConfig
import com.mineinabyss.idofront.config.ReloadScope
import com.mineinabyss.idofront.recpies.register
import com.mineinabyss.idofront.serialization.SerializableRecipe
import kotlinx.serialization.Serializable

object BonfireConfig : IdofrontConfig<BonfireConfig.Data>(bonfirePlugin, Data.serializer()) {
    @Serializable
    data class Data(
        var bonfireRecipe: SerializableRecipe,
        var maxPlayerCount: Int = 4,
    )

    override fun ReloadScope.load() {
        "Registering bonfire recipe" {
            data.bonfireRecipe.toCraftingRecipe().register()
        }
    }
}