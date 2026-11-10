package io.github.patrickric.akkuhealthalarm.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the watcher after a restart of the phone and after an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            -> MonitorService.start(context)
        }
    }
}
