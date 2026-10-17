package com.project200.domain.manager

/** 서버에서 다시 받을 수 있는 6개월 이전 운동 캐시만 정리합니다. 로컬 변경과 타이머는 보존합니다 */
interface ExerciseCacheCleaner {
    suspend fun cleanExpiredCache()
}
