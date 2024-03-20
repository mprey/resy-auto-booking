package me.mprey.resy.controllers

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import me.mprey.resy.models.ReservationRequest
import me.mprey.resy.services.NotificationService
import me.mprey.resy.services.ResyService
import mu.KotlinLogging
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ReservationController(
    private val resyService: ResyService,
    private val notificationService: NotificationService,
) {
    private val logger = KotlinLogging.logger {  }

    companion object {
        private val MAX_DURATION = Duration.ofMinutes(3).toMillis()
        private const val REQUESTS_PER_ATTEMPT = 2

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/YY")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    }

    /**
     * Execute a reservation request previously scheduled.
     *
     * The controller will first spawn `REQUESTS_PER_ATTEMPT` threads sending a request
     * for reservation times on a given date. The controller will return the first non-empty
     * result.
     *
     * Once the times are requested, the controller will begin requesting a reservation
     * sequentially by order of priority. Each time gets only one request.
     *
     * If a reservation is found during this, we will exit early and notify the user.
     *
     * If a reservation is not found, we will cycle ot most `MAX_DURATION` milliseconds or until a reservation is found.
     */
    fun executeReservation(reservationRequest: ReservationRequest) {
        val targetDate = LocalDate.now(reservationRequest.timeZone.toZoneId()).plusDays(
            reservationRequest.reservationPostDaysOffset.toLong()
        )
        val reservation = runExecuteReservation(reservationRequest, targetDate)

        if (reservation == null) {
            notificationService.sendNotification(
                "Sorry, ${reservationRequest.submittedBy}! I was unable to find a reservation " +
                        "for ${reservationRequest.name} on ${targetDate.format(DATE_FORMAT)}. " +
                        "Someone wrote better code than me I guess. Well, at least you can try again!"
            )
        } else {
            notificationService.sendNotification(
                "Hey, ${reservationRequest.submittedBy}, we did it! We found a reservation for ${reservationRequest.name} " +
                        "Your reservation is on ${targetDate.format(DATE_FORMAT)} at ${reservation.dateTime.format(TIME_FORMAT)} " +
                        "with a table type of ${reservation.tableType} for ${reservation.partySize} people. Go say thanks to Mason!"
            )
        }
    }

    private fun runExecuteReservation(reservationRequest: ReservationRequest, targetDate: LocalDate): ResyService.Booking? {
        val currentTime = System.currentTimeMillis()
        while ((System.currentTimeMillis() - currentTime) < MAX_DURATION) {
            logger.info("Execution reservation attempt for date {}: {}", targetDate, reservationRequest)
            logger.info("Current date time: {}", LocalDateTime.now())
            val result = executeReservationAttempt(reservationRequest, targetDate)
            if (result != null) {
                return result
            }
        }
        return null
    }

    private fun executeReservationAttempt(reservationRequest: ReservationRequest, targetDate: LocalDate): ResyService.Booking? {
        val availableTimes = requestReservationTimes(reservationRequest, targetDate)
        if (availableTimes.isNullOrEmpty()) {
            println("Null");
            return null
        }

        for (req in reservationRequest.requestedTimes) {
            val match = availableTimes.firstOrNull {
                it.dateTime.hour == req.time.hour &&
                        it.dateTime.minute == req.time.minute &&
                        (req.tableType?.equals(it.tableType, ignoreCase = true) ?: true)
            } ?: continue

            val result = runBlocking {
                resyService.getReservationDetails(match)?.let {
                    resyService.bookReservation(it)
                } ?: false
            }

            if (result) {
                return match
            }
        }

        return null
    }

    private fun requestReservationTimes(reservationRequest: ReservationRequest, targetDate: LocalDate): List<ResyService.Booking>? {
        return runBlocking {
            select {
                for (i in 0..REQUESTS_PER_ATTEMPT) {
                    async {
                        resyService.getReservations(reservationRequest.venueId, targetDate, reservationRequest.numSeats)
                    }.onAwait { it }
                }
            }
        }
    }
}