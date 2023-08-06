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
    maven("https://jitpack.io")
    maven("https://repo.codemc.io/repository/maven-public/") // OfflineManager
    mavenLocal()
}

dependencies {
    // MineInAbyss platform
    compileOnly(libs.sqlite.jdbc) { isTransitive = false }
    compileOnly(libs.exposed.core) { isTransitive = false }
    compileOnly(libs.exposed.dao) { isTransitive = false }
    compileOnly(libs.exposed.jdbc) { isTransitive = false }
    compileOnly(libs.exposed.javatime) { isTransitive = false }

    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlinx.serialization.kaml)
    compileOnly(libs.minecraft.mccoroutine)

    // Geary platform
    compileOnly(bfLibs.geary.papermc)

    // Other plugins
    compileOnly(bfLibs.deeperworld)
    compileOnly(bfLibs.blocky)
    compileOnly(libs.minecraft.plugin.protocollib)
    compileOnly(bfLibs.protocolburrito)
    compileOnly(bfLibs.offlineManager)

    // Shaded
    implementation(libs.bundles.idofront.core)
    implementation(libs.idofront.nms)
}
