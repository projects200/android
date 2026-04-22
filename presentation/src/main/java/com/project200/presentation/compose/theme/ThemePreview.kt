package com.project200.presentation.compose.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview(showBackground = true)
@Composable
fun ColorPalettePreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "컬러 팔레트",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            ColorRow("Main Color", ColorMain)
            ColorRow("Main Background", ColorMainBackground)
            ColorRow("Error Red", ColorErrorRed)
            ColorRow("White 300", ColorWhite300)
            ColorRow("White 200", ColorWhite200)
            ColorRow("White 100", ColorWhite100)
            ColorRow("Gray 100", ColorGray100)
            ColorRow("Gray 200", ColorGray200)
            ColorRow("Gray 300", ColorGray300)
            ColorRow("Black", ColorBlack)

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "점수 레벨",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            ColorRow("High Level", ColorScoreHighLevel)
            ColorRow("Middle Level", ColorScoreMiddleLevel)
            ColorRow("Low Level", ColorScoreLowLevel)
        }
    }
}

@Composable
private fun ColorRow(label: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = color,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "#${color.toArgb().toUInt().toString(16).uppercase().takeLast(6)}",
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TypographyPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "타이포그래피",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Text(
                text = "Display Large (22sp, Bold)",
                style = AppTypography.displayLarge
            )

            Text(
                text = "Display Medium (18sp, Bold)",
                style = AppTypography.displayMedium
            )

            Text(
                text = "Body Large (15sp, Normal)",
                style = AppTypography.bodyLarge
            )

            Text(
                text = "Body Medium (14sp, Normal)",
                style = AppTypography.bodyMedium
            )

            Text(
                text = "Body Small (12sp, Normal)",
                style = AppTypography.bodySmall
            )

            Text(
                text = "Label Large (15sp, SemiBold)",
                style = AppTypography.labelLarge
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ShapesPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "쉐이프/코너",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            color = ColorMain,
                            shape = AppShapes.small
                        )
                )
                Text("Small (5dp)")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            color = ColorMain,
                            shape = AppShapes.medium
                        )
                )
                Text("Medium (8dp)")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            color = ColorMain,
                            shape = AppShapes.large
                        )
                )
                Text("Large (12dp)")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            color = ColorMain,
                            shape = AppShapes.extraLarge
                        )
                )
                Text("ExtraLarge (16dp)")
            }
        }
    }
}

private fun Color.toArgb(): Int {
    return ((alpha * 255).toInt() shl 24) or
        ((red * 255).toInt() shl 16) or
        ((green * 255).toInt() shl 8) or
        (blue * 255).toInt()
}
