package com.project200.data.impl

import android.content.Context
import androidx.core.net.toUri
import com.project200.common.di.IoDispatcher
import com.project200.data.api.ApiService
import com.project200.data.dto.CommentDTO
import com.project200.data.dto.CreateCommentRequestDTO
import com.project200.data.dto.CreateCommentResponseDTO
import com.project200.data.dto.FeedCreateResultDTO
import com.project200.data.dto.FeedDTO
import com.project200.data.dto.FeedPictureUploadDTO
import com.project200.data.dto.GetFeedsDTO
import com.project200.data.dto.LikeRequestDTO
import com.project200.data.mapper.toDTO
import com.project200.data.mapper.toModel
import com.project200.data.mapper.toMultipartBodyPart
import com.project200.data.utils.apiCallBuilder
import com.project200.domain.model.BaseResult
import com.project200.domain.model.Comment
import com.project200.domain.model.CreateCommentResult
import com.project200.domain.model.CreateFeedModel
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedCreateResult
import com.project200.domain.model.FeedListResult
import com.project200.domain.model.FeedPicture
import com.project200.domain.model.UpdateFeedModel
import com.project200.domain.repository.FeedRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class FeedRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context,
) : FeedRepository {

    override suspend fun getFeeds(prevFeedId: Long?, size: Int?): BaseResult<FeedListResult> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getFeeds(prevFeedId, size) },
            mapper = { dto: GetFeedsDTO? ->
                dto?.toModel() ?: FeedListResult(hasNext = false, feeds = emptyList())
            },
        )
    }

    override suspend fun getFeedDetail(feedId: Long): BaseResult<Feed> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getFeedDetail(feedId) },
            mapper = { dto: FeedDTO? ->
                dto?.toModel() ?: throw NoSuchElementException("피드 상세 데이터가 없습니다.")
            },
        )
    }

    override suspend fun createFeed(createFeedModel: CreateFeedModel): BaseResult<FeedCreateResult> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postFeed(createFeedModel.toDTO()) },
            mapper = { dto: FeedCreateResultDTO? ->
                dto?.toModel() ?: throw NoSuchElementException("피드 생성 결과 데이터가 없습니다.")
            },
        )
    }

    override suspend fun deleteFeed(feedId: Long): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.deleteFeed(feedId) },
            mapper = { Unit },
        )
    }

    override suspend fun getComments(feedId: Long): BaseResult<List<Comment>> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.getComments(feedId) },
            mapper = { dtoList: List<CommentDTO>? ->
                dtoList?.map { it.toModel() } ?: emptyList()
            },
        )
    }

    override suspend fun createComment(
        feedId: Long,
        content: String,
        parentCommentId: Long?,
        taggedMemberId: String?,
    ): BaseResult<CreateCommentResult> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = {
                apiService.createComment(
                    feedId = feedId,
                    request = CreateCommentRequestDTO(
                        content = content,
                        parentCommentId = parentCommentId,
                        taggedMemberId = taggedMemberId,
                    ),
                )
            },
            mapper = { dto: CreateCommentResponseDTO? ->
                dto?.toModel() ?: throw NoSuchElementException("댓글 생성 결과 데이터가 없습니다.")
            },
        )
    }

    override suspend fun likeComment(commentId: Long, liked: Boolean): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.likeComment(commentId, LikeRequestDTO(liked)) },
            mapper = { Unit },
        )
    }

    override suspend fun deleteComment(commentId: Long): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.deleteComment(commentId) },
            mapper = { Unit },
        )
    }

    override suspend fun likeFeed(feedId: Long, liked: Boolean): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.likeFeed(feedId, LikeRequestDTO(liked)) },
            mapper = { Unit },
        )
    }

    override suspend fun updateFeed(updateFeedModel: UpdateFeedModel): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.updateFeed(updateFeedModel.feedId, updateFeedModel.toDTO()) },
            mapper = { Unit },
        )
    }

    override suspend fun uploadFeedImages(
        feedId: Long,
        imageUris: List<String>,
    ): BaseResult<List<FeedPicture>> {
        val uris = imageUris.mapNotNull {
            try {
                it.toUri()
            } catch (e: CancellationException) {
                Timber.w(e, "CancellationException: $it")
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Invalid URI string: $it")
                null
            }
        }

        val imageParts = uris.mapNotNull { uri ->
            uri.toMultipartBodyPart(context, "pictures")
        }

        if (imageParts.isEmpty() && uris.isNotEmpty()) {
            return BaseResult.Error("CONVERSION_FAILED", "이미지 파일 변환에 실패했습니다.")
        }

        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.postFeedImages(feedId, imageParts) },
            mapper = { dtoList: List<FeedPictureUploadDTO>? ->
                dtoList?.map { it.toModel() } ?: emptyList()
            },
        )
    }

    override suspend fun deleteFeedImage(feedId: Long, imageId: Long): BaseResult<Unit> {
        return apiCallBuilder(
            ioDispatcher = ioDispatcher,
            apiCall = { apiService.deleteFeedImage(feedId, imageId) },
            mapper = { Unit },
        )
    }
}
