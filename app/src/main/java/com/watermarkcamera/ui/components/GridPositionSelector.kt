package com.watermarkcamera.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.watermarkcamera.watermark.WatermarkAlignment

/**
 * 9宫格位置选择器
 */
@Composable
fun GridPositionSelector(
    selected: WatermarkAlignment,
    onSelected: (WatermarkAlignment) -> Unit,
    modifier: Modifier = Modifier
) {
    val alignments = listOf(
        listOf(WatermarkAlignment.TOP_LEFT, WatermarkAlignment.TOP, WatermarkAlignment.TOP_RIGHT),
        listOf(WatermarkAlignment.LEFT, WatermarkAlignment.CENTER, WatermarkAlignment.RIGHT),
        listOf(WatermarkAlignment.BOTTOM_LEFT, WatermarkAlignment.BOTTOM, WatermarkAlignment.BOTTOM_RIGHT)
    )

    Column(
        modifier = modifier
            .background(
                Color.LightGray.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        alignments.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { alignment ->
                    PositionButton(
                        alignment = alignment,
                        isSelected = alignment == selected,
                        onClick = { onSelected(alignment) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionButton(
    alignment: WatermarkAlignment,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.White
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Gray
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (alignment) {
                WatermarkAlignment.TOP_LEFT -> "↖"
                WatermarkAlignment.TOP -> "↑"
                WatermarkAlignment.TOP_RIGHT -> "↗"
                WatermarkAlignment.LEFT -> "←"
                WatermarkAlignment.CENTER -> "●"
                WatermarkAlignment.RIGHT -> "→"
                WatermarkAlignment.BOTTOM_LEFT -> "↙"
                WatermarkAlignment.BOTTOM -> "↓"
                WatermarkAlignment.BOTTOM_RIGHT -> "↘"
            },
            color = if (isSelected) Color.White else Color.Gray
        )
    }
}
