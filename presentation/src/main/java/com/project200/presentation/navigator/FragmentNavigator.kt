package com.project200.presentation.navigator

import java.time.LocalDate

interface FragmentNavigator {
    /** 운동 리스트 */
    fun navigateFromExerciseListToExerciseDetail(recordId: Long)

    fun navigateFromExerciseListToExerciseForm()

    /** 운동 상세 */
    fun navigateFromExerciseDetailToExerciseForm(recordId: Long)

    /** 운동 생성/수정 */
    fun navigateFromExerciseFormToExerciseDetail(recordId: Long)

    /** 운동 메인 */
    fun navigateFromExerciseMainToExerciseList(date: LocalDate)

    fun navigateFromExerciseMainToSetting()

    fun navigateFromExerciseMainToExerciseForm()
}
