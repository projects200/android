package com.project200.data.utils

import android.util.Base64
import com.project200.data.datasource.ExerciseImageFileStore
import com.project200.data.local.PreferenceManager
import com.project200.undabang.oauth.AuthStateManager
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject

/** 토큰 자체는 보관하지 않습니다. 프로세스 재시작 후에는 새 세션에서 저널을 다시 claim합니다. */
data class ExerciseSyncAccount(
    val memberId: String,
    val sessionRevision: Long,
    val fileSessionRevision: Long,
)

class ExerciseSyncAccountGuard
    @Inject
    constructor(
        private val preferences: PreferenceManager,
        private val files: ExerciseImageFileStore,
        private val authStateManager: AuthStateManager,
    ) {
        fun capture(memberId: String): ExerciseSyncAccount? =
            ExerciseSyncAccount(memberId, preferences.exerciseSessionRevision(), files.sessionRevision)
                .takeIf { isCurrent(it) }

        fun isCurrent(account: ExerciseSyncAccount): Boolean = accepts(account, authStateManager.getCurrent().idToken)

        fun accepts(account: ExerciseSyncAccount, token: String?): Boolean {
            // 계정 전환 뒤의 subject를 이전 세션 검사에 섞지 않도록 먼저 읽습니다
            val expectedSubject = preferences.getMemberSubject() ?: account.memberId
            return preferences.getMemberId() == account.memberId &&
                preferences.exerciseSessionRevision() == account.sessionRevision &&
                files.sessionRevision == account.fileSessionRevision &&
                idTokenSubject(token) == expectedSubject
        }

        fun requireToken(account: ExerciseSyncAccount, token: String?) {
            if (!accepts(account, token)) throw ExerciseSyncAuthenticationException()
        }
    }

// 서명 검증을 대신하지 않습니다. 서버가 확인한 회원ID와 JWT subject의 연결에만 사용합니다
internal fun idTokenSubject(token: String?): String? =
    try {
        val parts = token?.split('.')
        if (parts?.size != 3) null else {
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
            JSONObject(payload).optString("sub").takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }

class ExerciseSyncAuthenticationException : IOException("EXERCISE_SYNC_AUTHENTICATION_REQUIRED")
