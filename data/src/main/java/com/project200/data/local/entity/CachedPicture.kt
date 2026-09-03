package com.project200.data.local.entity

import com.squareup.moshi.JsonClass

/**
 * 상세 화면에 뿌릴 사진 한 장입니다.
 *
 * 사진 파일이 아니라 주소만 담습니다. 오프라인에서 사진이 실제로 뜨는 건
 * Coil과 Glide의 디스크 캐시가 맡습니다
 *
 * id는 로컬 조회 키가 아니라 이미지 삭제 요청에 서버로 되돌려 보낼 값입니다
 */
@JsonClass(generateAdapter = true)
data class CachedPicture(
    val id: Long,
    val url: String,
)
