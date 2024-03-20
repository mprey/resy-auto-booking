package me.mprey.resy.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.FormUrlEncoded
import io.ktor.http.ContentType.Application.Json
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.slf4j.Logger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ResyService(
    private val apiKey: String,
    private val authToken: String,
) {
    private val logger: Logger = KotlinLogging.logger {}
    private val client = HttpClient(CIO) {
        install(UserAgent) {
            agent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        }

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        defaultRequest {
            url("https://api.resy.com")
            header(HttpHeaders.Authorization, """ResyAPI api_key="$apiKey"""")
            header("X-Resy-Auth-Token", authToken)
            header("X-Origin", "https://widgets.resy.com")
            header(HttpHeaders.Origin, "https://widgets.resy.com")
            header(HttpHeaders.Referrer, "https://widgets.resy.com")
        }
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ISO_DATE
        private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
    }

    @Serializable
    private data class SlotDate(val start: String)
    @Serializable
    private data class SlotConfig(val token: String, val type: String)
    @Serializable
    private data class ReservationVenueSlot(val config: SlotConfig, val date: SlotDate)
    @Serializable
    private data class ReservationVenue(val slots: List<ReservationVenueSlot>)
    @Serializable
    private data class GetReservationResult(val venues: List<ReservationVenue>)
    @Serializable
    private data class GetReservationsResponse(val results: GetReservationResult)

    data class Booking(val dateTime: LocalDateTime, val tableType: String, val token: String, val partySize: Int)

    suspend fun getReservations(venueId: Int, date: LocalDate, partySize: Int): List<Booking>? {
        logger.debug("Finding reservations for {} on {}", venueId, date)
        val response = this.client.get {
            url {
                appendPathSegments("4", "find")
                parameter("lat", 0.toString())
                parameter("long", 0.toString())
                parameter("day", date.format(DATE_FORMAT))
                parameter("party_size", partySize.toString())
                parameter("venue_id", venueId.toString())
            }

            contentType(Json)
        }

        if (response.status.value !in 200..299) {
            logger.error("Invalid response from Resy: {}", response.bodyAsText())
            return null
        }

        val parsed: GetReservationsResponse = response.body()

        return parsed.results.venues.flatMap {
            it.slots.map {
                Booking(
                    dateTime = LocalDateTime.parse(it.date.start, DATE_TIME_FORMAT),
                    tableType = it.config.type,
                    token = it.config.token,
                    partySize = partySize
                )
            }
        }
    }

    @Serializable
    data class ReservationPaymentMethod(val id: Int)
    @Serializable
    data class ReservationUser(val payment_methods: List<ReservationPaymentMethod>)
    @Serializable
    data class ReservationBookToken(val value: String)
    @Serializable
    data class GetReservationDetailsResponse(val book_token: ReservationBookToken, val user: ReservationUser)

    data class BookingDetail(val bookToken: String, val paymentId: Int)

    suspend fun getReservationDetails(booking: Booking): BookingDetail? {
        logger.debug("Finding reservation details for {}", booking)
        val response = this.client.get {
            url {
                appendPathSegments("3", "details")
                parameter("commit", 1.toString())
                parameter("config_id", booking.token)
                parameter("day", booking.dateTime.format(DATE_FORMAT))
                parameter("party_size", booking.partySize.toString())
            }

            contentType(Json)
        }

        if (response.status.value !in 200..299) {
            logger.error("Invalid response from Resy: {}", response.bodyAsText())
            return null
        }

        val parsed: GetReservationDetailsResponse = response.body()
        return BookingDetail(parsed.book_token.value, parsed.user.payment_methods.first().id)
    }

    @Serializable
    data class BookReservationResponse(val resy_token: String)

    suspend fun bookReservation(bookingDetail: BookingDetail): Boolean {
        logger.debug("Booking reservation: {}", bookingDetail)

        val response = this.client.submitForm {
            url {
                appendPathSegments("3", "book")
            }

            setBody(MultiPartFormDataContent(formData {
                append("book_token", bookingDetail.bookToken)
                append("struct_payment_method", """{"id":${bookingDetail.paymentId}}""")
            }))

            contentType(FormUrlEncoded)
        }

        if (response.status.value !in 200..299) {
            logger.error("Error while booking reservation: {}", response.bodyAsText())
            return false
        }

        val parsed: BookReservationResponse = response.body()
        return parsed.resy_token.isNotEmpty()
    }

    @Serializable
    data class FindRestaurantGeo(
        val latitude: Double = 40.712941,
        val longitude: Double = -74.006393,
        val radius: Int = 35420,
    )
    @Serializable
    data class FindRestaurantSlot(
        val day: String,
        val party_size: Int,
    )
    @Serializable
    data class FindRestaurantRequest(
        val availability: Boolean = true,
        val geo: FindRestaurantGeo = FindRestaurantGeo(), // TODO: allow this to be customizable?
        val order_by: String = "availability",
        val page: Int = 1,
        val per_page: Int = 20,
        val query: String,
        val slot_filter: FindRestaurantSlot,
        val types: List<String> = listOf("venue"),
    )

    @Serializable
    data class FindRestaurantHit(val name: String, val objectID: String)
    @Serializable
    data class FindRestaurantSearch(val hits: List<FindRestaurantHit>)
    @Serializable
    data class FindRestaurantResponse(val search: FindRestaurantSearch)

    data class Restaurant(
        val venueId: Int,
        val name: String,
    )

    suspend fun findRestaurant(name: String, partySize: Int): Restaurant? {
        logger.debug("Finding restaurant: {}", name)

        val response = this.client.post {
            url {
                appendPathSegments("3", "venuesearch", "search")
            }

            contentType(Json)

            setBody(
                FindRestaurantRequest(
                    query = name,
                    slot_filter = FindRestaurantSlot(
                        party_size = partySize,
                        day = LocalDate.now().format(DATE_FORMAT),
                    )
                )
            )
        }

        if (response.status.value !in 200..299) {
            logger.error("Error while finding reservation: {}", response.bodyAsText())
            return null
        }

        val parsed: FindRestaurantResponse = response.body()
        return parsed.search.hits.firstOrNull()?.let {
            Restaurant(
                name = it.name,
                venueId = it.objectID.toInt()
            )
        }
    }
}