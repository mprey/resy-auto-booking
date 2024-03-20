package me.mprey.resy.models

import java.time.DayOfWeek
import java.time.LocalTime
import java.util.*

data class ReservationRequest(
    val name: String,
    val venueId: Int,

    val reservationPostTime: LocalTime,
    val reservationPostDaysOffset: Int,
    val timeZone: TimeZone,

    val numSeats: Int,
    val weekday: DayOfWeek,
    val requestedTimes: List<ReservationTime>,

    val notificationNumber: String? = null,
    val submittedBy: String,
//    val submittedTime: Instant,
)

data class ReservationTime(
    val time: LocalTime,
    val tableType: String? = null,
)
