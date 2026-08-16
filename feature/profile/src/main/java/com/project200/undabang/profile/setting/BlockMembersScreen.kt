package com.project200.undabang.profile.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project200.domain.model.BlockedMember
import com.project200.presentation.compose.components.display.UndabangAvatar
import com.project200.presentation.compose.components.layout.UndabangTopBar
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorBlack
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.contentBold
import com.project200.presentation.compose.theme.contentRegular
import com.project200.undabang.feature.profile.R

@Composable
fun BlockMembersScreen(
    members: List<BlockedMember>,
    onBackClick: () -> Unit,
    onUnblockClick: (BlockedMember) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(ColorWhite300),
    ) {
        UndabangTopBar(
            title = stringResource(R.string.block_members),
            onNavigationClick = onBackClick,
        )

        if (members.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.block_members_empty),
                    style = MaterialTheme.typography.contentRegular,
                    color = ColorGray200,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = members, key = { it.memberBlockId }) { member ->
                    BlockMemberItem(
                        member = member,
                        onUnblockClick = { onUnblockClick(member) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockMemberItem(
    member: BlockedMember,
    onUnblockClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    val strokePx = 0.3.dp.toPx()
                    drawLine(
                        color = ColorBlack,
                        start = Offset(0f, size.height - strokePx / 2),
                        end = Offset(size.width, size.height - strokePx / 2),
                        strokeWidth = strokePx,
                    )
                }
                .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UndabangAvatar(
            imageUrl = member.thumbnailImageUrl ?: member.profileImageUrl,
            size = 50.dp,
            borderWidth = 0.dp,
        )
        Spacer(Modifier.width(15.dp))
        Text(
            text = member.nickname,
            style = MaterialTheme.typography.contentBold,
            color = ColorBlack,
            modifier = Modifier.weight(1f),
        )
        UnblockButton(onClick = onUnblockClick)
    }
}

@Composable
private fun UnblockButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ColorGray200)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.unblock),
            color = ColorWhite300,
            fontSize = 14.sp,
            style = MaterialTheme.typography.contentBold,
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun BlockMembersScreenPreview() {
    AppTheme {
        BlockMembersScreen(
            members =
                listOf(
                    BlockedMember(1L, "id1", "운다방테스터1", null, null),
                    BlockedMember(2L, "id2", "운다방테스터2", null, null),
                    BlockedMember(3L, "id3", "긴닉네임을가진사용자입니다", null, null),
                ),
            onBackClick = {},
            onUnblockClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun BlockMembersScreenEmptyPreview() {
    AppTheme {
        BlockMembersScreen(
            members = emptyList(),
            onBackClick = {},
            onUnblockClick = {},
        )
    }
}
