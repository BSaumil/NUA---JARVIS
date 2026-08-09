package com.nua.assistant.smarthome

sealed class SmartHomeResult {
    data class Success(val message: String) : SmartHomeResult()
    data class NotConfigured(val reason: String) : SmartHomeResult()
    data class Failure(val message: String) : SmartHomeResult()
}

/**
 * Extension point for Tier 1 smart-home control (Matter / Google Home APIs). No
 * implementation backed by a real cloud project ships in this repo — Google's Home
 * APIs are limited-access and require a provisioned Google Cloud project plus real
 * device commissioning (structures, rooms, linked devices) that has to happen outside
 * the app, in the Google Home app/console. UnconfiguredSmartHomeRepository is what's
 * actually wired up today; swap the Hilt binding in SmartHomeModule.kt for a real
 * implementation once that setup exists.
 */
interface SmartHomeRepository {
    suspend fun controlDevice(deviceName: String, action: String): SmartHomeResult
}
