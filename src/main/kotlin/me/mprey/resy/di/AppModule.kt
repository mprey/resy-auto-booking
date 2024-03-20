package me.mprey.resy.di

import io.github.cdimascio.dotenv.dotenv
import me.mprey.resy.controllers.ReservationController
import me.mprey.resy.managers.ReservationManager
import me.mprey.resy.services.NotificationService
import me.mprey.resy.services.ResyService
import org.koin.dsl.module

val appModule = module {
    val dotenv = dotenv()
    single { ResyService(dotenv["API_KEY"], dotenv["AUTH_TOKEN"]) }
    single { NotificationService() }

    single { ReservationController(get(), get()) }
    single { ReservationManager(get(), get(), get()) }
}
