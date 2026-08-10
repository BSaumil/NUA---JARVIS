package com.nua.assistant.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class NuaCarSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen = NuaCarScreen(carContext)
}
