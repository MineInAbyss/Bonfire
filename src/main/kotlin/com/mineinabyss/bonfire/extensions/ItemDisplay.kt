package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.bonfire
import com.mineinabyss.bonfire.ecs.components.BonfireModel
import com.mineinabyss.geary.papermc.datastore.has
import com.mineinabyss.geary.papermc.tracking.entities.gearyMobs
import com.mineinabyss.geary.papermc.tracking.entities.toGeary
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.ItemDisplay


fun ItemDisplay.setBonfireModel() = gearyMobs.bukkit2Geary.getOrCreate(this).setPersisting(BonfireModel())
fun Entity.isBonfireModel(): Boolean = this is ItemDisplay && toGeary().has<BonfireModel>()
fun Entity.isOldBonfireModel(): Boolean = this is ArmorStand && persistentDataContainer.has<BonfireModel>()

fun ItemDisplay.setDefaults() {
    this.isInvulnerable = true
    this.isPersistent = true
    this.setBonfireModel()
    this.itemStack = bonfire.config.modelItem.toItemStack()
}
