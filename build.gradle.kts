import Com_mineinabyss_conventions_platform_gradle.Deps

val idofrontVersion: String by project
val deeperworldVersion: String by project
val gearyPlatformVersion: String by project

plugins {
    id("com.mineinabyss.conventions.kotlin")
    kotlin("plugin.serialization")
    id("com.mineinabyss.conventions.papermc")
    id("com.mineinabyss.conventions.publication")
    id("com.mineinabyss.conventions.copyjar")
}

repositories {
    mavenCentral()
    maven("https://repo.mineinabyss.com/releases")
    maven("https://repo.dmulloy2.net/repository/public") // Protocol Lib
    maven("https://jitpack.io")
}

dependencies {
    // MineInAbyss platform
    compileOnly(Deps.`sqlite-jdbc`) { isTransitive = false }
    compileOnly(Deps.exposed.core) { isTransitive = false }
    compileOnly(Deps.exposed.dao) { isTransitive = false }
    compileOnly(Deps.exposed.jdbc) { isTransitive = false }
    compileOnly(Deps.exposed.`java-time`) { isTransitive = false }

    compileOnly(Deps.kotlinx.serialization.json)
    compileOnly(Deps.kotlinx.serialization.kaml)
    compileOnly(Deps.minecraft.skedule)

    // Geary platform
    compileOnly(platform("com.mineinabyss:geary-platform:$gearyPlatformVersion"))
    compileOnly("com.mineinabyss:geary-platform-papermc")
    compileOnly("com.mineinabyss:looty")

    // Other plugins
    compileOnly("com.mineinabyss:deeperworld:$deeperworldVersion")

    // Shaded
    implementation("com.mineinabyss:idofront:$idofrontVersion")
}
