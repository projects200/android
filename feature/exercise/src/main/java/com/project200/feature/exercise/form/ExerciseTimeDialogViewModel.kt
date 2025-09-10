package com.project200.feature.exercise.form

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.project200.common.utils.ClockProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class ExerciseTimeDialogViewModel
    @Inject
    constructor(
        private val clockProvider: ClockProvider,
    ) : ViewModel() {
        private val _initialDateTime = MutableLiveData<LocalDateTime?>()
        val initialDateTime: LiveData<LocalDateTime?> = _initialDateTime

        private val _event = MutableSharedFlow<Event>(replay = 0, extraBufferCapacity = 1)
        val event: SharedFlow<Event> = _event

        fun setInitialDateTime(dateTime: LocalDateTime?) {
            _initialDateTime.value = dateTime
        }

        fun onDateTimeConfirmed(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
        ) {
            val selectedDateTime = LocalDateTime.of(year, month, day, hour, minute)
            val currentDateTime = clockProvider.localDateTimeNow()

            val isTodaySelected = selectedDateTime.toLocalDate() == currentDateTime.toLocalDate()

            viewModelScope.launch {
                if (isTodaySelected && selectedDateTime.isAfter(currentDateTime)) {
                    _event.emit(Event.ShowFutureTimeErrorToast)
                    return@launch
                }
                _event.emit(Event.TimeSelected(year, month, day, hour, minute))
            }
        }

        sealed class Event {
            data class TimeSelected(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) : Event()

            data object ShowFutureTimeErrorToast : Event()
        }
    }
