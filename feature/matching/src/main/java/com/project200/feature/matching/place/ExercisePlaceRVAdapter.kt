package com.project200.feature.matching.place

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.project200.domain.model.ExercisePlace
import com.project200.undabang.feature.matching.databinding.ItemExercisePlaceBinding

class ExercisePlaceRVAdapter(
    val onMenuClicked: (ExercisePlace, View) -> Unit,
) : RecyclerView.Adapter<ExercisePlaceRVAdapter.ExercisePlaceViewHolder>() {
    private var places: List<ExercisePlace> = emptyList()

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ExercisePlaceViewHolder {
        val binding =
            ItemExercisePlaceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return ExercisePlaceViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ExercisePlaceViewHolder,
        position: Int,
    ) {
        holder.bind(places[position])
    }

    override fun getItemCount(): Int = places.size

    fun submitList(newPlaces: List<ExercisePlace>) {
        places = newPlaces
        notifyDataSetChanged()
    }

    inner class ExercisePlaceViewHolder(
        private val binding: ItemExercisePlaceBinding,
    ) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(place: ExercisePlace) {
            binding.placeNameTv.text = place.name
            binding.placeAddressTv.text = place.address
            binding.menuIv.setOnClickListener {
                onMenuClicked(place, binding.menuIv)
            }
        }
    }
}
