package com.bookflow.app.presentation.screens.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.flashcards.domain.Rating
import com.bookflow.app.presentation.components.BookFlowTopBar
import com.bookflow.app.presentation.components.BookFlowTopBarAction

private val ratingColors = mapOf(
    Rating.AGAIN to Color(0xFFDC2626), Rating.HARD to Color(0xFFD97706), Rating.GOOD to Color(0xFF16A34A), Rating.EASY to Color(0xFF2563EB)
)

@Composable
fun ReviewScreen(viewModel: ReviewViewModel, onBack: () -> Unit, onOpenSource: (bookId: String, page: Int) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            BookFlowTopBar(title = "Study", subtitle = if (state.current != null) "${state.remaining} left" else null, onBack = onBack) {
                if (state.canUndo) BookFlowTopBarAction(Icons.AutoMirrored.Outlined.Undo, "Undo last answer", viewModel::undo)
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val item = state.current
            if (item != null) Column(Modifier.navigationBarsPadding()) {
                HorizontalDivider()
                if (!state.revealed) {
                    Button(onClick = viewModel::reveal, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) { Text("Show answer", fontSize = 16.sp) }
                } else Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rating.entries.forEach { rating ->
                        Button(
                            onClick = { viewModel.answer(rating) },
                            colors = ButtonDefaults.buttonColors(containerColor = ratingColors.getValue(rating)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                            modifier = Modifier.weight(1f).height(60.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(state.intervals[rating].orEmpty(), fontSize = 12.sp)
                                Text(rating.title, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val item = state.current
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                item == null -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(64.dp), tint = Color(0xFF16A34A))
                    Spacer(Modifier.height(12.dp))
                    Text("Done for now", style = MaterialTheme.typography.headlineSmall)
                    Text(if (state.reviewedThisSession > 0) "You did ${state.reviewedThisSession} reviews. More appear as cards come due." else "Nothing is due in this deck right now.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) { Text("Back to deck") }
                }
                else -> {
                    val (question, answer) = item.questionAnswer(state.revealed)
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).clickable(enabled = !state.revealed, onClick = viewModel::reveal).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        CardFaceView(question, Modifier.fillMaxWidth(), fontSize = 22.sp)
                        if (answer != null) {
                            HorizontalDivider()
                            CardFaceView(answer, Modifier.fillMaxWidth(), fontSize = 20.sp)
                        }
                        val book = item.card.sourceBookId
                        val page = item.card.sourcePage
                        if (state.revealed && book != null && page != null) {
                            TextButton(onClick = { onOpenSource(book, page) }) {
                                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Show source page ${page + 1}")
                            }
                        }
                    }
                }
            }
        }
    }
}
