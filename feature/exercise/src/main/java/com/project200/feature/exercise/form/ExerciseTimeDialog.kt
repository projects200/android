package com.project200.feature.exercise.form

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.NumberPicker
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.DialogExerciseTimeBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ExerciseTimeDialog : BaseDialogFragment<DialogExerciseTimeBinding>(R.layout.dialog_exercise_time) {
    override fun getViewBinding(view: View): DialogExerciseTimeBinding {
        return DialogExerciseTimeBinding.bind(view)
    }

    private val calendarDays = ArrayList<Calendar>()
    private lateinit var dateDisplayValues: Array<String>

    var onDateTimeSelected: ((Int, Int, Int, Int, Int) -> Unit)? = null

    override fun setupViews() {
        super.setupViews()

        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.85).toInt()
            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        setupDateAndTimePickers()
        setupListeners() // 리스너 설정 분리

        binding.confirmBtn.setOnClickListener {
            // 이제 여기서는 값을 읽어 콜백만 호출하면 됩니다. (검사 불필요)
            val selectedCal = calendarDays[binding.datePicker.value]
            val year = selectedCal.get(Calendar.YEAR)
            val month = selectedCal.get(Calendar.MONTH)
            val day = selectedCal.get(Calendar.DAY_OF_MONTH)
            val hour = binding.hourPicker.value
            val minute = binding.minutePicker.value

            onDateTimeSelected?.invoke(year, month, day, hour, minute)
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun setupDateAndTimePickers() {
        val currentCalendar = Calendar.getInstance()

        dateDisplayValues = Array(DAYS_IN_PICKER) { "" }
        val sdf = SimpleDateFormat("yy.MM.dd", Locale.KOREAN)

        val tempCal = currentCalendar.clone() as Calendar
        tempCal.add(Calendar.DAY_OF_MONTH, -TODAY_INDEX)

        calendarDays.clear()
        for (i in 0 until DAYS_IN_PICKER) {
            calendarDays.add(tempCal.clone() as Calendar)
            dateDisplayValues[i] = sdf.format(tempCal.time)
            tempCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        with(binding.datePicker) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            minValue = 0
            maxValue = TODAY_INDEX
            displayedValues = dateDisplayValues
            value = TODAY_INDEX
            wrapSelectorWheel = false
        }

        // 초기 시간/분 설정
        with(binding.hourPicker) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            minValue = 0
            maxValue = 23
            setFormatter { value -> String.format(Locale.getDefault(), "%02d", value) }
            wrapSelectorWheel = true
        }

        with(binding.minutePicker) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            minValue = 0
            maxValue = 59
            setFormatter { value -> String.format(Locale.getDefault(), "%02d", value) }
            wrapSelectorWheel = true
        }

        // 초기 상태 (오늘 날짜 기준) 시간/분 피커 조정
        updateTimePickers(true)
    }

    private fun setupListeners() {
        binding.datePicker.setOnValueChangedListener { _, _, newVal ->
            updateTimePickers(newVal == TODAY_INDEX)
        }

        binding.hourPicker.setOnValueChangedListener { _, _, newHour ->
            if (binding.datePicker.value == TODAY_INDEX) {
                updateMinutePickerForToday(newHour)
            }
        }
    }


    private fun updateTimePickers(isToday: Boolean) {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val hourPicker = binding.hourPicker

        if (isToday) {
            hourPicker.maxValue = currentHour
            if (hourPicker.value > currentHour) {
                hourPicker.value = currentHour
            }
            updateMinutePickerForToday(hourPicker.value)
        } else { // 과거 날짜
            hourPicker.maxValue = 23
            binding.minutePicker.maxValue = 59
        }
    }


    private fun updateMinutePickerForToday(selectedHour: Int) {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val minutePicker = binding.minutePicker

        if (selectedHour < currentHour) {
            minutePicker.maxValue = 59
        } else {
            minutePicker.maxValue = currentMinute
            if (minutePicker.value > currentMinute) {
                minutePicker.value = currentMinute
            }
        }
    }

    companion object {
        private const val DAYS_IN_PICKER = 61
        private const val TODAY_INDEX = 60
    }
}