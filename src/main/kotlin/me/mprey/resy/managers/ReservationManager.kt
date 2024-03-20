package me.mprey.resy.managers

import me.mprey.resy.controllers.ReservationController
import me.mprey.resy.models.*
import me.mprey.resy.services.NotificationService
import me.mprey.resy.services.ResyService
import mu.KotlinLogging
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

class ReservationManager(
    private val resyService: ResyService,
    private val reservationController: ReservationController,
    private val notificationService: NotificationService,
) {
    private val pendingReservations: MutableList<ReservationRequest> = mutableListOf()
    private val logger = KotlinLogging.logger {  }

    companion object {
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/YY")
    }

    suspend fun submitReservation(request: SubmitReservationRequest): SubmitReservationResponse {
        logger.info("Got a new reservation request: {}", request)
        val restaurant = resyService.findRestaurant(request.name, request.numSeats)
            ?: return error("Unable to find restaurant named ${request.name}")

        val reservationPostTime = request.reservationPostTime.toLocalTime()
            ?: return error("Unable to parse time ${request.reservationPostTime}")

        if (request.reservationPostDaysOffset < 0) {
            return error("Day offset must be greater than 0")
        }

        val timeZone = request.timeZoneString.toTimeZone() ?: return error("Invalid time zone: ${request.timeZoneString}")

        val weekday = request.weekdayString.toWeekday() ?: return error("Invalid weekday: ${request.weekdayString}")

        if (request.numSeats <= 0) {
            return error("Number of seats must be > 0")
        }

        if (pendingReservations.any { it.venueId == restaurant.venueId && it.weekday == weekday }) {
            return error("There's already pending reservations for this restaurant on this weekday. Consult who submitted that.")
        }

        val reservationTimes = request.requestedTimes.map {
            val time = it.time.toLocalTime() ?: return error("Invalid time: ${it.time}")

            ReservationTime(time = time, tableType = it.tableType)
        }

        val reservationRequest = ReservationRequest(
            name = restaurant.name,
            venueId = restaurant.venueId,
            reservationPostTime = reservationPostTime,
            reservationPostDaysOffset = request.reservationPostDaysOffset,
            timeZone = timeZone,
            numSeats = request.numSeats,
            weekday = weekday,
            requestedTimes = reservationTimes,
            notificationNumber = request.notificationNumber,
            submittedBy = request.submittedBy,
        )

        this.pendingReservations.add(reservationRequest)
        this.startTimer(reservationRequest)

        return SubmitReservationResponse(success = true)
    }

    fun getPendingReservations(): PendingReservationsResponse {
        logger.info("Fetching pending reservations...")
        return PendingReservationsResponse(
            reservations = this.pendingReservations.associate {
                it.name to PendingReservationResponse(
                    numSeats = it.numSeats,
                    weekday = it.weekday.name,
                    submittedBy = it.submittedBy,
                    times = it.requestedTimes.mapIndexed { idx, obj ->
                        PendingReservationTimeResponse(
                            time = obj.time.format(TIME_FORMATTER),
                            priority = idx + 1,
                            tableType = obj.tableType
                        )
                    }
                )
            }
        )
    }

    private fun startTimer(reservationRequest: ReservationRequest) {
        var currentDateTime = LocalDateTime.of(
            LocalDate.now(reservationRequest.timeZone.toZoneId()),
            reservationRequest.reservationPostTime,
        )

        if (reservationRequest.reservationPostTime < LocalTime.now(reservationRequest.timeZone.toZoneId())) {
            currentDateTime = currentDateTime.plusDays(1)
        }

        var minimumReservationDate = currentDateTime.plusDays(reservationRequest.reservationPostDaysOffset.toLong())
        var attemptDateTime = currentDateTime

        while (minimumReservationDate.dayOfWeek != reservationRequest.weekday) {
            minimumReservationDate = minimumReservationDate.plusDays(1)
            attemptDateTime = attemptDateTime.plusDays(1)
        }

        // Spawn the thread 1 minute ahead since sometimes reservations post early
        val zoneDateTime = attemptDateTime.atZone(reservationRequest.timeZone.toZoneId()).minusMinutes(1)
        val timerDate = Date.from(zoneDateTime.toInstant())

        logger.info("Scheduling reservation request for: {}", zoneDateTime)
        notificationService.sendNotification(
            "Hey, ${reservationRequest.submittedBy}. We've got your request for a reservation at ${reservationRequest.name}. " +
                    "The earliest date for a reservation is at ${minimumReservationDate.format(DATE_FORMATTER)}. We'll attempt to " +
                    "get the reservation on ${zoneDateTime.format(DATE_FORMATTER)} at time ${zoneDateTime.format(TIME_FORMATTER)} " +
                    zoneDateTime.zone.id
        )

        val timer = Timer(true) // enforce daemon to not halt program
        timer.schedule(object : TimerTask() {
            override fun run() {
                pendingReservations.remove(reservationRequest)
                reservationController.executeReservation(reservationRequest)
            }
        }, timerDate)
    }

    private fun String.toWeekday(): DayOfWeek? {
        return DayOfWeek.entries.firstOrNull { it.name.lowercase() == this.lowercase() }
    }

    private fun String.toLocalTime(): LocalTime? {
        return try {
            LocalTime.parse(this, TIME_FORMATTER)
        } catch (ex: DateTimeParseException) {
            return null
        }
    }

    private fun String.toTimeZone(): TimeZone? {
        val timeZone = TimeZone.getTimeZone(this)
        return timeZone.takeIf { it != TimeZone.getTimeZone("GMT") } // Ignore fallback time zone
    }

    private fun error(message: String) = SubmitReservationResponse(
        error = message,
        success = false,
    )
}