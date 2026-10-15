package com.bookflow.app.presentation.screens.esign

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.bookflow.app.core.util.DateUtils
import com.bookflow.app.domain.model.Book
import com.bookflow.app.esign.domain.SavedSignature
import com.bookflow.app.esign.domain.SignatureKind
import com.bookflow.app.presentation.components.BookFlowTopBar
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ESignHomeScreen(
    viewModel: ESignHomeViewModel,
    creator: SignatureCreatorViewModel,
    onSignBook: (bookId: String) -> Unit,
    onSignFile: (Uri) -> Unit,
    onOpenBook: (bookId: String) -> Unit
) {
    val context = LocalContext.current
    val signatures by viewModel.signatures.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val signed by viewModel.signed.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf<SignatureKind?>(null) }
    var managing by remember { mutableStateOf<SavedSignature?>(null) }
    var choosingBook by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onSignFile(uri)
        }
    }
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(topBar = { BookFlowTopBar(title = "Fill & Sign", subtitle = "Sign PDFs on this device") }, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Sign a document", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Add your signature, initials, text, dates, and checkmarks. A signed copy is saved; the original stays unchanged.",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f), modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SourceButton(Icons.Outlined.MenuBook, "From library", Modifier.weight(1f)) { choosingBook = true }
                            SourceButton(Icons.Outlined.FolderOpen, "From files", Modifier.weight(1f)) { filePicker.launch(arrayOf("application/pdf")) }
                        }
                    }
                }
            }
            item {
                Text("Your signature", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SignatureKind.entries.forEach { kind ->
                        SignatureTile(kind, signatures.firstOrNull { it.kind == kind }, Modifier.weight(1f),
                            onCreate = { creating = kind }, onManage = { managing = it })
                    }
                }
            }
            item { Text("Signed documents", style = MaterialTheme.typography.titleMedium) }
            if (signed.isEmpty()) item {
                Text("Documents you sign appear here and in your library.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            items(signed, key = { it.id }) { book -> SignedRow(book, onOpen = { onOpenBook(book.id) }, onShare = { share(context, book) }) }
        }
    }

    creating?.let { kind ->
        SignatureCreatorSheet(kind, creator, onSaved = { creating = null; viewModel.refresh() }, onDismiss = { creating = null })
    }
    managing?.let { signature ->
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(signature.kind.title) },
            text = { AsyncImage(File(signature.path), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)) },
            confirmButton = { TextButton(onClick = { managing = null; creating = signature.kind }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { viewModel.delete(signature); managing = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
        )
    }
    if (choosingBook) {
        ModalBottomSheet(onDismissRequest = { choosingBook = false }) {
            Text("Choose a document", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            if (library.isEmpty()) Text("Your library is empty. Use “From files” instead.", modifier = Modifier.padding(20.dp))
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(library, key = { it.id }) { book ->
                    ListItem(headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${book.pageCount} pages") },
                        leadingContent = { Icon(Icons.Outlined.Description, null) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { choosingBook = false; onSignBook(book.id) })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SourceButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Row(Modifier.padding(vertical = 14.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SignatureTile(kind: SignatureKind, signature: SavedSignature?, modifier: Modifier, onCreate: () -> Unit, onManage: (SavedSignature) -> Unit) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(96.dp).background(Color.White, RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { if (signature == null) onCreate() else onManage(signature) }),
            contentAlignment = Alignment.Center
        ) {
            if (signature != null) {
                // The path changes on every save, so the preview never shows a stale image
                AsyncImage(File(signature.path), "${kind.title} preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
                Text("Create", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(kind.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
    }
}

@Composable
private fun SignedRow(book: Book, onOpen: () -> Unit, onShare: () -> Unit) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color(0xFFEDE9FE), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Draw, null, tint = Color(0xFF6D28D9))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(book.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Signed ${DateUtils.formatRelative(book.addedTimestamp)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, "Share ${book.title}") }
        }
    }
}

private fun share(context: android.content.Context, book: Book) {
    val file = (book.uriString?.let { Uri.parse(it).path } ?: book.filePath).let(::File)
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share signed PDF"))
    }.onFailure { android.widget.Toast.makeText(context, "Couldn't share this file", android.widget.Toast.LENGTH_SHORT).show() }
}
