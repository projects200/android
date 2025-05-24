package com.project200.feature.exercise.form

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

        binding.confirmBtn.setOnClickListener {
            onDateTimeSelected?.let { callback ->
                val selectedCal = calendarDays[binding.datePicker.value]
                val year = selectedCal.get(Calendar.YEAR)
                val month = selectedCal.get(Calendar.MONTH) // 0부터 시작
                val day = selectedCal.get(Calendar.DAY_OF_MONTH)
                val hour = binding.hourPicker.value
                val minute = binding.minutePicker.value
                callback(year, month, day, hour, minute)
            }
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
        tempCal.add(Calendar.DAY_OF_MONTH, -CURRENT_DATE_INDEX)

        calendarDays.clear()
        for (i in 0 until DAYS_IN_PICKER) {
            calendarDays.add(tempCal.clone() as Calendar)
            dateDisplayValues[i] = sdf.format(tempCal.time)
            tempCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        with(binding.datePicker) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            minValue = 0
            maxValue = DAYS_IN_PICKER - 1
            displayedValues = dateDisplayValues
            value = CURRENT_DATE_INDEX
            wrapSelectorWheel = true
        }

        with(binding.hourPicker) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            minValue = 0
            maxValue = 23
            value = currentCalendar.get(Calendar.HOUR_OF_DAY)
            setFormatter { value -> String.format(Locale.getDefault(), "%02d", value) }
            wrapSelectorWheel = true
        }

        with(binding.minutePicker) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            minValue = 0
            maxValue = 59
            value = currentCalendar.get(Calendar.MINUTE)
            setFormatter { value -> String.format(Locale.getDefault(), "%02d", value) }
            wrapSelectorWheel = true
        }
    }

    companion object {
        private const val DAYS_IN_PICKER = 61
        private const val CURRENT_DATE_INDEX = 30
    }
}