package com.project200.domain.manager

/**
 * 계정 경계를 지키기 위해 로컬에 쌓인 캐시를 지웁니다.
 * 로그아웃이나 계정 전환 때 이전 사용자의 데이터가 남으면 안 됩니다.
 * 구현은 Room을 쓰는 data 모듈에 있습니다.
 */
interface SessionDataCleaner {
    suspend fun clearAll()

    /** 호출자는 사진 저장 잠금 안에서 확인합니다. 구현은 같은 잠금을 다시 획득하지 않습니다. */
    suspend fun hasStoredData(): Boolean

    /**
     * 사진 저장과 같은 잠금 안에서 세션과 저장소를 다시 확인한 뒤 계정을 확정합니다.
     * discardApproved는 현재 로그인에 묶인 확인 ID를 검증한 호출자만 true로 전달합니다.
     * 승인 없이는 DB나 앱 소유 파일이 남아 있으면 false를 반환하고 아무것도 삭제하지 않습니다.
     * 호출자는 잠금을 잡지 않아야 하며 두 콜백은 네트워크나 중단 지점을 포함하지 않습니다.
     */
    suspend fun clearForAccountAdoption(
        discardApproved: Boolean,
        isCurrent: () -> Boolean,
        onCleared: () -> Unit,
    ): Boolean
}
