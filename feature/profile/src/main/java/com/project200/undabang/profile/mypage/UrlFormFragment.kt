package com.project200.undabang.profile.mypage

import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.project200.domain.model.BaseResult
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentUrlFormBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class UrlFormFragment : BindingFragment<FragmentUrlFormBinding>(R.layout.fragment_url_form) {
    private val viewModel: UrlFormViewModel by viewModels()
    private val args: UrlFormFragmentArgs by navArgs()

    override fun getViewBinding(view: android.view.View): FragmentUrlFormBinding {
        return FragmentUrlFormBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.open_url_form_title))
            showBackButton(true) { findNavController().navigateUp() }
        }
        Timber.tag("UrlFormFragment").d("args.id: ${args.id}, args.url: ${args.url}")
        viewModel.setInitialUrl(args.id, args.url)
        binding.urlEt.setText(args.url)
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
                            Toast.makeText(requireContext(), getString(R.string.success_to_save_url), Toast.LENGTH_SHORT).show()
                            findNavController().previousBackStackEntry?.savedStateHandle?.set(MypageFragment.OPEN_URL_REFRESH_KEY, true)
                            findNavController().popBackStack()
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
