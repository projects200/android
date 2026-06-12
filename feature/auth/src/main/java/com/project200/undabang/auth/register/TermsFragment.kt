package com.project200.undabang.auth.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.project200.presentation.compose.applyAppTheme
import com.project200.presentation.terms.TermsDialogFragment
import com.project200.presentation.terms.TermsDialogFragment.Companion.PRIVACY
import com.project200.presentation.terms.TermsDialogFragment.Companion.TERMS
import com.project200.undabang.feature.auth.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TermsFragment : Fragment() {
    private val viewModel: TermsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val serviceChecked by viewModel.serviceChecked.collectAsStateWithLifecycle()
                val privacyChecked by viewModel.privacyChecked.collectAsStateWithLifecycle()
                val isAllRequiredChecked by viewModel.isAllRequiredChecked.collectAsStateWithLifecycle()

                TermsScreen(
                    serviceChecked = serviceChecked,
                    privacyChecked = privacyChecked,
                    isAllRequiredChecked = isAllRequiredChecked,
                    onToggleService = { viewModel.toggleService() },
                    onTogglePrivacy = { viewModel.togglePrivacy() },
                    onServiceClick = { showTermsDialog(TERMS) },
                    onPrivacyClick = { showTermsDialog(PRIVACY) },
                    onNextClick = {
                        if (isAdded && findNavController().currentDestination?.id == R.id.termsFragment) {
                            findNavController().navigate(R.id.action_termsFragment_to_registerFragment)
                        }
                    },
                )
            }
        }

    private fun showTermsDialog(termsType: String) {
        TermsDialogFragment.newInstance(termsType).show(parentFragmentManager, TermsDialogFragment::class.java.simpleName)
    }
}
