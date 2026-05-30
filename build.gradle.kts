import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
import net.minecrell.pluginyml.paper.PaperPluginDescription.RelativeLoadOrder.BEFORE

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(miaLibs.plugins.mia.kotlin.jvm)
    alias(miaLibs.plugins.kotlinx.serialization)
    alias(miaLibs.plugins.mia.papermc)
    alias(miaLibs.plugins.mia.copyjar)
    alias(miaLibs.plugins.mia.nms)
    alias(miaLibs.plugins.mia.publication)
    alias(miaLibs.plugins.mia.autoversion)
}

repositories {
    mavenCentral()
    maven("https://repo.mineinabyss.com/releases")
    maven("https://repo.mineinabyss.com/snapshots")
    maven("https://repo.dmulloy2.net/repository/public") // ProtocolLib
}

dependencies {
    // MineInAbyss platform
    compileOnly(miaLibs.bundles.idofront.core)
    compileOnly(miaLibs.idofront.nms)
    compileOnly(miaLibs.kotlinx.serialization.json)
    compileOnly(miaLibs.kotlinx.serialization.kaml)
    compileOnly(miaLibs.kotlinx.serialization.cbor)
    compileOnly(miaLibs.minecraft.mccoroutine)
    compileOnly(miaLibs.geary.papermc)

    // Other plugins
    compileOnly(libs.blocky)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=kotlin.ExperimentalUnsignedTypes",
        )
    }
}

paper {
    main = "com.mineinabyss.bonfire.BonfirePlugin"
    name = "Bonfire"
    apiVersion = "1.21"
    authors = listOf("boy0000", "Scyu_", "Norazan", "Ru_Kira")

    permissions {
        register("bonfire.*") {
            description = "Gives access to all commands and allows staff to remove bonfires"
            children = listOf("bonfire.remove")
        }
        register("bonfire.remove") {
            description = "Allow staff to remove bonfires."
            default = OP
        }
    }

    serverDependencies {
        register("Geary") {
            load = BEFORE
            joinClasspath = true
        }
        register("Blocky") {
            load = BEFORE
            joinClasspath = true
        }
        register("DeeperWorld") {
            load = BEFORE
            joinClasspath = true
            required = false
        }
        register("AxiomPaper") {
            load = BEFORE
            joinClasspath = true
            required = false
        }
    }
}