package me.mprey.resy.services

import mu.KotlinLogging

class NotificationService {
    private val logger = KotlinLogging.logger {  }
    fun sendNotification(message: String) {
        logger.error(message)
    }
}