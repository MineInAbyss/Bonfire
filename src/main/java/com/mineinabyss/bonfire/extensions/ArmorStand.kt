package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.ecs.components.BonfireModel
import com.mineinabyss.geary.minecraft.store.decode
import com.mineinabyss.geary.minecraft.store.encode
import com.mineinabyss.geary.minecraft.store.has
import org.bukkit.entity.ArmorStand


fun ArmorStand.setBonfireModel() = persistentDataContainer.encode(BonfireModel())
fun ArmorStand.bonfireData(): BonfireModel? = persistentDataContainer.decode()
fun ArmorStand.isBonfireModel(): Boolean = persistentDataContainer.has<BonfireModel>()