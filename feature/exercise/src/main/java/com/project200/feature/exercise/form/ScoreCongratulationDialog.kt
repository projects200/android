package com.project200.feature.exercise.form

import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.feature.exercise.R
import com.project200.undabang.feature.exercise.databinding.DialogScoreCongratulationBinding

class ScoreCongratulationDialog : BaseDialogFragment<DialogScoreCongratulationBinding>(R.layout.dialog_score_congratulation) {
    var confirmClickListener: (() -> Unit)? = null

    override fun getViewBinding(view: View): DialogScoreCongratulationBinding {
        return DialogScoreCongratulationBinding.bind(view)
    }

    override fun setupViews() {
        isCancelable = false

        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.70).toInt()
            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        binding.confirmBtn.setOnClickListener {
            confirmClickListener?.invoke()
            dismiss()
        }
    }

}