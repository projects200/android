package com.project200.presentation.utils

import android.annotation.SuppressLint
import android.view.View
import com.project200.presentation.base.BaseDialogFragment
// DatePickerDialogFragment가 사용하는 레이아웃(R.layout.dialog_date_picker)이 있는 모듈의 R 클래스를 임포트해야 합니다.
// 만약 presentation 모듈에 있다면 아래와 같이 사용합니다.
import com.project200.undabang.presentation.R // 또는 com.project200.presentation.R 등 실제 경로로 수정
import com.project200.undabang.presentation.databinding.DialogDatePickerBinding // 바인딩 클래스 경로 확인
import timber.log.Timber
import java.time.LocalDate // LocalDate 임포트
import java.time.format.DateTimeParseException // DateTimeParseException 임포트
import java.util.Calendar

class DatePickerDialogFragment(
    private val initialDateString: String? = null, // 초기 날짜 문자열을 받을 파라미터 추가
    private val onDateSelected: (String) -> Unit
) : BaseDialogFragment<DialogDatePickerBinding>(R.layout.dialog_date_picker) {

    override fun getViewBinding(view: View): DialogDatePickerBinding {
        return DialogDatePickerBinding.bind(view)
    }

    @SuppressLint("DefaultLocale")
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
            onDateSelected(String.format("%04d-%02d-%02d", year, month + 1, day))
            dismiss()
        }
    }
}