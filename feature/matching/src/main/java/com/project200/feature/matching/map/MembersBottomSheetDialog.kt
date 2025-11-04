package com.project200.feature.matching.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.resources.MaterialResources.getDimensionPixelSize
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.undabang.feature.matching.databinding.DialogMembersBottomSheetBinding
import com.project200.undabang.presentation.R

class MembersBottomSheetDialog(
    private val items: List<MapClusterItem>,
    private val onItemClick: (MapClusterItem) -> Unit,
) : BottomSheetDialogFragment() {
    private var _binding: DialogMembersBottomSheetBinding? = null
    val binding get() = _binding!!

    private lateinit var memberAdapter: MemberRVAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogMembersBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        binding.closeBtn.setOnClickListener { dismiss() }
    }

    private fun setupRecyclerView() {
        memberAdapter =
            MemberRVAdapter { item ->
                onItemClick(item)
                dismiss()
            }
        binding.rvMembers.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = memberAdapter
        }
        memberAdapter.submitList(items)

        setupHeight()
    }

    private fun setupHeight() {
        if (items.size > 3) {
            binding.rvMembers.post {
                // 첫 번째 아이템 뷰의 높이를 가져옵니다.
                val firstItem = binding.rvMembers.findViewHolderForAdapterPosition(0)?.itemView
                val itemHeight = firstItem?.height ?: 0

                if (itemHeight > 0) {
                    val maxHeight =
                        (itemHeight * MAX_HEIGHT_ITEM_COUNT).toInt() + resources.getDimensionPixelSize(
                            R.dimen.base_horizontal_margin,
                        ) * 3

                    val layoutParams = binding.rvMembers.layoutParams
                    layoutParams.height = maxHeight
                    binding.rvMembers.layoutParams = layoutParams
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val MAX_HEIGHT_ITEM_COUNT = 3
    }
}
