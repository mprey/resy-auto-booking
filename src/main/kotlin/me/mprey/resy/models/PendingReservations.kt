package me.mprey.resy.models

import kotlinx.serialization.Serializable

@Serializable
data class PendingReservationsResponse(
    val reservations: Map<String, PendingReservationResponse>
)

@Serializable
data class PendingReservationResponse(
    val numSeats: Int,
    val weekday: String,
    val submittedBy: String,
    val times: List<PendingReservationTimeResponse>,
)

@Serializable
data class PendingReservationTimeResponse(
    val time: String,
    val priority: Int,
    val tableType: String? = null,
)