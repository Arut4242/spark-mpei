package ru.mpei.assistant

import android.app.Application
import ru.mpei.assistant.data.local.SessionStorage
import ru.mpei.assistant.worker.BarsSyncScheduler
import ru.mpei.assistant.worker.EmailCheckWorker
import ru.mpei.assistant.worker.NotificationHelper

class MpeiApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Create notification channels
        NotificationHelper.createChannels(this)

        // Schedule periodic background check for BARS (grades & absences: 2 hours)
        BarsSyncScheduler.schedule(this, 120L)

        // Start background check for OSEP email (10 minutes)
        EmailCheckWorker.start(this)
    }
}
