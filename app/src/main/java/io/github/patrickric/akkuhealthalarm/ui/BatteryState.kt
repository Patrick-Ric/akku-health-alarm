package io.github.patrickric.akkuhealthalarm.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.patrickric.akkuhealthalarm.data.BatteryReading
import kotlinx.coroutines.delay

/** Live battery values while the app is visible (current changes too fast for broadcasts). */
@Composable
fun rememberBatteryReading(): MutableState<BatteryReading?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(BatteryReading.current(context)) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                BatteryReading.current(context)?.let { state.value = it }
                delay(2_000)
            }
        }
    }
    return state
}

data class SetupState(val needsNotifications: Boolean, val needsBatteryOptimization: Boolean) {
    val complete get() = !needsNotifications && !needsBatteryOptimization
}

fun readSetupState(context: Context): SetupState {
    val notifications = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return SetupState(notifications, !pm.isIgnoringBatteryOptimizations(context.packageName))
}

/** Re-checked every time the app comes back, e.g. from the system settings. */
@Composable
fun rememberSetupState(): Pair<SetupState, () -> Unit> {
    val context = LocalContext.current
    var state by remember { mutableStateOf(readSetupState(context)) }
    LifecycleResumeEffect(Unit) {
        state = readSetupState(context)
        onPauseOrDispose { }
    }
    return state to { state = readSetupState(context) }
}
