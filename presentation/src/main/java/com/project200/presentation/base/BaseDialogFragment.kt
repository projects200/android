package com.project200.presentation.base

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding

abstract class BaseDialogFragment<VB : ViewBinding>(
    @LayoutRes private val layoutResId: Int,
) : DialogFragment(layoutResId) {
    private var _binding: VB? = null
    val binding get() = _binding!!

    protected abstract fun getViewBinding(view: View): VB

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        _binding = getViewBinding(view)
        setupViews()
    }

    open fun setupViews() {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
