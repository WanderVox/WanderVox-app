package com.wandervox.app

import android.app.Application
import com.wandervox.core.data.repository.LogSyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WanderVoxApp : Application() {

    @Inject
    lateinit var logSyncCoordinator: LogSyncCoordinator

    override fun onCreate() {
        super.onCreate() // Hilt injects the fields here.
        logSyncCoordinator.start()
    }
}
