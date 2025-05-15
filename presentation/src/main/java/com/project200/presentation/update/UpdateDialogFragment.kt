package com.project200.presentation.update


import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.DialogUpdateBinding
import androidx.core.net.toUri
import com.project200.common.constants.AppConstants.MARKET_URL
import androidx.core.graphics.drawable.toDrawable

class UpdateDialogFragment(private val isForceUpdate: Boolean): BaseDialogFragment<DialogUpdateBinding>(R.layout.dialog_update) {

    override fun getViewBinding(view: View): DialogUpdateBinding {
        return DialogUpdateBinding.bind(view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isCancelable = !isForceUpdate
    }

    override fun onStart() {
        super.onStart()

        dialog?.window?.let { window ->
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = (screenWidth * 0.85).toInt()

            window.setLayout(desiredWidth, WindowManager.LayoutParams.WRAP_CONTENT)

            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        }
    }

    override fun setupViews() {
        super.setupViews()

        binding.updateBtn.setOnClickListener { navigateToMarket() }
        binding.laterBtn.setOnClickListener { dismiss() }

        binding.laterBtn.visibility = if (isForceUpdate) View.GONE else View.VISIBLE
        binding.btnBlank.visibility = if (isForceUpdate) View.GONE else View.VISIBLE
    }

    private fun navigateToMarket() {
        startActivity(Intent(Intent.ACTION_VIEW, MARKET_URL.toUri()))
    }
}