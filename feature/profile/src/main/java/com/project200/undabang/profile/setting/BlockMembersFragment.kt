package com.project200.undabang.profile.setting

import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.profile.R
import com.project200.undabang.feature.profile.databinding.FragmentBlockMembersBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BlockMembersFragment: BindingFragment<FragmentBlockMembersBinding>(R.layout.fragment_block_members) {
    private val viewModel: BlockMembersViewModel by viewModels()
    private lateinit var blockMemberAdapter: BlockMemberRVAdapter

    override fun getViewBinding(view: View): FragmentBlockMembersBinding {
        return FragmentBlockMembersBinding.bind(view)
    }

    override fun setupViews() {
        binding.baseToolbar.apply {
            setTitle(getString(R.string.block_members))
            showBackButton(true) { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        blockMemberAdapter = BlockMemberRVAdapter { member ->
            viewModel.unblockMember(member)
        }

        binding.blockMembersRv.apply {
            adapter = blockMemberAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    override fun setupObservers() {
        viewModel.blockedMembers.observe(viewLifecycleOwner) { members ->
            blockMemberAdapter.submitList(members)
            binding.blockMembersRv.isVisible = !members.isNullOrEmpty()
            // 차단된 사용자가 없을 때 표시할 뷰
            binding.emptyBlockMembersTv.isVisible = members.isNullOrEmpty()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.errorEvent.collect { message ->
                    Toast.makeText(requireContext(), message ?: getString(R.string.error_unknown), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}