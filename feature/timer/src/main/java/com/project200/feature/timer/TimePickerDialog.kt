package com.project200.feature.timer

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.core.graphics.drawable.toDrawable
import com.project200.presentation.base.BaseDialogFragment
import com.project200.presentation.utils.TimeEditTextLimiter.addRangeLimit
import com.project200.undabang.feature.timer.R
import com.project200.undabang.feature.timer.databinding.DialogTimePickerBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class TimePickerDialog(
    private val initialTime: Int? = null,
    private val onTimeSelected: (Int) -> Unit,
) : BaseDialogFragment<DialogTimePickerBinding>(R.layout.dialog_time_picker) {

    override fun getViewBinding(view: View): DialogTimePickerBinding {
        return DialogTimePickerBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()

        // 다이얼로그 가로 너비 및 배경 투명화 설정
        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.85).toInt()
            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        setupTimeInputs()
        setupFocusAndRangeListeners()

        binding.confirmBtn.setOnClickListener {
            // 빈 칸일 경우 0으로 처리, 범위 재확인
            val minutes = (binding.minuteEt.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59)
            val seconds = (binding.secondEt.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59)
            val totalTimeInSeconds = (minutes * 60) + seconds

            onTimeSelected(totalTimeInSeconds)
            dismiss()
        }

        binding.cancelBtn.setOnClickListener {
            dismiss()
        }
    }

    private fun setupTimeInputs() {
        // 초 단위를 분과 초로 계산하여 초기값 설정
        val minutes = initialTime?.div(60) ?: 0
        val seconds = initialTime?.rem(60) ?: 0

        binding.minuteEt.setText(String.format(Locale.KOREA, "%02d", minutes))
        binding.secondEt.setText(String.format(Locale.KOREA, "%02d", seconds))
    }

    private fun setupFocusAndRangeListeners() {
        // 1. 포커스 상태에 따른 배경 및 포맷팅 처리
        val focusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            if (view is EditText) {
                if (hasFocus) {
                    // 포커스 얻었을 때: 배경 적용 및 텍스트 전체 선택
                    view.setBackgroundResource(com.project200.undabang.presentation.R.drawable.bg_solid_corner)
                    view.selectAll()
                } else {
                    // 포커스 잃었을 때: 배경 제거 및 00~59 보정 후 포맷팅
                    view.setBackgroundResource(0)
                    val input = view.text.toString().toIntOrNull() ?: 0
                    val clamped = input.coerceIn(0, 59)
                    view.setText(String.format(Locale.KOREA, "%02d", clamped))
                }
            }
        }

        binding.minuteEt.onFocusChangeListener = focusChangeListener
        binding.secondEt.onFocusChangeListener = focusChangeListener

        // 실시간 입력 범위 제한 (0~59)
        binding.minuteEt.addRangeLimit(59)
        binding.secondEt.addRangeLimit(59)

        // 초기 상태 배경 제거
        binding.minuteEt.setBackgroundResource(0)
        binding.secondEt.setBackgroundResource(0)
    }
}