package com.project200.data.local

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExerciseCacheInvalidationPreferenceTest {
    @Test
    fun `무효 표시는 새 설정 인스턴스에도 남고 회원별로 독립적으로 해제된다`() {
        val context: android.content.Context = RuntimeEnvironment.getApplication()
        val preferences = PreferenceManager(context)
        val firstMember = "cache-invalidation-member-a"
        val secondMember = "cache-invalidation-member-b"
        try {
            preferences.setExerciseCacheInvalidated(firstMember, true)
            preferences.setExerciseCacheInvalidated(secondMember, true)
            val reopened = PreferenceManager(context)
            assertThat(reopened.isExerciseCacheInvalidated(firstMember)).isTrue()
            assertThat(reopened.isExerciseCacheInvalidated(secondMember)).isTrue()
            reopened.setExerciseCacheInvalidated(firstMember, false)
            val nextInstance = PreferenceManager(context)
            assertThat(nextInstance.isExerciseCacheInvalidated(firstMember)).isFalse()
            assertThat(nextInstance.isExerciseCacheInvalidated(secondMember)).isTrue()
        } finally {
            preferences.setExerciseCacheInvalidated(firstMember, false)
            preferences.setExerciseCacheInvalidated(secondMember, false)
        }
    }
}
