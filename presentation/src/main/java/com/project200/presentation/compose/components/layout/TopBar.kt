package com.project200.presentation.compose.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.mediumBold

@Composable
fun UndabangTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigationClick: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navigationIconVisible: Boolean = true,
    actionIcon1: ImageVector? = null,
    actionIcon1Description: String = "",
    onAction1Click: (() -> Unit)? = null,
    actionIcon2: ImageVector? = null,
    actionIcon2Description: String = "",
    onAction2Click: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(65.dp)
            .background(color = MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (navigationIconVisible) {
            IconButton(
                onClick = { onNavigationClick?.invoke() },
                modifier = Modifier.weight(0.1f)
            ) {
                Icon(
                    imageVector = navigationIcon,
                    contentDescription = "뒤로가기",
                    tint = ColorWhite300
                )
            }
        } else {
            Box(modifier = Modifier.weight(0.1f))
        }

        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            style = MaterialTheme.typography.mediumBold,
            color = ColorWhite300,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.weight(0.25f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (actionIcon2 != null && onAction2Click != null) {
                IconButton(onClick = onAction2Click) {
                    Icon(
                        imageVector = actionIcon2,
                        contentDescription = actionIcon2Description,
                        tint = ColorWhite300,
                    )
                }
            }

            if (actionIcon1 != null && onAction1Click != null) {
                IconButton(onClick = onAction1Click) {
                    Icon(
                        imageVector = actionIcon1,
                        contentDescription = actionIcon1Description,
                        tint = ColorWhite300,
                    )
                }
            }

            actions()
        }
    }
}
