package com.project200.feature.exercise.form

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.DialogExerciseTimeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class ExerciseTimeDialog :
    BaseDialogFragment<DialogExerciseTimeBinding>(R.layout.dialog_exercise_time) {
    override fun getViewBinding(view: View): DialogExerciseTimeBinding {
        return DialogExerciseTimeBinding.bind(view)
    }

    private val viewModel: ExerciseTimeDialogViewModel by viewModels()

    private val calendarDays = ArrayList<Calendar>()
    private lateinit var dateDisplayValues: Array<String>
    private var initialCalendar: Calendar? = null

    var onDateTimeSelected: ((Int, Int, Int, Int, Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            if (it.containsKey(ARG_INITIAL_CALENDAR)) {
                val initialMillis = it.getLong(ARG_INITIAL_CALENDAR)
                val initialCal = Calendar.getInstance().apply { timeInMillis = initialMillis }
                val initialDateTime =
                    LocalDateTime.of(
                        initialCal.get(Calendar.YEAR),
                        initialCal.get(Calendar.MONTH) + 1,
                        initialCal.get(Calendar.DAY_OF_MONTH),
                        initialCal.get(Calendar.HOUR_OF_DAY),
                        initialCal.get(Calendar.MINUTE),
                    )
                viewModel.setInitialDateTime(initialDateTime)
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
        setupObservers()

        binding.confirmBtn.setOnClickListener {
            val selectedDateCal = calendarDays[binding.datePicker.value]
            val year = selectedDateCal.get(Calendar.YEAR)
            val month = selectedDateCal.get(Calendar.MONTH)
            val day = selectedDateCal.get(Calendar.DAY_OF_MONTH)
            val hour = binding.hourPicker.value
            val minute = binding.minutePicker.value * 5

            viewModel.onDateTimeConfirmed(year, month + 1, day, hour, minute)
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun setupDateAndTimePickers() {
        val currentCalendar =
            Calendar.getInstance().apply {
                viewModel.initialDateTime.value?.let {
                    timeInMillis = it.atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
                } ?: run {
                    val now = LocalDateTime.now()
                    val flooredMinute = (now.minute / 5) * 5
                    timeInMillis =
                        now.withMinute(flooredMinute).truncatedTo(ChronoUnit.MINUTES)
                            .atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
                }
            }

        dateDisplayValues = Array(DAYS_IN_PICKER) { "" }
        val sdf = SimpleDateFormat("yy.MM.dd", Locale.KOREAN)

        val tempCal = Calendar.getInstance()
        tempCal.add(Calendar.DAY_OF_MONTH, -TODAY_INDEX)

        var initialDateIndex = TODAY_INDEX

        calendarDays.clear()
        for (i in 0 until DAYS_IN_PICKER) {
            val day = tempCal.clone() as Calendar
            calendarDays.add(day)
            dateDisplayValues[i] = sdf.format(day.time)
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
            value = currentCalendar.get(Calendar.HOUR_OF_DAY)
        }

        with(binding.minutePicker) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            minValue = 0
            maxValue = 11
            displayedValues = Array(12) { String.format(Locale.getDefault(), "%02d", it * 5) }
            value = currentCalendar.get(Calendar.MINUTE) / 5
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collectLatest { event ->
                    when (event) {
                        is ExerciseTimeDialogViewModel.Event.TimeSelected -> {
                            onDateTimeSelected?.invoke(
                                event.year,
                                event.month - 1,
                                event.day,
                                event.hour,
                                event.minute,
                            )
                            dismiss()
                        }

                        is ExerciseTimeDialogViewModel.Event.ShowFutureTimeErrorToast -> {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.exercise_record_time_future_error),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun isSameDay(
        cal1: Calendar,
        cal2: Calendar,
    ): Boolean {
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
