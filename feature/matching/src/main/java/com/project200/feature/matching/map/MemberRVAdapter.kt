package com.project200.feature.matching.map

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getString
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.project200.feature.matching.map.cluster.MapClusterItem
import com.project200.feature.matching.utils.GenderType
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.ItemMemberBinding

class MemberRVAdapter(
    private val onMemberClick: (MapClusterItem) -> Unit,
) : RecyclerView.Adapter<MemberRVAdapter.MemberViewHolder>() {
    private var itemList: List<MapClusterItem> = emptyList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): MemberViewHolder {
        val binding = ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MemberViewHolder(binding)
    }

    override fun getItemCount(): Int = itemList.size

    override fun onBindViewHolder(
        holder: MemberViewHolder,
        position: Int,
    ) {
        holder.bind(itemList[position])
    }

    /**
     * 어댑터의 데이터를 갱신하고 UI를 새로고침하는 함수
     */
    fun updateList(items: List<MapClusterItem>) {
        this.itemList = items
        notifyDataSetChanged() // 리스트 전체를 갱신
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
            binding.placeNameTv.text = item.location.placeName

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
}
