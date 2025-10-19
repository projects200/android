package com.project200.feature.timer.utils

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.project200.feature.timer.custom.CustomTimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomTimerServiceManager @Inject constructor(private val application: Application) {
    private val _service = MutableStateFlow< CustomTimerService?>(null)
    val service: StateFlow<CustomTimerService?> = _service

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CustomTimerService.TimerBinder
            _service.value = binder.getService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
        }
    }

    fun bindService() {
        Intent(application, CustomTimerService::class.java).also { intent ->
            application.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService() {
        application.unbindService(serviceConnection)
    }
}