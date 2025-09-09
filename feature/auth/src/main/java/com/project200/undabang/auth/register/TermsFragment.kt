package com.project200.undabang.auth.register

import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.project200.presentation.base.BindingFragment
import com.project200.presentation.terms.TermsDialogFragment
import com.project200.presentation.terms.TermsDialogFragment.Companion.PRIVACY
import com.project200.presentation.terms.TermsDialogFragment.Companion.TERMS
import com.project200.undabang.feature.auth.R
import com.project200.undabang.feature.auth.databinding.FragmentTermsBinding
import timber.log.Timber

class TermsFragment : BindingFragment<FragmentTermsBinding>(R.layout.fragment_terms) {
    private val viewModel: TermsViewModel by viewModels()

    override fun getViewBinding(view: View): FragmentTermsBinding {
        return FragmentTermsBinding.bind(view)
    }

    override fun setupViews() =
        with(binding) {
            // 체크 토글
            serviceBtnImv.setOnClickListener { viewModel.toggleService() }
            privacyBtnImv.setOnClickListener { viewModel.togglePrivacy() }
        /*locationBtnImv.setOnClickListener { viewModel.toggleLocation() }
        notifyBtnImv.setOnClickListener { viewModel.toggleNotify() }*/

            serviceTitleTv.setOnClickListener {
                showTermsDialog(TERMS)
            }
            privacyTitleTv.setOnClickListener {
                showTermsDialog(PRIVACY)
            }

            termsNextBtn.setOnClickListener {
                if (isAdded && findNavController().currentDestination?.id == R.id.termsFragment) {
                    findNavController().navigate(R.id.action_termsFragment_to_registerFragment)
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

        /*viewModel.locationChecked.observe(viewLifecycleOwner) { checked ->
            binding.locationBtnImv.isSelected = checked
        }

        viewModel.notifyChecked.observe(viewLifecycleOwner) { checked ->
            binding.notifyBtnImv.isSelected = checked
        }*/

        viewModel.isAllRequiredChecked.observe(viewLifecycleOwner) { allChecked ->
            Timber.d("isAllRequiredChecked $allChecked")
            binding.termsNextBtn.isEnabled = allChecked
        }
    }

    private fun showTermsDialog(termsType: String) {
        TermsDialogFragment(termsType).show(parentFragmentManager, TermsDialogFragment::class.java.simpleName)
    }
}
