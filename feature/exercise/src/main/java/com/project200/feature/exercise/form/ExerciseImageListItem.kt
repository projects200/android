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

    // 기존 이미지 (Url + 서버 ID)
    data class ExistingImageItem(val url: String, val pictureId: Long) : ExerciseImageListItem() {
        override val key: String = pictureId.toString()
    }
}
