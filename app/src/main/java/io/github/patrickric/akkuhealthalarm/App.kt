package io.github.patrickric.akkuhealthalarm

import android.app.Application
import android.content.Context
import io.github.patrickric.akkuhealthalarm.data.LogDatabase
import io.github.patrickric.akkuhealthalarm.data.SettingsRepository
import io.github.patrickric.akkuhealthalarm.service.Notifications

class App : Application() {
    val settings by lazy { SettingsRepository(this) }
    val db by lazy { LogDatabase.build(this) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

val Context.app: App get() = applicationContext as App
