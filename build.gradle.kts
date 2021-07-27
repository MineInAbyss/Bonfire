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
//    maven("https://nexus.okkero.com/repository/maven-releases")
    maven("https://repo.dmulloy2.net/repository/public") // Protocol Lib
}

dependencies {
    // Kotlin spice dependencies
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json")
//    compileOnly("com.github.okkero:skedule")

    // Shaded
    implementation("com.mineinabyss:idofront:1.17.1-0.6.22")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion") { isTransitive = false }
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion") { isTransitive = false }
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion") { isTransitive = false }
    // Sqlite
    compileOnly("org.xerial:sqlite-jdbc:3.30.1")

    //Yaml
    compileOnly("com.charleskorn.kaml:kaml")

    // Plugin dependencies
    compileOnly ("com.mineinabyss:geary-platform-papermc:0.6.48")
    compileOnly ("com.comphenix.protocol:ProtocolLib:4.7.0")

}

//tasks.shadowJar {
//    relocate("com.mineinabyss.idofront", "${project.group}.${project.name}.idofront".toLowerCase())
//    minimize()
//}
