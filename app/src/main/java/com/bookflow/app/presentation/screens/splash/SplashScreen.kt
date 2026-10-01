package com.bookflow.app.presentation.screens.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookflow.app.R
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.presentation.components.BookFlowEmblem
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val onFinished by rememberUpdatedState(onSplashFinished)
    LaunchedEffect(Unit) { delay(1200); onFinished() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFF8F8FF), Color(0xFFF2F0FF), Color(0xFFE9E6FF)))).safeDrawingPadding()) {
        val compact = maxHeight < 550.dp
        Column(Modifier.fillMaxSize().padding(top = if (compact) 12.dp else 48.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BookFlowEmblem(Modifier.size(if (compact) 64.dp else 112.dp))
            Text(buildAnnotatedString { append("Book"); withStyle(SpanStyle(color = BrandPurple)) { append("Flow") } }, fontSize = if (compact) 30.sp else 42.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF101326), letterSpacing = (-1.5).sp)
            Text("Your Reading Companion", fontSize = 17.sp, color = Color(0xFF666B85))
            if (!compact) {
                Row(Modifier.fillMaxWidth().widthIn(max = 500.dp).padding(horizontal = 36.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val items = listOf(Triple(Icons.Default.MenuBook, "Read", BrandPurple), Triple(Icons.Default.Edit, "Highlight", Color(0xFFFFB800)), Triple(Icons.Default.Description, "Take Notes", Color(0xFF176CFF)), Triple(Icons.Default.FolderOpen, "Organize", Color(0xFF00B889)))
                    items.forEach { (icon, label, tint) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = .13f)) { Icon(icon, null, Modifier.padding(10.dp).size(26.dp), tint = tint) }
                            Text(label, Modifier.padding(top = 6.dp), fontSize = 11.sp, color = Color(0xFF444A65))
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.splash_book), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.width(180.dp).height(5.dp), color = BrandPurple, trackColor = Color(0xFFDAD5E9))
            Text("Opening your library…", Modifier.padding(top = 14.dp), fontSize = 14.sp, color = Color(0xFF666B85))
        }
    }
}
