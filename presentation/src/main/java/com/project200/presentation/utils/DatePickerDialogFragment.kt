package com.project200.presentation.utils

import android.view.View
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.DialogDatePickerBinding
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

class DatePickerDialogFragment(
    private val initialDateString: String? = null,
    private val onDateSelected: (String) -> Unit
) : BaseDialogFragment<DialogDatePickerBinding>(R.layout.dialog_date_picker) {

    override fun getViewBinding(view: View): DialogDatePickerBinding {
        return DialogDatePickerBinding.bind(view)
    }

    override fun setupViews() = with(binding) {
        val today = Calendar.getInstance()
        datePicker.maxDate = today.timeInMillis // 오늘 이후 날짜 선택 불가

        // 초기 날짜 설정
        initialDateString?.let { dateStr ->
            if (dateStr.isNotBlank()) {
                val initialDate = LocalDate.parse(dateStr)
                datePicker.updateDate(initialDate.year, initialDate.monthValue - 1, initialDate.dayOfMonth)
            }
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