package com.project200.domain.manager

interface TimerSyncScheduler {
    fun schedule()

    fun cancel()
}
