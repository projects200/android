package com.project200.common.base

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModel
import androidx.viewbinding.ViewBinding

abstract class BaseDialogFragment<VB : ViewBinding>(
    @LayoutRes private val layoutResId: Int
) : DialogFragment(layoutResId) {

    private var _binding: VB? = null
    protected val binding get() = _binding!!

    protected abstract fun getViewBinding(view: View): VB

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = getViewBinding(view)
        setupObservers()
        setupViews()
    }

    open fun setupObservers() {}
    open fun setupViews() {}

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
