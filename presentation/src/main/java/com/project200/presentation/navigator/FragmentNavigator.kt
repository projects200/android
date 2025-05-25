package com.project200.presentation.navigator

interface FragmentNavigator {
    fun navigateFromExerciseListToExerciseDetail(recordId: Long)
    fun navigateFromExerciseListToSetting()
    fun navigateFromExerciseListToExerciseForm()
    fun navigateFromExerciseDetailToExerciseForm(recordId: Long)
    fun navigateFromExerciseFormToExerciseDetail(recordId: Long)
}