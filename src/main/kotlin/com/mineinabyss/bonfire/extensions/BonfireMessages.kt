package com.mineinabyss.bonfire.extensions

import kotlinx.serialization.Serializable

@Serializable
data class BonfireMessages(
    val BONFIRE_REMOVED: String = "<red>Your respawn point was unset because the bonfire was broken by the owner",
    val BONFIRE_BREAK_DENIED: String = "<red>You cannot break this bonfire, unkindled one",
    val BONFIRE_BREAK: String = "<red>Respawn point has been removed",
    val BONFIRE_FULL: String = "<red>This bonfire is full",
    val BONFIRE_EXPIRED: String = "<red>The bonfire has expired and turned to ash",
    val BONFIRE_NOT_FOUND: String = "<red>Bonfire was not found...",
    val BONFIRE_RESPAWNING: String = "<yellow>Respawning at bonfire...",
)
