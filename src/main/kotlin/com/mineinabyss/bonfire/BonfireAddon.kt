package com.mineinabyss.bonfire

import com.mineinabyss.bonfire.systems.bonfireEffectSystem
import com.mineinabyss.geary.addons.dsl.createAddon
import com.mineinabyss.geary.autoscan.autoscan

val BonfireAddon = createAddon("Bonfire", configuration = {
    autoscan(BonfirePlugin::class.java.classLoader, "com.mineinabyss.bonfire") {
        all()
    }
}) {
    systems {
        bonfireEffectSystem()
    }
}