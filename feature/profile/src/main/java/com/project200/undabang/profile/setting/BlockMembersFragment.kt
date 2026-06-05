package com.project200.undabang.profile.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.project200.presentation.compose.applyAppTheme
import com.project200.undabang.feature.profile.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BlockMembersFragment : Fragment() {
    private val viewModel: BlockMembersViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                val members by viewModel.blockedMembers.collectAsStateWithLifecycle()
                BlockMembersScreen(
                    members = members,
                    onBackClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    onUnblockClick = viewModel::unblockMember,
                )
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorEvent.collect { message ->
                    Toast.makeText(
                        requireContext(),
                        message ?: getString(R.string.error_unknown),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }
}
