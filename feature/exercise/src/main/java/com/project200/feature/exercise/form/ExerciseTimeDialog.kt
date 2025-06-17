package com.project200.feature.exercise.form

import android.graphics.Color
import android.os.Bundle
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
    private var initialCalendar: Calendar? = null

    var onDateTimeSelected: ((Int, Int, Int, Int, Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // arguments에서 초기 시간을 가져옵니다
        arguments?.let {
            if (it.containsKey(ARG_INITIAL_CALENDAR)) {
                initialCalendar = Calendar.getInstance().apply {
                    timeInMillis = it.getLong(ARG_INITIAL_CALENDAR)
                }
            }
        }
    }

    override fun setupViews() {
        super.setupViews()

        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.85).toInt()
            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        setupDateAndTimePickers()
        setupListeners()

        binding.confirmBtn.setOnClickListener {
            val selectedCal = calendarDays[binding.datePicker.value]
            val year = selectedCal.get(Calendar.YEAR)
            val month = selectedCal.get(Calendar.MONTH)
            val day = selectedCal.get(Calendar.DAY_OF_MONTH)
            val hour = binding.hourPicker.value
            val minute = binding.minutePicker.value * 5

            onDateTimeSelected?.invoke(year, month, day, hour, minute)
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun setupDateAndTimePickers() {
        // 전달받은 initialCalendar를 사용, 없으면 현재 시간으로 설정
        val currentCalendar = initialCalendar ?: Calendar.getInstance()

        dateDisplayValues = Array(DAYS_IN_PICKER) { "" }
        val sdf = SimpleDateFormat("yy.MM.dd", Locale.KOREAN)

        val tempCal = Calendar.getInstance()
        tempCal.add(Calendar.DAY_OF_MONTH, -TODAY_INDEX)

        var initialDateIndex = TODAY_INDEX // 기본값은 오늘

        calendarDays.clear()
        // 날짜 배열을 만들고, 초기 날짜의 인덱스를 찾음
        for (i in 0 until DAYS_IN_PICKER) {
            val day = tempCal.clone() as Calendar
            calendarDays.add(day)
            dateDisplayValues[i] = sdf.format(day.time)
            // 초기 날짜와 일치하는 인덱스를 찾음
            if (isSameDay(day, currentCalendar)) {
                initialDateIndex = i
            }
            tempCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        with(binding.datePicker) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            minValue = 0
            maxValue = DAYS_IN_PICKER - 1
            displayedValues = dateDisplayValues
            value = initialDateIndex
            wrapSelectorWheel = false
        }

        with(binding.hourPicker) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            minValue = 0
            maxValue = 23
            setFormatter { value -> String.format(Locale.getDefault(), "%02d", value) }
            wrapSelectorWheel = true
            value = currentCalendar.get(Calendar.HOUR_OF_DAY) // 시간 초기값 설정
        }

        with(binding.minutePicker) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            minValue = 0
            maxValue = 11
            displayedValues = Array(12) { String.format(Locale.getDefault(), "%02d", it * 5) }
            value = currentCalendar.get(Calendar.MINUTE) / 5 // currentCalendar 기준으로 분 초기값 설정
        }

        // 현재 선택된 날짜가 오늘인지 확인하여 시간/분 피커 조정
        updateTimePickers(initialDateIndex == TODAY_INDEX)
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
    // 현재 날짜가 오늘인지에 따라 시간/분 피커를 업데이트
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
            binding.minutePicker.maxValue = 11
        }
    }

    // 현재 선택된 시간에 따라 분 피커를 업데이트
    private fun updateMinutePickerForToday(selectedHour: Int) {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val minutePicker = binding.minutePicker

        if (selectedHour < currentHour) {
            minutePicker.maxValue = 11
        } else { // 현재 시간과 같은 경우
            val maxMinuteIndex = currentMinute / 5 // 5로 나눈 몫으로 최대 인덱스 계산
            minutePicker.maxValue = maxMinuteIndex
            if (minutePicker.value > maxMinuteIndex) {
                minutePicker.value = maxMinuteIndex
            }
        }
    }

    // 두 Calendar가 같은 날짜인지 확인하는 유틸리티 함수
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        private const val ARG_INITIAL_CALENDAR = "initialCalendar"
        private const val DAYS_IN_PICKER = 61
        private const val TODAY_INDEX = 60

        fun newInstance(initialCalendar: Calendar?): ExerciseTimeDialog {
            val args = Bundle()
            initialCalendar?.let {
                args.putLong(ARG_INITIAL_CALENDAR, it.timeInMillis)
            }
            val fragment = ExerciseTimeDialog()
            fragment.arguments = args
            return fragment
        }
    }
}