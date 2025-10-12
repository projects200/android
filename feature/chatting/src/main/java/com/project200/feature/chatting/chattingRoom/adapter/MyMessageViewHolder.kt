package com.project200.feature.chatting.chattingRoom.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.ChattingMessage
import com.project200.feature.chatting.utils.TimestampFormatter.formatTimestamp
import com.project200.presentation.utils.UiUtils.dpToPx
import com.project200.undabang.feature.chatting.R
import com.project200.undabang.feature.chatting.databinding.ItemMyMessageBinding

class MyMessageViewHolder(
    private val binding: ItemMyMessageBinding,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(current: ChattingMessage) {
        binding.messageTv.text = current.content

        // 그룹의 마지막 메시지인지 판단하여 시간 표시 및 padding 결정
        if (current.showTime) {
            // 하단 패딩을 12dp로 설정
            binding.root.setPadding(
                binding.root.paddingLeft,
                binding.root.paddingTop,
                binding.root.paddingRight,
                binding.root.context.resources.getDimensionPixelSize(R.dimen.chatting_margin)
            )
            // 시간 표시
            binding.timeTv.visibility = View.VISIBLE
            binding.timeTv.text = formatTimestamp(current.sentAt)
        } else {
            // 하단 패딩을 5dp로 설정
            binding.root.setPadding(
                binding.root.paddingLeft,
                binding.root.paddingTop,
                binding.root.paddingRight,
                binding.root.context.resources.getDimensionPixelSize(R.dimen.chatting_in_group_margin)
            )
            // 시간 표시 안함
            binding.timeTv.visibility = View.INVISIBLE
            binding.timeTv.text = formatTimestamp(current.sentAt)
        }
    }
}
