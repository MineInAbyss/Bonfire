@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.mia.kotlin.jvm)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.mia.papermc)
    alias(libs.plugins.mia.copyjar)
    alias(libs.plugins.mia.nms)
    alias(libs.plugins.mia.publication)
    alias(libs.plugins.mia.autoversion)
}

repositories {
    mavenCentral()
    maven("https://repo.mineinabyss.com/releases")
    maven("https://repo.mineinabyss.com/snapshots")
    maven("https://repo.dmulloy2.net/repository/public") // ProtocolLib
}

dependencies {
    // MineInAbyss platform
    compileOnly(libs.bundles.idofront.core)
    compileOnly(libs.idofront.nms)
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlinx.serialization.kaml)
    compileOnly(libs.kotlinx.serialization.cbor)
    compileOnly(libs.minecraft.mccoroutine)

    // Geary platform
    compileOnly(bfLibs.geary.papermc)

    // Other plugins
    compileOnly(bfLibs.blocky)
    compileOnly(libs.minecraft.plugin.protocollib)
    compileOnly(bfLibs.protocolburrito)
}
