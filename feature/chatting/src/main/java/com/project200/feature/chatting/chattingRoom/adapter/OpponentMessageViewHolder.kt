package com.project200.feature.chatting.chattingRoom.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.domain.model.ChattingMessage
import com.project200.feature.chatting.utils.TimestampFormatter.formatTimestamp
import com.project200.undabang.feature.chatting.R
import com.project200.undabang.feature.chatting.databinding.ItemOpponentMessageBinding

class OpponentMessageViewHolder(
    private val binding: ItemOpponentMessageBinding,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(current: ChattingMessage) {
        binding.messageTv.text = current.content
        val imageRes = current.thumbnailImageUrl ?: current.profileUrl

        Glide.with(binding.profileImgIv)
            .load(imageRes)
            .placeholder(R.drawable.ic_profile_default)
            .error(R.drawable.ic_profile_default)
            .into(binding.profileImgIv)

        // 그룹의 첫 메시지인지 판단하여 프로필 표시
        binding.profileImgIv.visibility = if (current.showProfile) View.VISIBLE else View.INVISIBLE

        // 시간 표시 및 padding 결정
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

    // 그룹의 마지막 메시지 여부 (시간 표시 결정)
    fun isLastInGroup(current: ChattingMessage, next: ChattingMessage?): Boolean {
        if (next == null) return true // 마지막 메시지
        // 다음 메시지가 시스템 메시지거나, 보낸사람이 다르거나, 분(minute)이 다르면 현재 그룹 종료
        return next.chatType == "SYSTEM" ||
                current.senderId != next.senderId ||
                current.sentAt.minute != next.sentAt.minute
    }

    // 그룹의 첫 메시지 여부 (프로필, 닉네임 표시 결정)
    fun isFirstInGroup(current: ChattingMessage, prev: ChattingMessage?): Boolean {
        if (prev == null) return true // 첫 메시지
        // 이전 메시지가 시스템 메시지거나, 보낸사람이 다르거나, 분(minute)이 다르면 새 그룹 시작
        return prev.chatType == "SYSTEM" ||
                current.senderId != prev.senderId ||
                current.sentAt.minute != prev.sentAt.minute
    }

}
