package com.project200.undabang

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ApplicationClass: Application() {
    override fun onCreate() {
        super.onCreate()
    }
}