package com.project200.feature.exercise

import com.google.common.truth.Truth.assertThat
import com.project200.domain.model.BaseResult
import com.project200.feature.exercise.main.ExerciseListAccessibilityState
import com.project200.feature.exercise.main.exerciseListAccessibilityState
import org.junit.Test

class ExerciseListAccessibilityStateTest {
    @Test
    fun `첫 요청 네트워크 실패는 목록 제출이나 레이아웃 완료 없이 오류로 노출한다`() {
        val state =
            exerciseListAccessibilityState(
                isListCommitted = false,
                isListLoaded = false,
                isListEmpty = true,
                error = BaseResult.Error("NETWORK_ERROR", "network"),
            )

        assertThat(state).isEqualTo(ExerciseListAccessibilityState.NETWORK_ERROR)
    }

    @Test
    fun `최신 네트워크 실패는 이전 어댑터 행이 남아 있어도 오류로 노출한다`() {
        val state =
            exerciseListAccessibilityState(
                isListCommitted = false,
                isListLoaded = false,
                isListEmpty = false,
                error = BaseResult.Error("NETWORK_ERROR", "network"),
            )

        assertThat(state).isEqualTo(ExerciseListAccessibilityState.NETWORK_ERROR)
    }

    @Test
    fun `성공한 빈 목록은 어댑터 반영 전에는 성공 상태를 노출하지 않는다`() {
        assertThat(exerciseListAccessibilityState(false, true, true, null))
            .isEqualTo(ExerciseListAccessibilityState.NOT_READY)
        assertThat(exerciseListAccessibilityState(true, true, true, null))
            .isEqualTo(ExerciseListAccessibilityState.EMPTY)
    }

    @Test
    fun `성공한 실제 행도 최신 조회와 어댑터 반영이 모두 끝나야 노출한다`() {
        assertThat(exerciseListAccessibilityState(true, false, false, null))
            .isEqualTo(ExerciseListAccessibilityState.NOT_READY)
        assertThat(exerciseListAccessibilityState(true, true, false, null))
            .isEqualTo(ExerciseListAccessibilityState.CONTENT)
    }

    @Test
    fun `일반 서버 오류는 네트워크 실패로 바꾸지 않는다`() {
        val state = exerciseListAccessibilityState(false, false, true, BaseResult.Error("SERVER_ERROR", "server"))

        assertThat(state).isEqualTo(ExerciseListAccessibilityState.NOT_READY)
    }
}
