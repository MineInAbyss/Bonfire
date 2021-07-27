package com.mineinabyss.bonfire.extensions

import com.mineinabyss.bonfire.components.BonfireData
import com.mineinabyss.geary.minecraft.store.decode
import com.mineinabyss.geary.minecraft.store.encode
import com.mineinabyss.geary.minecraft.store.has
import org.bukkit.block.Campfire
import java.util.*


val Campfire.isBonfire: Boolean get() = persistentDataContainer.has<BonfireData>()
fun Campfire.isBonfire(uuid: UUID): Boolean = bonfireData()?.uuid == uuid
fun Campfire.bonfireData(): BonfireData? = persistentDataContainer.decode()
fun Campfire.makeBonfire(uuid: UUID) = persistentDataContainer.encode(BonfireData(uuid))
fun Campfire.save(data: BonfireData) = persistentDataContainer.encode(data)
