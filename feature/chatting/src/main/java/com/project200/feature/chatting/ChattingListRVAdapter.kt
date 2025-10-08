package com.project200.feature.chatting

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.ChattingRoom
import com.project200.undabang.feature.chatting.databinding.ItemChattingRoomBinding

class ChattingListRVAdapter :
    ListAdapter<ChattingRoom, ChattingListRVAdapter.ChattingRoomViewHolder>(ChattingRoomDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChattingRoomViewHolder {
        val binding =
            ItemChattingRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChattingRoomViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChattingRoomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChattingRoomViewHolder(private val binding: ItemChattingRoomBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(chattingRoom: ChattingRoom) {
            binding.nicknameTv.text = chattingRoom.nickname
            binding.lastMessageTv.text = chattingRoom.lastMessage
            binding.lastTimeTv.text = chattingRoom.lastChattedAt
            binding.badgeTv.text = chattingRoom.unreadCount.toString()
        }
    }

    class ChattingRoomDiffCallback : DiffUtil.ItemCallback<ChattingRoom>() {
        // 아이템의 고유 ID를 비교하여 같은 아이템인지 확인합니다.
        override fun areItemsTheSame(oldItem: ChattingRoom, newItem: ChattingRoom): Boolean {
            return oldItem.id == newItem.id
        }

        // 아이템의 내용이 같은지 비교합니다. areItemsTheSame이 true일 때만 호출됩니다.
        override fun areContentsTheSame(oldItem: ChattingRoom, newItem: ChattingRoom): Boolean {
            return oldItem == newItem
        }
    }
}