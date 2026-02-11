package com.project200.undabang.feature.feed.form

import android.net.Uri

data class RegisteredImage(
    val imageId: Long,
    val imageUrl: String
)

sealed class FeedFormImageItem {
    data class Existing(val imageId: Long, val imageUrl: String) : FeedFormImageItem()
    data class New(val uri: Uri) : FeedFormImageItem()
}
