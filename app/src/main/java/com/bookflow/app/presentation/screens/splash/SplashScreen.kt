package com.bookflow.app.presentation.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.core.theme.BrandPurpleLight
import com.bookflow.app.core.theme.FeatureBlueBg
import com.bookflow.app.core.theme.FeatureBlueText
import com.bookflow.app.core.theme.FeatureGreenBg
import com.bookflow.app.core.theme.FeatureGreenText
import com.bookflow.app.core.theme.FeaturePurpleBg
import com.bookflow.app.core.theme.FeaturePurpleText
import com.bookflow.app.core.theme.FeatureYellowBg
import com.bookflow.app.core.theme.FeatureYellowText
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val progress = remember { Animatable(0.1f) }
    val scale = remember { Animatable(0.92f) }
    val alpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floating"
    )

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(400))
        scale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        progress.animateTo(1f, tween(1400, easing = LinearEasing))
        delay(200)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF1F5FD),
                        Color(0xFFE8EEFA),
                        Color(0xFFE3EAFA)
                    )
                )
            )
            .statusBarsPadding()
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Logo & Branding & Features
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(scale.value)
                    .alpha(alpha.value)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // 3D Glowing Purple Emblem (from screenshot)
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF6366F1),
                                    Color(0xFF4F46E5),
                                    Color(0xFF4338CA)
                                )
                            )
                        )
                        .shadow(16.dp, RoundedCornerShape(24.dp), spotColor = Color(0x664F46E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = "BookFlow Icon",
                        tint = Color.White,
                        modifier = Modifier.size(62.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // App Title with dual tone: "Book" in black, "Flow" in purple
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color(0xFF0F172A), fontWeight = FontWeight.ExtraBold)) {
                            append("Book")
                        }
                        withStyle(SpanStyle(color = BrandPurple, fontWeight = FontWeight.ExtraBold)) {
                            append("Flow")
                        }
                    },
                    style = MaterialTheme.typography.displayMedium,
                    fontSize = 38.sp,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Your Reading Companion",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(26.dp))

                // 4 Feature Chips in a row (Matching Screenshot)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SplashFeatureChip(
                        icon = Icons.Default.MenuBook,
                        label = "Read",
                        bgColor = FeaturePurpleBg,
                        iconColor = FeaturePurpleText
                    )
                    SplashFeatureChip(
                        icon = Icons.Default.Edit,
                        label = "Highlight",
                        bgColor = FeatureYellowBg,
                        iconColor = FeatureYellowText
                    )
                    SplashFeatureChip(
                        icon = Icons.Default.Description,
                        label = "Take Notes",
                        bgColor = FeatureBlueBg,
                        iconColor = FeatureBlueText
                    )
                    SplashFeatureChip(
                        icon = Icons.Default.Folder,
                        label = "Organize",
                        bgColor = FeatureGreenBg,
                        iconColor = FeatureGreenText
                    )
                }
            }

            // Middle Section: Magical Open Book Illustration with Floating Particle Cards
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glow in center
                Canvas(modifier = Modifier.size(240.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                Color(0x66A5B4FC),
                                Color(0x22818CF8),
                                Color.Transparent
                            )
                        ),
                        radius = size.width / 2
                    )
                }

                // Floating Icons around book (Read, Highlight, Notes, Organize)
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .align(Alignment.TopCenter)
                        .padding(top = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FloatingIconCard(
                        icon = Icons.Default.MenuBook,
                        color = FeaturePurpleText,
                        bgColor = FeaturePurpleBg,
                        offsetY = floatAnim
                    )
                    FloatingIconCard(
                        icon = Icons.Default.Description,
                        color = FeatureBlueText,
                        bgColor = FeatureBlueBg,
                        offsetY = -floatAnim
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .align(Alignment.Center)
                        .padding(bottom = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FloatingIconCard(
                        icon = Icons.Default.Edit,
                        color = FeatureYellowText,
                        bgColor = FeatureYellowBg,
                        offsetY = -floatAnim * 0.8f
                    )
                    FloatingIconCard(
                        icon = Icons.Default.Folder,
                        color = FeatureGreenText,
                        bgColor = FeatureGreenBg,
                        offsetY = floatAnim * 0.8f
                    )
                }

                // Big Open Book graphic at bottom of illustration
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(130.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2

                    // Book Cover Base (Purple)
                    val coverPath = Path().apply {
                        moveTo(10f, h - 10f)
                        quadraticTo(cx, h + 15f, w - 10f, h - 10f)
                        lineTo(w - 6f, h)
                        quadraticTo(cx, h + 24f, 6f, h)
                        close()
                    }
                    drawPath(coverPath, Color(0xFF3730A3))

                    // White Pages Fan
                    for (i in 0..6) {
                        val curveOffset = (i * 5).toFloat()
                        val pagePath = Path().apply {
                            moveTo(cx, h - 10f - (i * 2))
                            quadraticTo(cx * 0.45f, h - 60f + curveOffset, 20f + (i * 4), h - 30f + curveOffset)
                            lineTo(22f + (i * 4), h - 22f + curveOffset)
                            quadraticTo(cx * 0.5f, h - 50f + curveOffset, cx, h - 8f - (i * 2))
                            close()
                        }
                        drawPath(pagePath, Color(0xFFFAF9F6).copy(alpha = 0.95f))

                        val rightPagePath = Path().apply {
                            moveTo(cx, h - 10f - (i * 2))
                            quadraticTo(cx * 1.55f, h - 60f + curveOffset, w - 20f - (i * 4), h - 30f + curveOffset)
                            lineTo(w - 22f - (i * 4), h - 22f + curveOffset)
                            quadraticTo(cx * 1.5f, h - 50f + curveOffset, cx, h - 8f - (i * 2))
                            close()
                        }
                        drawPath(rightPagePath, Color(0xFFFFFFFF).copy(alpha = 0.95f))
                    }
                }
            }

            // Bottom Section: Progress Bar and Loading Text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .width(180.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = BrandPurple,
                    trackColor = Color(0xFFCBD5E1)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Opening your library...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SplashFeatureChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bgColor: Color,
    iconColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF334155),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun FloatingIconCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    bgColor: Color,
    offsetY: Float
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .shadow(4.dp, RoundedCornerShape(10.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
    }
}
