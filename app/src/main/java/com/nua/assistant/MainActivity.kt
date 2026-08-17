package com.nua.assistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nua.assistant.services.ACTION_WAKE_WORD_DETECTED
import com.nua.assistant.services.EXTRA_WAKE_PHRASE_ID
import com.nua.assistant.ui.NuaScreen
import com.nua.assistant.ui.NuaTheme
import com.nua.assistant.ui.NuaViewModel
import dagger.hilt.android.AndroidEntryPoint

/** FragmentActivity (not plain ComponentActivity) because BiometricPrompt (security/BiometricGate.kt) requires one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: NuaViewModel by viewModels()

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.onWakeWordDetected(intent?.getStringExtra(EXTRA_WAKE_PHRASE_ID))
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* handled implicitly: features check permission state themselves before acting */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()

        setContent {
            NuaTheme {
                NuaScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ACTION_WAKE_WORD_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(wakeWordReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(wakeWordReceiver)
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        val notYetGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notYetGranted.isNotEmpty()) {
            permissionLauncher.launch(notYetGranted.toTypedArray())
        }
    }
}
