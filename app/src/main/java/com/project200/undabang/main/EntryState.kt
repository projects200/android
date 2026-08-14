package com.project200.undabang.main

sealed class EntryState {
    object Loading : EntryState()

    object Content : EntryState()

    object Login : EntryState()

    data class ForceUpdate(val fromReconnect: Boolean) : EntryState()
}
