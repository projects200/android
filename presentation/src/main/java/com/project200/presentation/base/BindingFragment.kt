package com.project200.presentation.base

import android.os.Bundle
import android.view.View
import androidx.annotation.LayoutRes
import androidx.viewbinding.ViewBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel

abstract class BindingFragment<VB : ViewBinding>(
    @LayoutRes private val layoutResId: Int
) : Fragment(layoutResId) {

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
        _binding = null
        super.onDestroyView()
    }
}
