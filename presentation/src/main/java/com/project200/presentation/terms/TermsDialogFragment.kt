package com.project200.presentation.terms

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color.Companion.Unspecified
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.undabang.presentation.R

class TermsDialogFragment : DialogFragment() {
    private val termsType: String
        get() = arguments?.getString(ARG_TERMS_TYPE) ?: TERMS

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                TermsDialogContent(
                    termsType = termsType,
                    onClose = { dismiss() },
                )
            }
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

    companion object {
        const val TERMS = "terms"
        const val PRIVACY = "privacy"
        private const val ARG_TERMS_TYPE = "terms_type"

        fun newInstance(termsType: String): TermsDialogFragment =
            TermsDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_TERMS_TYPE, termsType) }
            }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TermsDialogContent(
    termsType: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val url =
        when (termsType) {
            TermsDialogFragment.PRIVACY -> stringResource(R.string.privacy_link)
            else -> stringResource(R.string.terms_link)
        }

    val webView =
        remember {
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl(url)
            }
        }

    DisposableEffect(Unit) {
        onDispose {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.settings.javaScriptEnabled = false
            webView.clearHistory()
            webView.clearCache(true)
            webView.removeAllViews()
            webView.destroy()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = ColorWhite300,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = Unspecified,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            AndroidView(
                factory = { webView },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp),
            )
        }
    }
}
