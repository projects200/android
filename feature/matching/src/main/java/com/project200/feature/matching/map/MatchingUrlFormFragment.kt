package com.project200.feature.matching.map

import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.domain.model.BaseResult
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingUrlFormBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MatchingUrlFormFragment : BindingFragment<FragmentMatchingUrlFormBinding>(R.layout.fragment_matching_url_form) {
    private val viewModel: MatchingUrlFormViewModel by viewModels()

    override fun getViewBinding(view: android.view.View): FragmentMatchingUrlFormBinding {
        return FragmentMatchingUrlFormBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.open_url_form_title))
            showBackButton(true) { findNavController().navigateUp() }
        }
        setupListeners()
    }

    private fun setupListeners() {
        binding.confirmBtn.setOnClickListener {
            val url = binding.urlEt.text.toString()
            if(url.startsWith(getString(R.string.open_chat_type))) viewModel.confirmUrl(url)
            else Toast.makeText(requireContext(), getString(R.string.open_chat_invalid), Toast.LENGTH_SHORT).show()
        }
    }

    override fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.confirmResult.collect { result ->
                    when (result) {
                        is BaseResult.Success -> {
                            findNavController().navigate(
                                MatchingUrlFormFragmentDirections.actionMatchingUrlFormFragmentToMatchingMapFragment()
                            )
                        }
                        is BaseResult.Error -> {
                            Toast.makeText(requireContext(), getString(R.string.fail_to_save_url), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

    }
}
