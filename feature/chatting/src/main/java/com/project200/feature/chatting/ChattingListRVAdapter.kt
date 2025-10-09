package com.project200.feature.chatting

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project200.common.utils.CommonDateTimeFormatters.YYYY_MM_DD_SLASH_KR
import com.project200.common.utils.CommonDateTimeFormatters.a_h_mm_KR
import com.project200.domain.model.ChattingRoom
import com.project200.undabang.feature.chatting.databinding.ItemChattingRoomBinding
import java.time.LocalDate
import java.time.LocalDateTime

class ChattingListRVAdapter :
    ListAdapter<ChattingRoom, ChattingListRVAdapter.ChattingRoomViewHolder>(ChattingRoomDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ChattingRoomViewHolder {
        val binding =
            ItemChattingRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChattingRoomViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ChattingRoomViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class ChattingRoomViewHolder(private val binding: ItemChattingRoomBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(chattingRoom: ChattingRoom) {
            binding.nicknameTv.text = chattingRoom.nickname
            binding.lastMessageTv.text = chattingRoom.lastMessage

            // lastChattedAt을 조건에 따라 포맷팅
            binding.lastTimeTv.text = formatTimestamp(chattingRoom.lastChattedAt)

            if (chattingRoom.unreadCount > 0) {
                binding.badgeTv.visibility = View.VISIBLE
                binding.badgeTv.text = chattingRoom.unreadCount.toString()
            } else {
                binding.badgeTv.visibility = View.GONE
            }
        }

        // 시간 포맷팅을 처리하는 함수
        private fun formatTimestamp(dateTime: LocalDateTime): String {
            val today = LocalDate.now()
            val messageDate = dateTime.toLocalDate()

            return if (messageDate.isEqual(today)) {
                dateTime.format(a_h_mm_KR)
            } else {
                dateTime.format(YYYY_MM_DD_SLASH_KR)
            }
        }
    }

    class ChattingRoomDiffCallback : DiffUtil.ItemCallback<ChattingRoom>() {
        // 아이템의 고유 ID를 비교하여 같은 아이템인지 확인합니다.
        override fun areItemsTheSame(
            oldItem: ChattingRoom,
            newItem: ChattingRoom,
        ): Boolean {
            return oldItem.id == newItem.id
        }

        // 아이템의 내용이 같은지 비교합니다. areItemsTheSame이 true일 때만 호출됩니다.
        override fun areContentsTheSame(
            oldItem: ChattingRoom,
            newItem: ChattingRoom,
        ): Boolean {
            return oldItem == newItem
        }
    }
}
