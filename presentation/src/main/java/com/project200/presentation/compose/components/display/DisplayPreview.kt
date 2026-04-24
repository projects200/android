package com.project200.presentation.compose.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.project200.presentation.compose.theme.AppTheme
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite300

@Preview(showBackground = true)
@Composable
fun AvatarPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("프로필 아바타", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UndabangAvatar(imageUrl = null, size = 32.dp)
                UndabangAvatar(imageUrl = null, size = 48.dp)
                UndabangAvatar(imageUrl = null, size = 64.dp)
            }

            Text("다양한 크기", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CardPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("카드 컴포넌트", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            UndabangCard(
                backgroundColor = ColorWhite300
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "운동 종류",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "헬스",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            UndabangCard(
                backgroundColor = ColorMain.copy(alpha = 0.1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "점수",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "85점",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorMain
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BadgePreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("뱃지 컴포넌트", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UndabangBadge(count = 1)
                UndabangBadge(count = 5)
                UndabangBadge(count = 99)
                UndabangBadge(count = 100)
            }

            Text("카운트별 뱃지", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DotPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("도트 표시기", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UndabangDot(size = 8.dp)
                UndabangDot(size = 12.dp)
                UndabangDot(size = 16.dp)
            }

            Text("크기별 도트", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DisplayComponentsGroupPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ColorGray300)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Display 컴포넌트 조합", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ColorWhite300, shape = androidx.compose.material3.MaterialTheme.shapes.medium)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UndabangAvatar(imageUrl = null, size = 48.dp)

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "사용자 이름",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "최근 활동: 30분 전",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                UndabangBadge(count = 3)
            }
        }
    }
}
