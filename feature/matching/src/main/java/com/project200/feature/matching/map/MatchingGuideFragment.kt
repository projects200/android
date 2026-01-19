package com.project200.feature.matching.map

import android.content.SharedPreferences
import androidx.navigation.fragment.findNavController
import com.project200.common.utils.DefaultPrefs
import com.project200.presentation.base.BindingFragment
import com.project200.undabang.feature.matching.R
import com.project200.undabang.feature.matching.databinding.FragmentMatchingGuideBinding
import androidx.core.content.edit
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MatchingGuideFragment: BindingFragment<FragmentMatchingGuideBinding> (R.layout.fragment_matching_guide) {

    @Inject
    @DefaultPrefs
    lateinit var sharedPreferences: SharedPreferences


    override fun getViewBinding(view: android.view.View): FragmentMatchingGuideBinding {
        return FragmentMatchingGuideBinding.bind(view)
    }

    override fun setupViews() {
        binding.registerBtn.setOnClickListener {
            sharedPreferences.edit() { putBoolean(KEY_FIRST_MATCHING_VISIT, false) }
            findNavController().navigate(
                MatchingGuideFragmentDirections.actionMatchingGuideFragmentToExercisePlaceSearchFragment(),
            )
        }

        binding.skipBtn.setOnClickListener {
            sharedPreferences.edit() { putBoolean(KEY_FIRST_MATCHING_VISIT, false) }
            findNavController().navigateUp()
        }
    }

    companion object {
        const val KEY_FIRST_MATCHING_VISIT = "key_first_matching_visit"
    }
}
