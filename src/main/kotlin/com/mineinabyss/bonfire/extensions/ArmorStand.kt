package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.config.BonfireConfig
import com.mineinabyss.bonfire.ecs.components.BonfireModel
import com.mineinabyss.geary.papermc.store.encode
import com.mineinabyss.geary.papermc.store.has
import org.bukkit.entity.ArmorStand


fun ArmorStand.setBonfireModel() = persistentDataContainer.encode(BonfireModel())
fun ArmorStand.isBonfireModel(): Boolean = persistentDataContainer.has<BonfireModel>()

fun ArmorStand.setDefaults(): ArmorStand {
    this.setGravity(false)
    this.isInvulnerable = true
    this.isInvisible = true
    this.isPersistent = true
    this.isSmall = true
    this.isMarker = true
    this.setBonfireModel()
    this.equipment.helmet = BonfireConfig.data.modelItem.toItemStack()

    return this
}
