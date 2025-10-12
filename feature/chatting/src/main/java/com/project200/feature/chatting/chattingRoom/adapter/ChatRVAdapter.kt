package com.project200.feature.chatting.chattingRoom.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.ChattingMessage
import com.project200.undabang.feature.chatting.databinding.ItemMyMessageBinding
import com.project200.undabang.feature.chatting.databinding.ItemOpponentMessageBinding
import com.project200.undabang.feature.chatting.databinding.ItemSystemMessageBinding

class ChatRVAdapter : ListAdapter<ChattingMessage, RecyclerView.ViewHolder>(DiffCallback) {
    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when {
            message.chatType == "SYSTEM" -> VIEW_TYPE_SYSTEM_MESSAGE
            message.isMine -> VIEW_TYPE_MY_MESSAGE
            else -> VIEW_TYPE_OTHER_MESSAGE
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MY_MESSAGE -> {
                val binding = ItemMyMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MyMessageViewHolder(binding)
            }
            VIEW_TYPE_OTHER_MESSAGE -> {
                val binding = ItemOpponentMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                OpponentMessageViewHolder(binding)
            }
            else -> { // VIEW_TYPE_SYSTEM_MESSAGE
                val binding = ItemSystemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                SystemMessageViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        val currentMessage = getItem(position)
        when (holder.itemViewType) {
            VIEW_TYPE_MY_MESSAGE -> (holder as MyMessageViewHolder).bind(currentMessage)
            VIEW_TYPE_OTHER_MESSAGE -> (holder as OpponentMessageViewHolder).bind(currentMessage)
            VIEW_TYPE_SYSTEM_MESSAGE -> (holder as SystemMessageViewHolder).bind(currentMessage)
        }
    }

    companion object {
        private const val VIEW_TYPE_MY_MESSAGE = 1
        private const val VIEW_TYPE_OTHER_MESSAGE = 2
        private const val VIEW_TYPE_SYSTEM_MESSAGE = 3

        private val DiffCallback =
            object : DiffUtil.ItemCallback<ChattingMessage>() {
                override fun areItemsTheSame(
                    oldItem: ChattingMessage,
                    newItem: ChattingMessage,
                ): Boolean {
                    return oldItem.chatId == newItem.chatId
                }

                override fun areContentsTheSame(
                    oldItem: ChattingMessage,
                    newItem: ChattingMessage,
                ): Boolean {
                    return oldItem == newItem &&
                        oldItem.showTime == newItem.showTime &&
                        oldItem.showProfile == newItem.showProfile
                }
            }
    }
}
