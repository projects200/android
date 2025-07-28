package com.project200.presentation.base


import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import com.project200.common.constants.RuleConstants
import com.project200.common.constants.RuleConstants.MIN_YEAR
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.DialogDatePickerBinding
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

class DatePickerDialogFragment(
    private val initialDateString: String? = null,
    private val maxDate: LocalDate? = null,
    private val onDateSelected: (String) -> Unit
) : BaseDialogFragment<DialogDatePickerBinding>(R.layout.dialog_date_picker) {

    override fun getViewBinding(view: View): DialogDatePickerBinding {
        return DialogDatePickerBinding.bind(view)
    }

    override fun setupViews() = with(binding) {
        val calendarMaxDate = Calendar.getInstance().apply {
            maxDate?.let {
                set(it.year, it.monthValue - 1, it.dayOfMonth)
            } ?: run {
                // maxDate가 null이면 오늘 날짜로 설정
                timeInMillis = System.currentTimeMillis()
            }
        }
        datePicker.maxDate = calendarMaxDate.timeInMillis

        datePicker.minDate = LocalDate.of(MIN_YEAR, 8, 15).toEpochDay() * 24 * 60 * 60 * 1000L // 최소 날짜 설정

        val defaultDate = LocalDate.of(2000, 1, 1) // 기본값 2000년 1월 1일

        // 초기 날짜 설정
        initialDateString?.let { dateStr ->
            if (dateStr.isNotBlank()) {
                val initialDate = LocalDate.parse(dateStr)
                datePicker.updateDate(initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth)
            }
        }?: run {
            // initialDateString이 null인 경우 2000/01/01로 설정
            datePicker.updateDate(defaultDate.year, defaultDate.monthValue - 1, defaultDate.dayOfMonth)
        }

        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.85).toInt()

            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)

            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        }

        cancelButton.setOnClickListener { dismiss() }
        confirmBtn.setOnClickListener {
            val year = datePicker.year
            val month = datePicker.month
            val day = datePicker.dayOfMonth
            onDateSelected(String.format(Locale.KOREA,"%04d-%02d-%02d", year, month + 1, day))
            dismiss()
        }
    }
}