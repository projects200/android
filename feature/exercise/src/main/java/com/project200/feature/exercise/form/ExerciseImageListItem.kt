package com.project200.feature.exercise.form

import android.net.Uri

sealed class ExerciseImageListItem {
    // 각 아이템을 구분하기 위한 고유 키
    abstract val key: String

    object AddButtonItem : ExerciseImageListItem() {
        override val key: String = "add_button"
    }

    // 새로 추가된 이미지 (Uri)
    data class NewImageItem(val uri: Uri) : ExerciseImageListItem() {
        override val key: String = uri.toString()
    }

    // 서버 연결 후에도 로컬 키와 파일/URL 표시 소스는 그대로 사용합니다.
    data class ExistingImageItem(val source: String, val localId: String) : ExerciseImageListItem() {
        override val key: String = localId
    }
}
