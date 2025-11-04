package com.project200.feature.matching.map

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getString
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.feature.matching.utils.GenderType
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.ItemMemberBinding

class MemberRVAdapter(
    private val onMemberClick: (MapClusterItem) -> Unit,
) : ListAdapter<MapClusterItem, MemberRVAdapter.MemberViewHolder>(MemberDiffCallback) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: MemberViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class MemberViewHolder(private val binding: ItemMemberBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MapClusterItem) {
            binding.root.setOnClickListener {
                onMemberClick(item)
            }
            binding.nicknameTv.text = item.member.nickname
            binding.genderBirthTv.text =
                setGenderBirth(
                    binding.root.context,
                    item.member.gender,
                    item.member.birthDate,
                )
            binding.placeNameTv.text = item.location.exerciseLocationName

            val imageRes = item.member.profileThumbnailUrl ?: item.member.profileImageUrl

            Glide.with(binding.profileIv)
                .load(imageRes)
                .placeholder(R.drawable.ic_profile_default)
                .error(R.drawable.ic_profile_default)
                .into(binding.profileIv)
        }
    }

    private fun setGenderBirth(
        context: Context,
        gender: String,
        birthDate: String,
    ): String {
        val genderStr =
            when (gender) {
                GenderType.MALE.stringValue -> getString(context, R.string.mypage_male)
                GenderType.FEMALE.stringValue -> getString(context, R.string.mypage_female)
                else -> getString(context, R.string.unknown_gender)
            }

        return context.getString(R.string.gender_birth_format, genderStr, birthDate)
    }

    companion object MemberDiffCallback : DiffUtil.ItemCallback<MapClusterItem>() {
        override fun areItemsTheSame(
            oldItem: MapClusterItem,
            newItem: MapClusterItem,
        ): Boolean {
            return oldItem.member.memberId == newItem.member.memberId &&
                oldItem.location.latitude == newItem.location.latitude &&
                oldItem.location.longitude == newItem.location.longitude
        }

        override fun areContentsTheSame(
            oldItem: MapClusterItem,
            newItem: MapClusterItem,
        ): Boolean {
            return oldItem == newItem
        }
    }
}
