package com.project200.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
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

        @Volatile
        private var exerciseSessionRevision = 0L

        @Synchronized
        fun invalidateExerciseSession() {
            exerciseSessionRevision++
        }

        fun exerciseSessionRevision(): Long = exerciseSessionRevision

        fun saveMemberId(
            memberId: String,
            subject: String?,
        ) {
            if (getRetainedMemberId() != memberId) invalidateExerciseSession()
            check(prefs.edit().putString(KEY_MEMBER_ID, memberId).putString(KEY_MEMBER_SUBJECT, subject).commit()) {
                "회원 상태를 저장하지 못했습니다."
            }
        }

        fun getMemberId(): String? = if (prefs.contains(KEY_PENDING_LOGIN)) null else getRetainedMemberId()

        fun getRetainedMemberId(): String? = prefs.getString(KEY_MEMBER_ID, null)

        fun getPendingLogin(): PendingLogin? {
            val value = prefs.getString(KEY_PENDING_LOGIN, null) ?: return null
            val json = JSONObject(value)
            fun optional(key: String): String? = if (json.isNull(key)) null else json.getString(key)
            return PendingLogin(
                attemptId = json.getString("attempt"),
                retainedMemberId = optional("member"),
                retainedSubject = optional("retainedSubject"),
                sessionRevision = json.getLong("revision"),
                fileSessionRevision = json.getLong("fileRevision"),
                subject = optional("subject"),
                targetMemberId = optional("target"),
                confirmationId = optional("confirmation"),
                signupAllowed = json.optBoolean("signup"),
            )
        }

        fun savePendingLogin(pending: PendingLogin) {
            val value = JSONObject().apply {
                put("attempt", pending.attemptId)
                put("member", pending.retainedMemberId ?: JSONObject.NULL)
                put("retainedSubject", pending.retainedSubject ?: JSONObject.NULL)
                put("revision", pending.sessionRevision)
                put("fileRevision", pending.fileSessionRevision)
                put("subject", pending.subject ?: JSONObject.NULL)
                put("target", pending.targetMemberId ?: JSONObject.NULL)
                put("confirmation", pending.confirmationId ?: JSONObject.NULL)
                put("signup", pending.signupAllowed)
            }
            check(prefs.edit().putString(KEY_PENDING_LOGIN, value.toString()).commit()) {
                "로그인 대기 상태를 저장하지 못했습니다."
            }
        }

        /** 회원 저장 뒤에만 게이트를 엽니다. 프로세스가 중단되면 차단 상태로 남습니다. */
        fun completePendingLogin() {
            check(prefs.edit().remove(KEY_PENDING_LOGIN).commit()) {
                "로그인 대기 상태를 해제하지 못했습니다."
            }
        }

        fun getMemberSubject(): String? {
            return prefs.getString(KEY_MEMBER_SUBJECT, null)
        }

        fun clearMemberId() {
            invalidateExerciseSession()
            check(prefs.edit().remove(KEY_MEMBER_ID).remove(KEY_MEMBER_SUBJECT).commit()) {
                "회원 상태를 지우지 못했습니다."
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
            private const val KEY_MEMBER_SUBJECT = "member_subject"
            private const val KEY_PENDING_LOGIN = "pending_login"
            private const val KEY_EXERCISE_CACHE_INVALIDATED = "exercise_cache_invalidated_"
        }
    }
