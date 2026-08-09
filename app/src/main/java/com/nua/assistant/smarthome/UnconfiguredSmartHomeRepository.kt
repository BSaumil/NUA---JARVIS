package com.nua.assistant.smarthome

import javax.inject.Inject
import javax.inject.Singleton

/** Default binding for SmartHomeRepository — always reports not-configured; see that interface's doc comment. */
@Singleton
class UnconfiguredSmartHomeRepository @Inject constructor() : SmartHomeRepository {

    override suspend fun controlDevice(deviceName: String, action: String): SmartHomeResult =
        SmartHomeResult.NotConfigured(
            "Smart home control isn't set up yet — it needs a Google Home API project " +
                "linked with real device commissioning, which happens outside the app.",
        )
}
