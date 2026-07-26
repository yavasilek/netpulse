package ru.yavasilek.netpulse

import android.app.Application
import ru.yavasilek.netpulse.update.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NetPulseApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val container: AppContainer by lazy {
        AppContainer(
            context = applicationContext,
            applicationScope = applicationScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        container
        UpdateScheduler.sync(applicationContext)
    }
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as NetPulseApplication).container
