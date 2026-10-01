package com.bookflow.app.presentation.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.domain.model.*
import java.io.File

fun shareBook(context: Context, book: Book) {
    val uri = book.uriString?.let(Uri::parse) ?: FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(book.filePath))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(book.title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share ${book.title}"))
}

@Composable
fun BookArtwork(book: Book, modifier: Modifier = Modifier) {
    val color = runCatching { Color(android.graphics.Color.parseColor(book.coverColorHex)) }.getOrDefault(BrandPurple)
    Box(modifier.background(Brush.verticalGradient(listOf(color, Color(0xFF0C1934)))), contentAlignment = Alignment.Center) {
        if (book.thumbnailPath != null) AsyncImage(model = File(book.thumbnailPath), contentDescription = "Cover of ${book.title}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MenuBook, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(32.dp))
            Text(book.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardBookCard(book: Book, onClick: () -> Unit, onActions: () -> Unit, modifier: Modifier = Modifier, detailed: Boolean = false) {
    Card(modifier.combinedClickable(onClick = onClick, onLongClick = onActions), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
        Box {
            BookArtwork(book, Modifier.fillMaxWidth().aspectRatio(if (detailed) 1.04f else 1.12f))
            IconButton(onClick = onActions, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).background(Color.Black.copy(alpha = .3f), RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.MoreVert, "Options for ${book.title}", tint = Color.White)
            }
            if (book.isFavorite) Icon(Icons.Default.Star, "Favorite", tint = Color(0xFFFFD339), modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(18.dp))
        }
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, fontSize = if (detailed) 13.sp else 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, minLines = if (detailed) 1 else 2, overflow = TextOverflow.Ellipsis)
            Text(if (book.readingProgress > 0f) "Page ${book.currentPage + 1}${if (detailed) " / ${book.pageCount}" else ""}" else "${book.pageCount} pages", fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                LinearProgressIndicator(progress = { book.readingProgress.coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(4.dp), color = BrandPurple, trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {})
                Text("${(book.readingProgress * 100).toInt()}%", fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BookActionsDialog(book: Book, onDismiss: () -> Unit, onOpen: () -> Unit, onFavorite: () -> Unit, onCollections: () -> Unit, onInformation: () -> Unit, onShare: () -> Unit, onRemove: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(book.title) }, text = {
        Column {
            listOf("Open" to onOpen, (if (book.isFavorite) "Remove from Favorites" else "Favorite") to onFavorite,
                "Add to Collection" to onCollections, "Book Information" to onInformation, "Share" to onShare, "Remove from Library" to onRemove).forEach { (label, action) ->
                TextButton(onClick = { onDismiss(); action() }, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun BookCollectionsDialog(book: Book, collections: List<BookCollection>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var selected by remember(book.id) { mutableStateOf(book.collectionIds) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add to Collection") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (collections.isEmpty()) Text("Create a collection in the Collections tab first.")
            collections.forEach { collection ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(collection.id in selected, { checked -> selected = if (checked) selected + collection.id else selected - collection.id })
                    Text(collection.name)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(selected); onDismiss() }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
