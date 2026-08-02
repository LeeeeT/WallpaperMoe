package dev.leeeet.wallpapermoe.source

import kotlinx.serialization.Serializable

/** Selects members uniformly at random or visits each member in stored order. */
@Serializable
enum class SelectionMode { RANDOM, ROUND_ROBIN }
