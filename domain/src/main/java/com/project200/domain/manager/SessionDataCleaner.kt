package com.project200.domain.manager

/**
 * 계정 경계를 지키기 위해 로컬에 쌓인 캐시를 지웁니다.
 * 로그아웃이나 계정 전환 때 이전 사용자의 데이터가 남으면 안 됩니다.
 * 구현은 Room을 쓰는 data 모듈에 있습니다.
 */
interface SessionDataCleaner {
    suspend fun clearAll()
}
