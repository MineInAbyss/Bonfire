val idofrontVersion: String by project
val deeperworldVersion: String by project
val gearyPlatformVersion: String by project

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
    compileOnly(libs.minecraft.skedule)

    // Geary platform
    compileOnly(platform("com.mineinabyss:geary-platform:$gearyPlatformVersion"))
    compileOnly("com.mineinabyss:geary-papermc-core")
    compileOnly("com.mineinabyss:looty")

    // Other plugins
    compileOnly("com.mineinabyss:deeperworld:$deeperworldVersion")

    // Shaded
    implementation("com.mineinabyss:idofront:$idofrontVersion")
}

tasks.shadowJar {
    minimize()
}
