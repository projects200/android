package com.project200.feature.matching

import androidx.lifecycle.ViewModel
import com.project200.domain.usecase.GetMatchingMembersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MatchingMapViewModel @Inject constructor(
    private val getMatchingMembersUseCase: GetMatchingMembersUseCase,
): ViewModel() {

}