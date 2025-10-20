package com.project200.feature.timer.utils

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.project200.feature.timer.simple.SimpleTimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimpleTimerServiceManager @Inject constructor(private val application: Application) {

    private val _service = MutableStateFlow<SimpleTimerService?>(null)
    val service: StateFlow<SimpleTimerService?> = _service

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.Forest.tag("SimpleTimer").d("ServiceManager: onServiceConnected")
            val binder = service as SimpleTimerService.TimerBinder
            _service.value = binder.getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.Forest.tag("SimpleTimer").d("ServiceManager: onServiceDisconnected")
            _service.value = null
        }
    }

    fun bindService() {
        Timber.Forest.tag("SimpleTimer").d("ServiceManager: bindService called")
        Intent(application, SimpleTimerService::class.java).also { intent ->
            application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService() {
        Timber.Forest.tag("SimpleTimer").d("ServiceManager: unbindService called")
        try {
            application.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Timber.Forest.w(e, "Service was not registered.")
        }
    }
}