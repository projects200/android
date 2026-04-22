package com.project200.presentation.compose.components.button

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.project200.presentation.compose.theme.ColorGray200
import com.project200.presentation.compose.theme.ColorGray300
import com.project200.presentation.compose.theme.ColorMain
import com.project200.presentation.compose.theme.ColorWhite100
import com.project200.presentation.compose.theme.ColorWhite300
import com.project200.presentation.compose.theme.subtext14

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isCompact: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isCompact) 36.dp else 55.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ColorMain,
            contentColor = ColorWhite300,
            disabledContainerColor = ColorGray200,
            disabledContentColor = ColorWhite300,
        ),
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (isCompact) 8.dp else 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isCompact: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isCompact) 36.dp else 55.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ColorGray300,
            contentColor = ColorWhite300,
            disabledContainerColor = ColorGray200,
            disabledContentColor = ColorWhite300,
        ),
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (isCompact) 8.dp else 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun TextButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isCompact: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(if (isCompact) 32.dp else 44.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ColorWhite100,
            contentColor = ColorMain,
            disabledContentColor = ColorGray200
        ),
        shape = MaterialTheme.shapes.small,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = if (isCompact) 4.dp else 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.subtext14,
        )
    }
}
