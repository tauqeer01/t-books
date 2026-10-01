package com.bookflow.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.bookflow.app.core.theme.BrandPurple

@Composable
fun BookFlowEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier.background(BrandPurple.copy(alpha = .12f), RoundedCornerShape(22.dp)).padding(10.dp)) {
        scale(size.width / 100, size.height / 100, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val brush = Brush.verticalGradient(listOf(Color(0xFF9955FF), BrandPurple, Color(0xFF1326CC)), endY = 95f)
            drawPath(Path().apply { moveTo(47f, 30f); cubicTo(35f, 12f, 17f, 10f, 9f, 12f); lineTo(9f, 75f); cubicTo(25f, 75f, 37f, 80f, 47f, 91f); close() }, brush)
            drawPath(Path().apply { moveTo(53f, 30f); cubicTo(65f, 12f, 83f, 10f, 91f, 12f); lineTo(91f, 75f); cubicTo(75f, 75f, 63f, 80f, 53f, 91f); close() }, brush)
            drawPath(Path().apply { moveTo(2f, 23f); lineTo(5f, 23f); lineTo(5f, 79f); cubicTo(27f, 79f, 34f, 85f, 44f, 95f); cubicTo(25f, 85f, 12f, 85f, 2f, 87f); close() }, brush)
            drawPath(Path().apply { moveTo(98f, 23f); lineTo(95f, 23f); lineTo(95f, 79f); cubicTo(73f, 79f, 66f, 85f, 56f, 95f); cubicTo(75f, 85f, 88f, 85f, 98f, 87f); close() }, brush)
        }
    }
}
