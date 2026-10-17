package com.project200.undabang.e2e

import com.project200.data.dto.GetPreferredExerciseDTO
import com.project200.data.dto.NotificationStateDTO
import com.project200.domain.usecase.ValidateNicknameUseCase

internal object MaestroProfilePolicy {
    // 플로우의 eraseText는 Maestro 2.11.0 기본값인 50자만 지우므로 더 긴 소개는 UI로 정확히 되돌릴 수 없습니다.
    private const val MAX_ERASABLE_BIO_LENGTH = 50

    // UI 복구도 실제 중복 확인을 거치므로 닉네임은 앱 규칙과 같은 기준으로 검사합니다.
    private val validateNickname = ValidateNicknameUseCase()
    private val genders = setOf("MALE", "FEMALE", "UNKNOWN")
    private val skills = setOf("BEGINNER", "ROOKIE", "INTERMEDIATE", "ADVANCED", "SKILLED", "PRO")

    data class PreferenceRecovery(
        val deleteIds: List<Long>,
        val restore: List<GetPreferredExerciseDTO>,
    )

    fun validateText(
        nickname: String,
        gender: String,
        bio: String?,
    ) {
        // Maestro 2.11.0은 Android 비ASCII inputText를 자체 IME로 입력하므로 한글·문장부호도 복구할 수 있습니다.
        // 빈 소개는 로드된 UI 상태로 확인하고 지운 채 저장합니다. null은 PUT 본문으로 표현할 수 없어 거부합니다.
        val reason =
            when {
                !validateNickname(nickname) -> "profile_baseline_nickname_unsupported"
                gender !in genders -> "profile_baseline_gender_unsupported"
                bio == null -> "profile_baseline_bio_null"
                bio.length > MAX_ERASABLE_BIO_LENGTH -> "profile_baseline_bio_too_long"
                bio.any { it.isISOControl() } -> "profile_baseline_bio_control_characters"
                else -> return
            }
        throw MaestroSetupFailure(reason)
    }

    fun validatePreferences(preferences: List<GetPreferredExerciseDTO>) {
        if (preferences.map { it.preferredExerciseId }.toSet().size != preferences.size ||
            preferences.map { it.exerciseTypeId }.toSet().size != preferences.size ||
            preferences.any {
                it.preferredExerciseId <= 0 || it.exerciseTypeId <= 0 || it.skillLevel !in skills ||
                    it.daysOfWeek.size != 7 || !it.daysOfWeek.contains(true)
            }
        ) {
            throw MaestroSetupFailure("profile_preferences_invalid")
        }
    }

    fun validateNotifications(settings: List<NotificationStateDTO>) {
        val types = settings.map { it.type }
        if (types.any { it.isBlank() } || types.toSet().size != types.size ||
            "WORKOUT_REMINDER" !in types || "CHAT_MESSAGE" !in types
        ) {
            throw MaestroSetupFailure("profile_notifications_invalid")
        }
    }

    fun preferenceRecovery(
        baseline: List<GetPreferredExerciseDTO>,
        current: List<GetPreferredExerciseDTO>,
        addedType: Long?,
    ): PreferenceRecovery {
        validatePreferences(baseline)
        validatePreferences(current)
        val originalIds = baseline.map { it.preferredExerciseId }.toSet()
        val currentById = current.associateBy { it.preferredExerciseId }
        // 기존 ID를 삭제·재생성하면 정확한 복구가 아니므로, 누락과 type 변경은 중단합니다.
        if (baseline.any { currentById[it.preferredExerciseId]?.exerciseTypeId != it.exerciseTypeId }) {
            throw MaestroSetupFailure("profile_preference_identity_changed")
        }
        val added = current.filter { it.preferredExerciseId !in originalIds }
        if (added.any { it.exerciseTypeId != addedType } || added.size > 1 ||
            baseline.any { it.exerciseTypeId == addedType }
        ) {
            throw MaestroSetupFailure("profile_preference_ownership_failed")
        }
        return PreferenceRecovery(
            deleteIds = added.map { it.preferredExerciseId },
            restore =
                baseline.filter {
                    val actual = currentById.getValue(it.preferredExerciseId)
                    it.skillLevel != actual.skillLevel || it.daysOfWeek != actual.daysOfWeek
                },
        )
    }

    fun preferenceValues(preferences: List<GetPreferredExerciseDTO>) =
        preferences.associate { it.preferredExerciseId to Triple(it.exerciseTypeId, it.skillLevel, it.daysOfWeek) }
}
