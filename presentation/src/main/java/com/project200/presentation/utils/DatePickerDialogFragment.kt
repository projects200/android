package com.project200.presentation.utils

import android.annotation.SuppressLint
import android.view.View
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.DialogDatePickerBinding
import java.util.Calendar

class DatePickerDialogFragment(
    private val onDateSelected: (String) -> Unit
) : BaseDialogFragment<DialogDatePickerBinding>(R.layout.dialog_date_picker) {

    override fun getViewBinding(view: View): DialogDatePickerBinding {
        return DialogDatePickerBinding.bind(view)
    }

    @SuppressLint("DefaultLocale")
    override fun setupViews() = with(binding) {
        val today = Calendar.getInstance()
        datePicker.maxDate = today.timeInMillis

        cancelButton.setOnClickListener { dismiss() }
        confirmBtn.setOnClickListener {
            val year = datePicker.year
            val month = datePicker.month + 1
            val day = datePicker.dayOfMonth
            onDateSelected(String.format("%04d-%02d-%02d", year, month, day))
            dismiss()
        }
    }
}
