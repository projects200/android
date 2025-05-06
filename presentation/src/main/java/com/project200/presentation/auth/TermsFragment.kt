package com.project200.presentation.auth

import android.view.View
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.project200.common.base.BindingFragment
import com.project200.undabang.presentation.R
import com.project200.undabang.presentation.databinding.FragmentTermsBinding
import timber.log.Timber

class TermsFragment : BindingFragment<FragmentTermsBinding>(R.layout.fragment_terms) {

    private val viewModel: TermsViewModel by viewModels()

    override fun getViewBinding(view: View): FragmentTermsBinding {
        return FragmentTermsBinding.bind(view)
    }

    override fun setupViews() = with(binding) {
        // 체크 토글
        serviceBtnImv.setOnClickListener { viewModel.toggleService() }
        privacyBtnImv.setOnClickListener { viewModel.togglePrivacy() }
        locationBtnImv.setOnClickListener { viewModel.toggleLocation() }
        notifyBtnImv.setOnClickListener { viewModel.toggleNotify() }

        termsNextBtn.setOnClickListener {
            if (isAdded && findNavController().currentDestination?.id == R.id.termsFragment) {
                findNavController().navigate(R.id.action_termsFragment_to_signUpFragment)
            }
        }
    }

    override fun setupObservers() {
        viewModel.serviceChecked.observe(viewLifecycleOwner) { checked ->
            binding.serviceBtnImv.isSelected = checked
        }

        viewModel.privacyChecked.observe(viewLifecycleOwner) { checked ->
            binding.privacyBtnImv.isSelected = checked
        }

        viewModel.locationChecked.observe(viewLifecycleOwner) { checked ->
            binding.locationBtnImv.isSelected = checked
        }

        viewModel.notifyChecked.observe(viewLifecycleOwner) { checked ->
            binding.notifyBtnImv.isSelected = checked
        }

        viewModel.isAllRequiredChecked.observe(viewLifecycleOwner) { allChecked ->
            Timber.d("isAllRequiredChecked $allChecked")
            binding.termsNextBtn.isEnabled = allChecked
        }
    }
}
