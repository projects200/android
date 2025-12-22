package com.project200.presentation.utils

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

object TimeEditTextLimiter {
    /**
     * EditText 입력 중 최대값을 초과하지 않도록 제한하는 확장 함수
     */
    fun EditText.addRangeLimit(max: Int) {
        this.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toIntOrNull() ?: return
                if (input > max) {
                    setText(max.toString())
                    setSelection(text.length)
                }
            }
        })
    }
}