package com.project200.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceManager
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun saveMemberId(memberId: String) {
            prefs.edit {
                putString(KEY_MEMBER_ID, memberId)
            }
        }

        fun getMemberId(): String? {
            return prefs.getString(KEY_MEMBER_ID, null)
        }

        fun clearMemberId() {
            prefs.edit {
                remove(KEY_MEMBER_ID)
            }
        }

        fun isExerciseCacheInvalidated(memberId: String): Boolean = prefs.getBoolean(KEY_EXERCISE_CACHE_INVALIDATED + memberId, false)

        fun setExerciseCacheInvalidated(
            memberId: String,
            invalidated: Boolean,
        ) {
            check(prefs.edit().putBoolean(KEY_EXERCISE_CACHE_INVALIDATED + memberId, invalidated).commit()) {
                "운동 기록 캐시 무효 상태를 저장하지 못했습니다."
            }
        }

        companion object {
            private const val PREFS_NAME = "undabangPrefs"
            private const val KEY_MEMBER_ID = "member_id"
            private const val KEY_EXERCISE_CACHE_INVALIDATED = "exercise_cache_invalidated_"
        }
    }
