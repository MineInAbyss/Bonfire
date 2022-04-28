pluginManagement {
	repositories {
		gradlePluginPortal()
		maven("https://repo.mineinabyss.com/releases")
		maven("https://papermc.io/repo/repository/maven-public/") //Paper
	}
	plugins {
		val kotlinVersion: String by settings
		kotlin("jvm") version kotlinVersion
		kotlin("plugin.serialization") version kotlinVersion
		id("com.github.johnrengelman.shadow") version "6.0.0"
		id("io.github.0ffz.github-packages") version "1.2.0"
	}

	val idofrontVersion: String by settings
	resolutionStrategy {
		eachPlugin {
			if (requested.id.id.startsWith("com.mineinabyss.conventions"))
				useVersion(idofrontVersion)
		}
	}
}

dependencyResolutionManagement {
	val idofrontVersion: String by settings

	repositories {
		maven("https://repo.mineinabyss.com/releases")
	}

	versionCatalogs.create("libs").from("com.mineinabyss:catalog:$idofrontVersion")
}

rootProject.name = "Bonfire"
