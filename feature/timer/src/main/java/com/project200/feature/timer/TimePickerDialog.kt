package com.project200.feature.timer

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
import com.project200.domain.model.SimpleTimer
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.DialogTimePickerBinding
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
class TimePickerDialog(
    private val initialTime: Int? = null,
    private val onTimeSelected: (Int) -> Unit
):
    BaseDialogFragment<DialogTimePickerBinding>(R.layout.dialog_time_picker) {
    override fun getViewBinding(view: View): DialogTimePickerBinding {
        return DialogTimePickerBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()

        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.85).toInt()
            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        setupTimePickers()

        binding.confirmBtn.setOnClickListener {
            // NumberPicker에서 선택된 분과 초를 가져옵니다.
            val minutes = binding.minutePicker.value
            val seconds = binding.secondPicker.value
            val totalTimeInSeconds = (minutes * 60) + seconds

            onTimeSelected(totalTimeInSeconds)
            dismiss()
        }

        binding.cancelBtn.setOnClickListener {
            dismiss()
        }
    }

    private fun setupTimePickers() {
        binding.minutePicker.apply {
            minValue = 0
            maxValue = 59 // 최대 59분
            value = initialTime?.div(60) ?: 0 // 초기값이 null인 경우 0으로 설정
            setFormatter { String.format(Locale.KOREA, "%02d", it) }
        }

        binding.secondPicker.apply {
            minValue = 0
            maxValue = 59 // 최대 59초
            value = initialTime?.rem(60) ?: 0 // 초기값이 null인 경우 0으로 설정
            setFormatter { String.format(Locale.KOREA, "%02d", it) }
        }
    }

    companion object {
    }
}