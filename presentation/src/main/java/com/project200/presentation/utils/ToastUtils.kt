package com.project200.presentation.utils

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun Fragment.collectToast(
    toastFlow: Flow<UiText>,
    duration: Int = Toast.LENGTH_SHORT
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            toastFlow.collect { uiText ->
                Toast.makeText(requireContext(), uiText.asString(requireContext()), duration).show()
            }
        }
    }
}
