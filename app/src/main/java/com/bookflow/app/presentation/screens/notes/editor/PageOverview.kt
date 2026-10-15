package com.bookflow.app.presentation.screens.notes.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bookflow.app.notes.domain.PAGE_HEIGHT
import com.bookflow.app.notes.domain.PAGE_WIDTH
import com.bookflow.app.presentation.screens.notes.export.NotePageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** All pages as thumbnails: tap to jump, arrows to reorder. */
@Composable
fun PageOverview(pages: List<EditorPage>, current: Int, onOpen: (Int) -> Unit, onMove: (String, Int) -> Unit) {
    Column {
        Text("Pages", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(max = 600.dp)
        ) {
            itemsIndexed(pages, key = { _, p -> p.id }) { index, page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PageThumbnail(page, Modifier.fillMaxWidth().aspectRatio(PAGE_WIDTH / PAGE_HEIGHT).clip(RoundedCornerShape(4.dp))
                        .border(if (index == current) 2.5.dp else 1.dp, if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                        .clickable { onOpen(index) })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMove(page.id, -1) }, enabled = index > 0, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Move page ${index + 1} earlier") }
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { onMove(page.id, 1) }, enabled = index < pages.lastIndex, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Move page ${index + 1} later") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageThumbnail(page: EditorPage, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, page) {
        value = withContext(Dispatchers.Default) { runCatching { NotePageRenderer.renderBitmap(context, page, widthPx = 280) }.getOrNull() }
    }
    Box(modifier.background(Color.White)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Page", modifier = Modifier.fillMaxSize()) }
    }
}
