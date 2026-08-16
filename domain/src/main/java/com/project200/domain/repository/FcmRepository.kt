package com.project200.domain.repository

import com.project200.domain.model.BaseResult

interface FcmRepository {
    /** 기기에 저장된 FCM 토큰. 없으면 null */
    suspend fun getSavedToken(): String?

    /** 저장된 FCM 토큰을 서버에 등록합니다. 토큰이 없으면 보내지 않고 실패를 반환합니다 */
    suspend fun registerToken(): BaseResult<Unit>
}
