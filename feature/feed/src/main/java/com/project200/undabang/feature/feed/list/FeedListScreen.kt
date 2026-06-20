package com.project200.undabang.feature.feed.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.subtext12
import com.project200.presentation.compose.theme.subtext14
import com.project200.presentation.utils.RelativeTimeUtil
import com.project200.undabang.feature.feed.R
import kotlinx.coroutines.flow.distinctUntilChanged
import com.project200.undabang.presentation.R as PresentationR

@Composable
fun FeedListScreen(
    feeds: List<Feed>,
    selectedTypeName: String?,
    isLoading: Boolean,
    isEmpty: Boolean,
    onFeedClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onCategoryClick: () -> Unit,
    onClearCategory: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()

    // 스크롤 위치를 감지해서 마지막 보이는 아이템이 끝에서 5개 이내에 들어오면 추가 페이지 요청
    // 기존 addOnScrollListener 패턴을 LazyListState + snapshotFlow로 대체
    LaunchedEffect(lazyListState, feeds.size) {
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to lastVisible
        }
            .distinctUntilChanged()
            .collect { (total, lastVisible) ->
                if (total > 0 && total <= lastVisible + 5) {
                    onLoadMore()
                }
            }
    }

    UndabangScaffold(
        modifier = modifier,
        topBar = {
            // 카테고리 선택 모드일 때는 뒤로가기 + 카테고리명을 제목으로, 일반 모드일 때는 카테고리/추가 액션을 노출
            UndabangTopBar(
                title =
                    selectedTypeName
                        ?: stringResource(R.string.feed_title),
                navigationIconVisible = selectedTypeName != null,
                onNavigationClick = onClearCategory,
                actions = {
                    if (selectedTypeName == null) {
                        IconButton(onClick = onCategoryClick) {
                            Icon(
                                painter = painterResource(R.drawable.ic_category),
                                contentDescription = null,
                                tint = Color.Unspecified,
                            )
                        }
                    }
                    IconButton(onClick = onAddClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_feed_add),
                            contentDescription = null,
                            tint = Color.Unspecified,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            // 첫 페이지 로딩 중에는 인디케이터만, 페이지 더 불러올 때는 기존 리스트 유지
            val showInitialLoading = isLoading && feeds.isEmpty()

            when {
                showInitialLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                isEmpty -> {
                    Text(
                        text = stringResource(R.string.feed_empty_message),
                        style = MaterialTheme.typography.subtext14,
                        color = ColorGray200,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(feeds, key = { it.feedId }) { feed ->
                            FeedCard(
                                feed = feed,
                                onClick = { onFeedClick(feed.feedId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedCard(
    feed: Feed,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(bottom = 24.dp),
    ) {
        Row(
            modifier =
                Modifier
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
            Spacer(Modifier.width(6.dp))
            Text(
                text = RelativeTimeUtil.getRelativeTime(feed.feedCreatedAt),
                style = MaterialTheme.typography.subtext12,
                color = ColorGray200,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = feed.feedContent.orEmpty(),
            style = MaterialTheme.typography.subtext14,
            color = ColorBlack,
            maxLines = 6,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp).padding(start = 52.dp - 16.dp),
        )

        if (feed.feedPictures.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 52.dp, end = 16.dp),
            ) {
                items(feed.feedPictures, key = { it.feedPictureId }) { picture ->
                    FeedImage(picture = picture)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.padding(start = 52.dp),
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
                modifier = Modifier.padding(4.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = feed.feedLikesCount.toString(),
                style = MaterialTheme.typography.subtext14,
                color = ColorBlack,
            )
        }
    }
}

@Composable
private fun FeedImage(
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
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ColorWhite300),
    )
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun FeedListScreenPreview() {
    AppTheme {
        FeedListScreen(
            feeds = emptyList(),
            selectedTypeName = null,
            isLoading = false,
            isEmpty = true,
            onFeedClick = {},
            onAddClick = {},
            onCategoryClick = {},
            onClearCategory = {},
            onLoadMore = {},
        )
    }
}
