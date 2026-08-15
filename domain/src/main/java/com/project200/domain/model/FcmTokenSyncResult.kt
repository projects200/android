package com.project200.domain.model

/** FCM 토큰 서버 반영 결과 */
enum class FcmTokenSyncResult {
    SUCCESS, // 서버에 반영됨
    SKIPPED, // 로그인 전이거나 보낼 토큰이 없음. 재시도해도 같음
    FAILURE, // 전송을 시도했으나 실패. 재시도 대상
}
