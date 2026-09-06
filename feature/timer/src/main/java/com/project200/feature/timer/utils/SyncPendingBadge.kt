package com.project200.feature.timer.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.ColorGray100
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.subtext10
import com.project200.undabang.feature.timer.R

/**
 * 서버에 아직 반영되지 않은 타이머 행에 붙이는 표기입니다.
 *
 * 공용 컴포넌트가 아직 없어 화면 안에 가볍게 두었습니다. 나중에 공용 컴포넌트가 생기면
 * 이 파일 하나만 갈아 끼우면 되도록, 심플/커스텀 타이머 화면은 모두 이 컴포저블을 씁니다
 */
@Composable
fun SyncPendingBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.timer_sync_pending),
        style = MaterialTheme.typography.subtext10,
        color = ColorGray100,
        modifier =
            modifier
                .clip(RoundedCornerShape(4.dp))
                .background(ColorGray300)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
