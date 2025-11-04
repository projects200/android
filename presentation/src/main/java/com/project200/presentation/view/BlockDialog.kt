package com.project200.presentation.view

import android.graphics.Color
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.DialogBlockBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BlockDialog(
    private val onBlockBtnClicked: () -> Unit,
) :
    BaseDialogFragment<DialogBlockBinding>(R.layout.dialog_block) {
    override fun getViewBinding(view: View): DialogBlockBinding {
        return DialogBlockBinding.bind(view)
    }

    override fun setupViews() {
        super.setupViews()

        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.8).toInt()
            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.blockBtn.setOnClickListener {
            onBlockBtnClicked()
            dismiss()
        }
    }
}
