package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.systems.bonfireEffectSystem
import com.mineinabyss.dependencies.module
import com.mineinabyss.geary.autoscan.autoscan
import com.mineinabyss.geary.papermc.gearyWorld
import com.mineinabyss.idofront.features.mainCommand

val BonfireAddon = module("bonfire") {
    gearyWorld {
        bonfireEffectSystem()
    }
}.mainCommand {
    bonfireCommands()
}
