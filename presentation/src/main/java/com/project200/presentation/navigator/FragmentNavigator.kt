package com.project200.presentation.navigator

interface FragmentNavigator {
    fun navigateFromExerciseListToExerciseDetail(recordId: Int)
    fun navigateFromExerciseListToSetting()
    fun navigateFromExerciseListToExerciseForm()
}