package com.nua.assistant.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template

/**
 * Minimal driving-safe surface: NUA still listens for its wake words in the
 * background (see services/NuaForegroundService.kt) exactly as it does off the road,
 * so there's no separate car-specific voice trigger here — this screen is just status
 * text, deliberately not a chat UI or anything requiring visual attention while driving.
 */
class NuaCarScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template =
        MessageTemplate.Builder("Say a wake word — \"Hey NUA\" and others — to talk to NUA.")
            .setTitle("NUA")
            .setHeaderAction(Action.APP_ICON)
            .build()
}
