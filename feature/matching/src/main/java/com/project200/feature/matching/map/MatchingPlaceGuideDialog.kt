package com.project200.feature.matching.map

import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.DialogMatchingPlaceGuideBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MatchingPlaceGuideDialog(
    private val onGoToPlaceRegister: () -> Unit,
) :
    BaseDialogFragment<DialogMatchingPlaceGuideBinding>(R.layout.dialog_matching_place_guide) {
    override fun getViewBinding(view: View): DialogMatchingPlaceGuideBinding {
        return DialogMatchingPlaceGuideBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()

        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.8).toInt()
            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        binding.startBtn.setOnClickListener {
            onGoToPlaceRegister()
            dismiss()
        }

        binding.closeBtn.setOnClickListener {
            dismiss()
        }
    }
}
