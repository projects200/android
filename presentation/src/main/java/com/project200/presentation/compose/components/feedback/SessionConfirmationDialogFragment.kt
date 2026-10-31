package com.project200.presentation.compose.components.feedback

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.project200.presentation.compose.applyAppTheme

/** 세션 삭제 승인은 인스턴스 콜백 대신 복원 가능한 action과 Fragment Result로 전달합니다. */
class SessionConfirmationDialogFragment : DialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                UndabangAlertDialogContent(
                    title = requireArguments().getString(TITLE).orEmpty(),
                    message = requireArguments().getString(MESSAGE).orEmpty(),
                    confirmText = requireArguments().getString(CONFIRM_TEXT).orEmpty(),
                    onConfirm = {
                        sendResult(true)
                        dismiss()
                    },
                    onCancel = {
                        sendResult(false)
                        dismiss()
                    },
                )
            }
        }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        sendResult(false)
        super.onCancel(dialog)
    }

    private fun sendResult(confirmed: Boolean) {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            bundleOf(ACTION to requireArguments().getString(ACTION), CONFIRMED to confirmed),
        )
    }

    companion object {
        const val RESULT_KEY = "session_confirmation_result"
        const val ACTION = "action"
        const val CONFIRMED = "confirmed"
        private const val TITLE = "title"
        private const val MESSAGE = "message"
        private const val CONFIRM_TEXT = "confirm_text"

        fun newInstance(action: String, title: String, message: String, confirmText: String) =
            SessionConfirmationDialogFragment().apply {
                arguments = bundleOf(ACTION to action, TITLE to title, MESSAGE to message, CONFIRM_TEXT to confirmText)
            }
    }
}
