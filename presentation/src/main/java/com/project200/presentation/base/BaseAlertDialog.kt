package com.project200.presentation.base

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.DialogBaseAlertBinding
import java.util.Calendar

class BaseAlertDialog(
    private val title: String,
    private val desc: String?,
    private val onConfirmClicked: () -> Unit
) : BaseDialogFragment<DialogBaseAlertBinding>(R.layout.dialog_base_alert) {

    override fun getViewBinding(view: View): DialogBaseAlertBinding {
        return DialogBaseAlertBinding.bind(view)
    }

    @SuppressLint("DefaultLocale")
    override fun setupViews() = with(binding) {
        titleTv.text = title
        descTv.apply {
            if(desc.isNullOrEmpty()) this.visibility = View.GONE
            else text = desc
        }

        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.85).toInt()

            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)

            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        }

        cancelButton.setOnClickListener { dismiss() }
        confirmBtn.setOnClickListener {
            onConfirmClicked()
            dismiss()
        }
    }
}