package com.project200.domain.model

enum class Gender(val code: String) {
    MALE("M"),
    FEMALE("F")
}

enum class AgeGroup(val code: String) {
    TEEN("10"),
    TWENTIES("20"),
    THIRTIES("30"),
    FORTIES("40"),
    FIFTIES("50"),
    SIXTIES_PLUS("60")
}

enum class DayOfWeek(val index: Int) {
    MONDAY(0),
    TUESDAY(1),
    WEDNESDAY(2),
    THURSDAY(3),
    FRIDAY(4),
    SATURDAY(5),
    SUNDAY(6)
}


enum class SkillLevel(val code: String) {
    NOVICE("NOVICE"),
    BEGINNER("BEGINNER"),
    INTERMEDIATE("INTERMEDIATE"),
    ADVANCED("ADVANCED"),
    EXPERT("EXPERT"),
    PROFESSIONAL("PROFESSIONAL")
}


enum class ExerciseScore(val minScore: Int) {
    OVER_20(20),
    OVER_40(40),
    OVER_60(60),
    OVER_80(80)
}