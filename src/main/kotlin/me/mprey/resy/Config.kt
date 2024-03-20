package me.mprey.resy

import java.time.Duration
import java.time.LocalTime

object Config {
    const val API_VERSION: String = "v1"

    val KNOWN_POST_TIMES = mapOf(
        834 to (LocalTime.of(9, 0) to Duration.ofDays(20)), // 4 Charles
        1505 to (LocalTime.of(9, 0) to Duration.ofDays(6)), // Don Angie
        72271 to (LocalTime.of(10, 0) to Duration.ofDays(30)), // Cote
        3015 to (LocalTime.of(9, 0) to Duration.ofDays(28)), // Misi
    )
}