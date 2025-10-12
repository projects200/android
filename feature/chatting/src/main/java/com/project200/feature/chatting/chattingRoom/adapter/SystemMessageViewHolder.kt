package com.project200.feature.chatting.chattingRoom.adapter

import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.ChattingMessage
import com.project200.undabang.feature.chatting.databinding.ItemSystemMessageBinding

class SystemMessageViewHolder(
    private val binding: ItemSystemMessageBinding,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(current: ChattingMessage) {
        binding.messageTv.text = current.content
    }
}
