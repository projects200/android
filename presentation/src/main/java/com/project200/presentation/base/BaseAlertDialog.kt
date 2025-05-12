package com.project200.presentation.utils

import android.annotation.SuppressLint
import android.view.View
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.DialogBaseAlertBinding
import java.time.LocalDate
import java.util.Calendar

class BaseAlertDialog(
    private val title: String,
    private val onConfirmClicked: () -> Unit
) : BaseDialogFragment<DialogBaseAlertBinding>(R.layout.dialog_base_alert) {

    override fun getViewBinding(view: View): DialogBaseAlertBinding {
        return DialogBaseAlertBinding.bind(view)
    }

    @SuppressLint("DefaultLocale")
    override fun setupViews() = with(binding) {
        val today = Calendar.getInstance()
        titleTv.text = title

        cancelButton.setOnClickListener { dismiss() }
        confirmBtn.setOnClickListener {
            onConfirmClicked()
        }
    }
}