package com.project200.undabang.e2e

import com.project200.data.dto.GetPreferredExerciseDTO
import com.project200.data.dto.NotificationStateDTO

internal object MaestroProfilePolicy {
    private val nicknamePattern = Regex("[A-Za-z0-9]{1,30}")
    private val bioPattern = Regex("[A-Za-z0-9][A-Za-z0-9 _-]*")
    private val genders = setOf("MALE", "FEMALE", "UNKNOWN")
    private val skills = setOf("BEGINNER", "ROOKIE", "INTERMEDIATE", "ADVANCED", "SKILLED", "PRO")

    data class PreferenceRecovery(
        val deleteIds: List<Long>,
        val restore: List<GetPreferredExerciseDTO>,
    )

    fun validateText(nickname: String, gender: String, bio: String?) {
        // Maestro Android inputText로 원문을 정확히 복구할 수 있는 전용 계정만 허용합니다.
        if (!nicknamePattern.matches(nickname) || gender !in genders ||
            bio == null || bio.length > 4096 || !bioPattern.matches(bio)
        ) {
            throw MaestroSetupFailure("profile_baseline_text_unsupported")
        }
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
            restore = baseline.filter {
                val actual = currentById.getValue(it.preferredExerciseId)
                it.skillLevel != actual.skillLevel || it.daysOfWeek != actual.daysOfWeek
            },
        )
    }

    fun preferenceValues(preferences: List<GetPreferredExerciseDTO>) =
        preferences.associate { it.preferredExerciseId to Triple(it.exerciseTypeId, it.skillLevel, it.daysOfWeek) }
}
