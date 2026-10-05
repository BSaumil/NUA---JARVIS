package com.nua.assistant.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.nua.assistant.BuildConfig

/**
 * Android Auto entry point (Tier 1: the official Car App Library, not automation).
 * Only tested against the library's API surface, never on a real head unit or the
 * Desktop Head Unit emulator — see README's "Known gaps"; that device-level
 * verification is still blocked in this environment (no `adb`/`emulator`/Android SDK
 * tooling at all — confirmed unchanged from the prior session's own investigation).
 *
 * [createHostValidator] is the one real, code-level correctness gap that doesn't need a
 * device to fix: debug builds (the Desktop Head Unit signs with a debug/test
 * certificate that would never appear in a production allowlist) keep
 * [HostValidator.ALLOW_ALL_HOSTS_VALIDATOR], while a real release build uses the Car App
 * Library's own bundled, Google-maintained allowlist
 * ([androidx.car.app.R.array.hosts_allowlist_sample] — not a certificate NUA hand-rolls
 * itself, since getting that wrong would either silently lock out the real Android Auto
 * host or silently admit an impostor, both worse than the library's own maintained
 * list) covering the genuine Android Auto and Android Automotive OS hosts.
 */
class NuaCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(): Session = NuaCarSession()
}
