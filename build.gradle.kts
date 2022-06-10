val idofrontVersion: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.mineinabyss.conventions.kotlin")
    id("com.mineinabyss.conventions.papermc")
    id("com.mineinabyss.conventions.publication")
    id("com.mineinabyss.conventions.copyjar")
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.mineinabyss.com/releases")
    maven("https://repo.dmulloy2.net/repository/public") // Protocol Lib
    maven("https://jitpack.io")
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
    compileOnly(bflibs.geary.papermc.core)
    compileOnly(bflibs.looty)

    // Other plugins
    compileOnly(bflibs.deeperworld)

    // Shaded
    implementation(libs.idofront.core)
}

tasks.shadowJar {
    minimize()
}
