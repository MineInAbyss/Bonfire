package com.mineinabyss.bonfire.config

import com.mineinabyss.bonfire.bonfirePlugin
import com.mineinabyss.idofront.config.IdofrontConfig
import com.mineinabyss.idofront.config.ReloadScope
import com.mineinabyss.idofront.recpies.register
import com.mineinabyss.idofront.serialization.SerializableRecipe
import com.mineinabyss.idofront.time.TimeSpan
import kotlinx.serialization.Serializable
import org.bukkit.Bukkit

object BonfireConfig : IdofrontConfig<BonfireConfig.Data>(bonfirePlugin, Data.serializer()) {
    @Serializable
    data class Data(
        var bonfireRecipe: SerializableRecipe,
        var campfireDespawnTime: TimeSpan,
    )

    override fun ReloadScope.load() {
        "Registering bonfire recipe" {
            data.bonfireRecipe.toCraftingRecipe().register()
        }
    }
}