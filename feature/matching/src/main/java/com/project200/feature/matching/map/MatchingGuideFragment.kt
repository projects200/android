package com.project200.feature.matching.map

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.project200.common.utils.DefaultPrefs
import com.project200.presentation.compose.applyAppTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MatchingGuideFragment : Fragment() {
    @Inject
    @DefaultPrefs
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            applyAppTheme {
                MatchingGuideScreen(
                    onSkipClick = {
                        sharedPreferences.edit { putBoolean(KEY_FIRST_MATCHING_VISIT, false) }
                        findNavController().navigateUp()
                    },
                    onStartClick = {
                        sharedPreferences.edit { putBoolean(KEY_FIRST_MATCHING_VISIT, false) }
                        findNavController().navigate(
                            MatchingGuideFragmentDirections.actionMatchingGuideFragmentToExercisePlaceSearchFragment(),
                        )
                    },
                )
            }
        }

    companion object {
        const val KEY_FIRST_MATCHING_VISIT = "key_first_matching_visit"
    }
}
