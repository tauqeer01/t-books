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
    val uri = try {
        book.uriString?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: book.filePath.takeIf { it.isNotBlank() }?.let { path ->
                val file = File(path)
                if (file.exists()) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                else null
            }
    } catch (_: Exception) { null }

    if (uri == null) {
        android.widget.Toast.makeText(context, "Unable to share: file not found", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

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
fun BookActionsDialog(
    book: Book,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onCollections: () -> Unit,
    onInformation: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit
) {
    BookFlowBottomSheet(
        onDismissRequest = onDismiss,
        title = book.title,
        subtitle = "${book.pageCount} pages • ${book.category}",
        showCloseButton = true
    ) {
        val actions = listOf(
            Triple("Open", Icons.Default.MenuBook, onOpen),
            Triple(if (book.isFavorite) "Remove from Favorites" else "Favorite", androidx.compose.material.icons.Icons.Default.Favorite, onFavorite),
            Triple("Add to Collection", androidx.compose.material.icons.Icons.Default.FolderSpecial, onCollections),
            Triple("Book Information", androidx.compose.material.icons.Icons.Default.Info, onInformation),
            Triple("Share", androidx.compose.material.icons.Icons.Default.Share, onShare),
            Triple("Remove from Library", androidx.compose.material.icons.Icons.Default.DeleteOutline, onRemove)
        )

        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            actions.forEach { (label, icon, action) ->
                val isDestructive = label == "Remove from Library"
                Surface(
                    onClick = {
                        onDismiss()
                        action()
                    },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isDestructive) MaterialTheme.colorScheme.error else Color(0xFF475569),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDestructive) MaterialTheme.colorScheme.error else Color(0xFF0F172A)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BookCollectionsDialog(
    book: Book,
    collections: List<BookCollection>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selected by remember(book.id) { mutableStateOf(book.collectionIds) }

    BookFlowBottomSheet(
        onDismissRequest = onDismiss,
        title = "Add to Collection",
        subtitle = "Choose collections for \"${book.title}\"",
        confirmButton = {
            Button(
                onClick = {
                    onSave(selected)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            if (collections.isEmpty()) {
                Text(
                    text = "No collections found. Create a collection in the Collections tab first.",
                    fontSize = 13.5.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                collections.forEach { collection ->
                    val isChecked = collection.id in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (isChecked) selected - collection.id else selected + collection.id
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + collection.id else selected - collection.id
                            },
                            colors = CheckboxDefaults.colors(checkedColor = BrandPurple)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = collection.name,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0F172A)
                        )
                    }
                }
            }
        }
    }
}
