package com.project200.feature.exercise.form

import android.net.Uri

sealed class ExerciseImageListItem {
    object AddButtonItem : ExerciseImageListItem()
    data class ImageItem(val uri: Uri) : ExerciseImageListItem()

    val id: String
        get() = when (this) {
            is AddButtonItem -> "add_button"
            is ImageItem -> uri.toString()
        }
}