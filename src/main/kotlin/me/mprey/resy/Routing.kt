package me.mprey.resy

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import me.mprey.resy.di.appModule
import me.mprey.resy.managers.ReservationManager
import me.mprey.resy.models.SubmitReservationRequest
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.SLF4JLogger

fun Application.configureRouting() {

    install(Koin) {
        SLF4JLogger()
        modules(appModule)
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    val reservationManager by inject<ReservationManager>()

    routing {
        get("/") {
            call.respondText("Resy API Backend ${Config.API_VERSION}")
        }

        get("/api/${Config.API_VERSION}/pendingReservations") {
            call.respond(reservationManager.getPendingReservations())
        }

        post("api/${Config.API_VERSION}/submitReservation") {
            val request: SubmitReservationRequest = call.receive()
            call.respond(reservationManager.submitReservation(request))
        }
    }
}