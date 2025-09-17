package com.project200.feature.exercise.main

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import com.project200.common.constants.RuleConstants.MIN_YEAR
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.DialogExerciseYearMonthBinding
import java.time.YearMonth

class ExerciseYearMonthDialog :
    BaseDialogFragment<DialogExerciseYearMonthBinding>(R.layout.dialog_exercise_year_month) {
    var onDateSelected: ((YearMonth) -> Unit)? = null
    private var initialDate: YearMonth = YearMonth.now()
    private val currentYearMonth: YearMonth = YearMonth.now()

    // 피커에 표시할 문자열 배열
    private lateinit var yearDisplayValues: Array<String>
    private val monthDisplayValues: Array<String> = (1..12).map { "${it}월" }.toTypedArray()

    override fun getViewBinding(view: View): DialogExerciseYearMonthBinding {
        return DialogExerciseYearMonthBinding.bind(view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { bundle ->
            initialDate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getSerializable(ARG_INITIAL_DATE, YearMonth::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getSerializable(ARG_INITIAL_DATE) as? YearMonth
            } ?: YearMonth.now()
        }
    }

    override fun setupViews() {
        super.setupViews()
        setupDialogWindow()
        setupPickers()
        setupClickListeners()
    }

    private fun setupDialogWindow() {
        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.85).toInt()
            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable()) // 투명 배경 적용
        }
    }

    private fun setupPickers() {
        // Year Picker
        yearDisplayValues = (MIN_YEAR..currentYearMonth.year).map { "${it}년" }.toTypedArray()
        binding.yearPicker.apply {
            minValue = 0
            maxValue = yearDisplayValues.size - 1
            displayedValues = yearDisplayValues
            wrapSelectorWheel = false

            // 인덱스 반환
            setOnValueChangedListener { _, _, newIndex ->
                val selectedYear = MIN_YEAR + newIndex
                updateMonthPickerMax(selectedYear)
            }
        }

        // Month Picker
        binding.monthPicker.apply {
            minValue = 0
            maxValue = 11 // (12 - 1)
            displayedValues = monthDisplayValues
            wrapSelectorWheel = true
        }

        // 초기값 설정
        val initialYearIndex = initialDate.year - MIN_YEAR
        if (initialYearIndex in yearDisplayValues.indices) {
            binding.yearPicker.value = initialYearIndex
        }

        // 초기 년도를 기준으로 월 피커의 최대값 설정
        updateMonthPickerMax(initialDate.year)

        // 월 피커의 초기값(인덱스) 설정
        var initialMonthIndex = initialDate.monthValue - 1
        // 설정하려는 월이 최대치를 넘지 않도록 보정
        if (initialMonthIndex > binding.monthPicker.maxValue) {
            initialMonthIndex = binding.monthPicker.maxValue
        }
        binding.monthPicker.value = initialMonthIndex
    }

    private fun updateMonthPickerMax(year: Int) {
        // 년도에 따라 월의 최대값(인덱스 기준)을 동적으로 변경
        val maxMonthIndex = if (year == currentYearMonth.year) currentYearMonth.monthValue - 1 else 11

        binding.monthPicker.maxValue = maxMonthIndex
        // 만약 기존에 선택된 월이 새로운 최대치를 벗어나면 최대치로 값을 보정
        if (binding.monthPicker.value > maxMonthIndex) {
            binding.monthPicker.value = maxMonthIndex
        }
    }

    private fun setupClickListeners() {
        binding.confirmBtn.setOnClickListener {
            val selectedYear = MIN_YEAR + binding.yearPicker.value
            val selectedMonth = binding.monthPicker.value + 1
            val selectedYearMonth = YearMonth.of(selectedYear, selectedMonth)

            onDateSelected?.invoke(selectedYearMonth)
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    companion object {
        private const val ARG_INITIAL_DATE = "initial_date"

        fun newInstance(initialDate: YearMonth): ExerciseYearMonthDialog {
            val dialog = ExerciseYearMonthDialog()
            dialog.arguments =
                Bundle().apply {
                    putSerializable(ARG_INITIAL_DATE, initialDate)
                }
            return dialog
        }
    }
}
