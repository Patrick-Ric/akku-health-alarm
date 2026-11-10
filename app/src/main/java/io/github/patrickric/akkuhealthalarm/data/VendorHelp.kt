package io.github.patrickric.akkuhealthalarm.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.patrickric.akkuhealthalarm.R

/**
 * Some makers stop apps in the background beyond what Android itself does. For them the app
 * shows extra steps and tries to open the right settings page directly.
 */
enum class Vendor(
    val label: String,
    /** Page on dontkillmyapp.com. */
    val slug: String,
    val steps: Int,
    /** Settings pages that differ between models and versions; tried one after the other. */
    val pages: List<Pair<String, String>>,
) {
    XIAOMI(
        "Xiaomi", "xiaomi", R.array.vendor_steps_xiaomi,
        listOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"),
    ),
    HUAWEI(
        "Huawei / Honor", "huawei", R.array.vendor_steps_huawei,
        listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        ),
    ),
    SAMSUNG(
        "Samsung", "samsung", R.array.vendor_steps_samsung,
        listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.sm" to "com.samsung.android.sm.battery.ui.BatteryActivity",
        ),
    ),
    OPPO(
        "Oppo / Realme", "oppo", R.array.vendor_steps_oppo,
        listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        ),
    ),
    VIVO(
        "Vivo", "vivo", R.array.vendor_steps_vivo,
        listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        ),
    ),
    ONEPLUS(
        "OnePlus", "oneplus", R.array.vendor_steps_oneplus,
        listOf("com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
    ),
    OTHER("", "", R.array.vendor_steps_other, emptyList()),
    ;

    val needsExtraSteps get() = this != OTHER
    val link get() = if (slug.isEmpty()) "https://dontkillmyapp.com" else "https://dontkillmyapp.com/$slug"

    /** Opens the maker's settings page; falls back to the app's info page. Returns false on fallback. */
    fun openSettings(context: Context): Boolean {
        // EMUI 5 (Android 7) has the start-up page too, but it closes at once; the lock-screen
        // cleanup page is the one that matters there.
        val order = if (this == HUAWEI && Build.VERSION.SDK_INT < 26) pages.reversed() else pages
        for ((pkg, cls) in order) {
            try {
                context.startActivity(Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // Not on this model or version; try the next one.
            }
        }
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return false
    }

    companion object {
        fun current(): Vendor {
            val maker = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
            return when {
                listOf("xiaomi", "redmi", "poco").any { it in maker } -> XIAOMI
                listOf("huawei", "honor").any { it in maker } -> HUAWEI
                "samsung" in maker -> SAMSUNG
                listOf("oppo", "realme").any { it in maker } -> OPPO
                listOf("vivo", "iqoo").any { it in maker } -> VIVO
                "oneplus" in maker -> ONEPLUS
                else -> OTHER
            }
        }
    }
}
