package me.mprey.resy.models

import kotlinx.serialization.Serializable

@Serializable
data class SubmitReservationRequest(
    val name: String,

    val reservationPostTime: String,
    val reservationPostDaysOffset: Int,
    val timeZoneString: String,

    val numSeats: Int,
    val weekdayString: String,
    val requestedTimes: List<SubmitReservationRequestTime>,

    val notificationNumber: String? = null,
    val submittedBy: String,
)

@Serializable
data class SubmitReservationRequestTime(
    val time: String,
    val tableType: String? = null,
)

@Serializable
data class SubmitReservationResponse(
    val success: Boolean,
    val error: String? = null,
)