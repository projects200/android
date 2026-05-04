package com.project200.presentation.compose.components.feedback

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.project200.presentation.compose.applyAppTheme

/**
 * Fragment 컨텍스트에서 [UndabangAlertDialogContent] 를 띄우는 DialogFragment.
 *
 * 기존 `BaseAlertDialog` 의 대체. companion [show] 팩토리로 호출한다.
 * 콜백은 인스턴스 변수로 보관되어 configuration change 시 사라질 수 있는데, 이는 기존 `BaseAlertDialog`
 * 와 동일한 한계.
 */
class UndabangAlertDialogFragment : DialogFragment() {
    private val title: String get() = arguments?.getString(ARG_TITLE).orEmpty()
    private val message: String get() = arguments?.getString(ARG_MESSAGE).orEmpty()
    private val confirmText: String get() = arguments?.getString(ARG_CONFIRM_TEXT) ?: DEFAULT_CONFIRM_TEXT
    private val cancelText: String get() = arguments?.getString(ARG_CANCEL_TEXT) ?: DEFAULT_CANCEL_TEXT
    private val showCancel: Boolean get() = arguments?.getBoolean(ARG_SHOW_CANCEL) == true

    private var onConfirmListener: (() -> Unit)? = null
    private var onCancelListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = arguments?.getBoolean(ARG_IS_CANCELABLE, true) ?: true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                UndabangAlertDialogContent(
                    title = title,
                    message = message,
                    confirmText = confirmText,
                    cancelText = cancelText,
                    onConfirm = {
                        onConfirmListener?.invoke()
                        dismiss()
                    },
                    onCancel =
                        if (showCancel) {
                            {
                                onCancelListener?.invoke()
                                dismiss()
                            }
                        } else {
                            null
                        },
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
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_CONFIRM_TEXT = "confirm_text"
        private const val ARG_CANCEL_TEXT = "cancel_text"
        private const val ARG_SHOW_CANCEL = "show_cancel"
        private const val ARG_IS_CANCELABLE = "is_cancelable"
        private const val DEFAULT_CONFIRM_TEXT = "확인"
        private const val DEFAULT_CANCEL_TEXT = "취소"

        fun show(
            fragmentManager: FragmentManager,
            title: String,
            message: String = "",
            confirmText: String = DEFAULT_CONFIRM_TEXT,
            cancelText: String = DEFAULT_CANCEL_TEXT,
            isCancelable: Boolean = true,
            onCancel: (() -> Unit)? = null,
            onConfirm: () -> Unit,
        ) {
            UndabangAlertDialogFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_TITLE, title)
                        putString(ARG_MESSAGE, message)
                        putString(ARG_CONFIRM_TEXT, confirmText)
                        putString(ARG_CANCEL_TEXT, cancelText)
                        putBoolean(ARG_SHOW_CANCEL, onCancel != null)
                        putBoolean(ARG_IS_CANCELABLE, isCancelable)
                    }
                onConfirmListener = onConfirm
                onCancelListener = onCancel
            }.show(fragmentManager, UndabangAlertDialogFragment::class.java.name)
        }
    }
}
