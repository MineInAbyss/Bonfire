val exposedVersion: String by project

plugins {
    id("com.mineinabyss.conventions.kotlin")
    kotlin("plugin.serialization")
    id("com.mineinabyss.conventions.papermc")
    id("com.mineinabyss.conventions.publication")
}

repositories {
    mavenCentral()
    maven("https://repo.mineinabyss.com/releases")
    maven("https://repo.dmulloy2.net/repository/public") // Protocol Lib
    //maven("https://jitpack.io")
}

dependencies {
    // Kotlin spice dependencies
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json")
    compileOnly("com.charleskorn.kaml:kaml")

    // Shaded
    implementation("com.mineinabyss:idofront:1.17.1-0.6.23")
    //implementation("com.github.okkero:skedule")

    // Database
    slim("org.jetbrains.exposed:exposed-core:$exposedVersion") { isTransitive = false }
    slim("org.jetbrains.exposed:exposed-dao:$exposedVersion") { isTransitive = false }
    slim("org.jetbrains.exposed:exposed-jdbc:$exposedVersion") { isTransitive = false }
    slim("org.jetbrains.exposed:exposed-java-time:$exposedVersion") {isTransitive = false }

    // Sqlite
    slim("org.xerial:sqlite-jdbc:3.30.1")

    // Plugin dependencies
    compileOnly ("com.mineinabyss:geary-platform-papermc:0.6.48")
    compileOnly ("com.comphenix.protocol:ProtocolLib:4.7.0")
}

//tasks.shadowJar {
//    relocate("com.mineinabyss.idofront", "${project.group}.${project.name}.idofront".toLowerCase())
//    minimize()
//}
