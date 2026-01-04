package com.project200.presentation.utils

import androidx.annotation.StringRes
import com.project200.undabang.presentation.R

enum class SkillLevel(
    @StringRes val resId: Int,
) {
    BEGINNER(R.string.skill_beginner),
    ROOKIE(R.string.skill_rookie),
    INTERMEDIATE(R.string.skill_intermediate),
    ADVANCED(R.string.skill_advanced),
    SKILLED(R.string.skill_skilled),
    PRO(R.string.skill_pro);

    companion object {
        fun from(key: String?): SkillLevel? {
            return entries.find { it.name == key }
        }

        fun String?.toSkillLevelRes(): Int? {
            return SkillLevel.from(this)?.resId
        }
    }
}