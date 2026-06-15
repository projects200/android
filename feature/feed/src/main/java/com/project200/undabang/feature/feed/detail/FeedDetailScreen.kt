package com.project200.undabang.feature.feed.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.project200.domain.model.Feed
import com.project200.domain.model.FeedPicture
import com.project200.presentation.compose.components.layout.UndabangScaffold
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.subtext12
import com.project200.presentation.compose.theme.subtext14
import com.project200.presentation.utils.RelativeTimeUtil
import com.project200.undabang.feature.feed.R
import com.project200.undabang.presentation.R as PresentationR

@Composable
fun FeedDetailScreen(
    feed: Feed?,
    commentItems: List<CommentItem>,
    isLoading: Boolean,
    isLoadError: Boolean,
    isMyFeed: Boolean,
    currentMemberId: String?,
    replyTarget: CommentItem?,
    commentInput: String,
    onCommentInputChange: (String) -> Unit,
    onSendComment: () -> Unit,
    onCancelReply: () -> Unit,
    onReplyClick: (CommentItem) -> Unit,
    onCommentLikeClick: (CommentItem) -> Unit,
    onCommentMoreClick: (CommentItem) -> Unit,
    onFeedLikeClick: () -> Unit,
    onFeedMoreClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UndabangScaffold(
        modifier = modifier,
        topBar = {
            UndabangTopBar(
                title = "",
                onNavigationClick = onBackClick,
                actions = {
                    // 내 피드일 때만 우측에 더보기 메뉴 노출
                    if (isMyFeed) {
                        IconButton(onClick = onFeedMoreClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_more),
                                contentDescription = null,
                                tint = Color.Unspecified,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            CommentInputBar(
                input = commentInput,
                replyTarget = replyTarget,
                onChange = onCommentInputChange,
                onSend = onSendComment,
                onCancelReply = onCancelReply,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                isLoadError -> {
                    Text(
                        text = stringResource(R.string.feed_load_error),
                        style = MaterialTheme.typography.subtext14,
                        color = ColorGray200,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                feed != null -> {
                    FeedDetailContent(
                        feed = feed,
                        commentItems = commentItems,
                        currentMemberId = currentMemberId,
                        onFeedLikeClick = onFeedLikeClick,
                        onCommentLikeClick = onCommentLikeClick,
                        onReplyClick = onReplyClick,
                        onCommentMoreClick = onCommentMoreClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedDetailContent(
    feed: Feed,
    commentItems: List<CommentItem>,
    currentMemberId: String?,
    onFeedLikeClick: () -> Unit,
    onCommentLikeClick: (CommentItem) -> Unit,
    onReplyClick: (CommentItem) -> Unit,
    onCommentMoreClick: (CommentItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") { FeedHeader(feed = feed) }
        item(key = "content") {
            Text(
                text = feed.feedContent.orEmpty(),
                style = MaterialTheme.typography.subtext14,
                color = ColorBlack,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
        if (feed.feedPictures.isNotEmpty()) {
            item(key = "images") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(feed.feedPictures, key = { it.feedPictureId }) { picture ->
                        DetailImage(picture = picture)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        item(key = "actions") { FeedActionBar(feed = feed, onLikeClick = onFeedLikeClick) }
        item(key = "divider") {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(1.dp)
                    .background(ColorGray300),
            )
        }
        if (commentItems.isEmpty()) {
            item(key = "no-comments") {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.comment_empty_message),
                        style = MaterialTheme.typography.subtext14,
                        color = ColorGray200,
                    )
                }
            }
        } else {
            items(commentItems, key = { "${if (it is CommentItem.ReplyData) "r" else "c"}-${it.commentId}" }) { item ->
                CommentRow(
                    item = item,
                    currentMemberId = currentMemberId,
                    onLikeClick = { onCommentLikeClick(item) },
                    onReplyClick = { onReplyClick(item) },
                    onMoreClick = { onCommentMoreClick(item) },
                )
            }
        }
    }
}

@Composable
private fun FeedHeader(
    feed: Feed,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = feed.profileUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(PresentationR.drawable.ic_profile_default),
            error = painterResource(PresentationR.drawable.ic_profile_default),
            fallback = painterResource(PresentationR.drawable.ic_profile_default),
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = feed.nickname,
                    style = MaterialTheme.typography.contentBold,
                    fontSize = 14.sp,
                    color = ColorBlack,
                )
                if (!feed.feedTypeName.isNullOrBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(PresentationR.drawable.ic_arrow_right),
                        contentDescription = null,
                        tint = ColorGray200,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = feed.feedTypeName.orEmpty(),
                        style = MaterialTheme.typography.contentBold,
                        fontSize = 14.sp,
                        color = ColorBlack,
                    )
                }
            }
            Text(
                text = RelativeTimeUtil.getRelativeTime(feed.feedCreatedAt),
                style = MaterialTheme.typography.subtext12,
                color = ColorGray200,
            )
        }
    }
}

@Composable
private fun DetailImage(
    picture: FeedPicture,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = picture.feedPictureUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.ic_feed_image_placeholder),
        error = painterResource(R.drawable.ic_feed_image_placeholder),
        modifier =
            modifier
                .padding(end = 8.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ColorWhite300),
    )
}

@Composable
private fun FeedActionBar(
    feed: Feed,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_comment),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.padding(4.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = feed.feedCommentsCount.toString(),
            style = MaterialTheme.typography.subtext14,
            color = ColorBlack,
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            painter =
                painterResource(
                    if (feed.feedIsLiked) R.drawable.ic_like_fill else R.drawable.ic_like,
                ),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier =
                Modifier
                    .padding(4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onLikeClick,
                    ),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = feed.feedLikesCount.toString(),
            style = MaterialTheme.typography.subtext14,
            color = ColorBlack,
        )
    }
}

@Composable
private fun CommentRow(
    item: CommentItem,
    currentMemberId: String?,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isReply = item is CommentItem.ReplyData
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    // 답글은 더 들여서 표시
                    start = if (isReply) 56.dp else 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
    ) {
        AsyncImage(
            model = item.memberProfileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(PresentationR.drawable.ic_profile_default),
            error = painterResource(PresentationR.drawable.ic_profile_default),
            fallback = painterResource(PresentationR.drawable.ic_profile_default),
            modifier =
                Modifier
                    .size(if (isReply) 28.dp else 32.dp)
                    .clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.memberNickname,
                    style = MaterialTheme.typography.contentBold,
                    fontSize = 13.sp,
                    color = ColorBlack,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = RelativeTimeUtil.getRelativeTime(item.createdAt),
                    style = MaterialTheme.typography.subtext12,
                    color = ColorGray200,
                )
            }
            Spacer(Modifier.height(4.dp))
            val displayContent =
                when (item) {
                    is CommentItem.ReplyData -> {
                        val tag = item.taggedMember?.memberNickname
                        if (tag.isNullOrBlank()) item.content else "@$tag ${item.content}"
                    }
                    else -> item.content
                }
            Text(
                text = displayContent,
                style = MaterialTheme.typography.subtext14,
                color = ColorBlack,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "답글",
                    style = MaterialTheme.typography.subtext12,
                    color = ColorGray200,
                    modifier =
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onReplyClick,
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Icon(
                    painter =
                        painterResource(
                            if (item.isLiked) R.drawable.ic_like_fill else R.drawable.ic_like,
                        ),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onLikeClick,
                            ),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.likesCount.toString(),
                    style = MaterialTheme.typography.subtext12,
                    color = ColorBlack,
                )
            }
        }
        // 내 댓글에만 더보기 노출
        if (currentMemberId != null && item.memberId == currentMemberId) {
            Icon(
                painter = painterResource(R.drawable.ic_more),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier =
                    Modifier
                        .padding(start = 8.dp)
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onMoreClick,
                        ),
            )
        }
    }
}

@Composable
private fun CommentInputBar(
    input: String,
    replyTarget: CommentItem?,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(ColorWhite300)
                .padding(vertical = 8.dp),
    ) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(ColorGray300))

        // 답글 대상 표시 (있으면 상단에 띠 형태)
        if (replyTarget != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 10.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.feed_reply_writing,
                            replyTarget.memberNickname,
                        ),
                    style = MaterialTheme.typography.subtext12,
                    color = ColorGray200,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(PresentationR.drawable.ic_close),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier =
                        Modifier
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onCancelReply,
                            ),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = input,
                onValueChange = onChange,
                textStyle = TextStyle(fontSize = 14.sp, color = ColorBlack),
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(ColorWhite300)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .heightIn(min = 24.dp, max = 130.dp)
                        .defaultMinSize(minHeight = 24.dp),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text(
                            text = stringResource(R.string.comment_input_hint),
                            style = MaterialTheme.typography.subtext14,
                            color = ColorGray200,
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                painter =
                    painterResource(
                        if (input.isNotBlank()) R.drawable.ic_send else R.drawable.ic_send_unable,
                    ),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier =
                    Modifier
                        .padding(6.dp)
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = input.isNotBlank(),
                            onClick = onSend,
                        ),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun FeedDetailScreenPreview() {
    AppTheme {
        FeedDetailScreen(
            feed = null,
            commentItems = emptyList(),
            isLoading = true,
            isLoadError = false,
            isMyFeed = false,
            currentMemberId = null,
            replyTarget = null,
            commentInput = "",
            onCommentInputChange = {},
            onSendComment = {},
            onCancelReply = {},
            onReplyClick = {},
            onCommentLikeClick = {},
            onCommentMoreClick = {},
            onFeedLikeClick = {},
            onFeedMoreClick = {},
            onBackClick = {},
        )
    }
}
