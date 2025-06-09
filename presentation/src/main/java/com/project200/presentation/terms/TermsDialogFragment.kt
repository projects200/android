package com.project200.presentation.terms

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.drawable.toDrawable
import com.project200.presentation.base.BaseDialogFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.DialogTermsBinding

class TermsDialogFragment(
    private val termsType: String
): BaseDialogFragment<DialogTermsBinding>(R.layout.dialog_terms) {
    override fun getViewBinding(view: View): DialogTermsBinding {
        return DialogTermsBinding.bind(view)
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun setupViews() {
        binding.backBtn.setOnClickListener { dismiss() }

        binding.webview.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                }
                override fun onPageFinished(view: WebView?, url: String?) { super.onPageFinished(view, url) }
                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) { super.onReceivedError(view, request, error) }
            }


            webChromeClient = WebChromeClient()
            loadUrl(when(termsType) {
                TERMS -> getString(R.string.terms_link)
                PRIVACY -> getString(R.string.privacy_link)
                else -> getString(R.string.terms_link)
            })
        }
    }

    override fun onDestroyView() {
        binding.webview.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.settings.javaScriptEnabled = false
            it.clearHistory()
            it.clearCache(true)
            it.removeAllViews()
            it.destroy()
        }
        super.onDestroyView()
    }

    companion object {
        const val TERMS = "terms"
        const val PRIVACY = "privacy"
    }
}