package com.nua.assistant.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Android Auto entry point (Tier 1: the official Car App Library, not automation).
 * Only tested against the library's API surface, never on a real head unit or the
 * Desktop Head Unit emulator — see README's "Known gaps". ALLOW_ALL_HOSTS_VALIDATOR is
 * fine for local testing but should be tightened to a real allowlist before any
 * production build.
 */
class NuaCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = NuaCarSession()
}
