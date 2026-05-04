package com.project200.presentation.update

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.project200.common.constants.AppConstants.MARKET_URL
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.compose.components.button.PrimaryButton
import com.project200.presentation.compose.components.button.SecondaryButton
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentRegular
import com.project200.presentation.compose.theme.header
import com.project200.undabang.presentation.R

private val UpdateDialogButtonHeight = 45.dp

class UpdateDialogFragment : DialogFragment() {
    private val isForceUpdate: Boolean
        get() = arguments?.getBoolean(ARG_FORCE_UPDATE, false) ?: false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = !isForceUpdate
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                UpdateDialogContent(
                    isForceUpdate = isForceUpdate,
                    onUpdate = {
                        navigateToMarket()
                        dismiss()
                    },
                    onLater = { dismiss() },
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

    private fun navigateToMarket() {
        startActivity(Intent(Intent.ACTION_VIEW, MARKET_URL.toUri()))
    }

    companion object {
        private const val ARG_FORCE_UPDATE = "force_update"

        fun newInstance(isForceUpdate: Boolean): UpdateDialogFragment =
            UpdateDialogFragment().apply {
                arguments = Bundle().apply { putBoolean(ARG_FORCE_UPDATE, isForceUpdate) }
            }
    }
}

@Composable
fun UpdateDialogContent(
    isForceUpdate: Boolean,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = ColorWhite300,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.update_title),
                style = MaterialTheme.typography.header,
                color = ColorBlack,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.update_desc),
                style = MaterialTheme.typography.contentRegular,
                color = ColorBlack,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (isForceUpdate) {
                PrimaryButton(
                    text = stringResource(R.string.update_btn),
                    onClick = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    height = UpdateDialogButtonHeight,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.update_later_btn),
                        onClick = onLater,
                        modifier = Modifier.weight(1f),
                        height = UpdateDialogButtonHeight,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.update_btn),
                        onClick = onUpdate,
                        modifier = Modifier.weight(1f),
                        height = UpdateDialogButtonHeight,
                    )
                }
            }
        }
    }
}
