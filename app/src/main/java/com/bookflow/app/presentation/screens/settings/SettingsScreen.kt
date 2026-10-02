package com.bookflow.app.presentation.screens.settings

import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.foundation.selection.selectable
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.presentation.components.BookFlowBottomSheet
import com.bookflow.app.presentation.components.BookFlowTopBar
import com.bookflow.app.presentation.components.BookFlowTopBarAction
import com.bookflow.app.presentation.components.BookFlowConfirmationSheet
import com.bookflow.app.presentation.components.BookFlowEmblem
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onCollections: () -> Unit = {},
    onLibrary: () -> Unit = {},
    onOpenLegal: (com.bookflow.app.presentation.screens.legal.LegalDocument) -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs = state.preferences
    val context = LocalContext.current

    var editorSection by remember { mutableStateOf<String?>(null) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    var showStorageDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        topBar = {
            BookFlowTopBar(title = "Settings", subtitle = "Customize your reading experience") {
                BookFlowTopBarAction(
                    icon = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = "Search settings",
                    selected = isSearching,
                    onClick = {
                        isSearching = !isSearching
                        if (!isSearching) searchQuery = ""
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val wide = maxWidth > 700.dp
            val horizontalPadding = if (wide) 64.dp else 18.dp

            val scheme = MaterialTheme.colorScheme
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Settings search (toggled from the header)
                if (isSearching) item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search settings...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = BrandPurple,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, tint = BrandPurple, modifier = Modifier.size(20.dp))
                        }
                    )
                }

                // App Profile Card
                if (searchQuery.isBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 0.5.dp,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAboutDialog = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BookFlowEmblem(Modifier.size(50.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp)
                                ) {
                                    Text(
                                        text = "BookFlow",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Version ${com.bookflow.app.BuildConfig.VERSION_NAME}\nYour Reading Companion",
                                        fontSize = 12.5.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Groups of working, redesigned settings
                val groups = listOf(
                    SettingGroup(
                        title = "Reading Preferences",
                        items = listOf(
                            SettingRowData(
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                iconTint = Color(0xFF5935FF),
                                iconBg = Color(0xFFEDE9FE),
                                title = "Reading Mode",
                                subtitle = "Vertical, Horizontal, Single Page",
                                value = prefs.scrollMode.displayName,
                                onClick = { editorSection = "Reading Mode" }
                            ),
                            SettingRowData(
                                icon = Icons.Default.DarkMode,
                                iconTint = Color(0xFF6366F1),
                                iconBg = Color(0xFFE0E7FF),
                                title = "App Theme",
                                subtitle = "Light or dark app appearance",
                                value = appTheme.displayName,
                                onClick = { showThemeDialog = true }
                            ),
                            SettingRowData(
                                icon = Icons.Default.LightMode,
                                iconTint = Color(0xFF10B981),
                                iconBg = Color(0xFFD1FAE5),
                                title = "Page Theme",
                                subtitle = "Reader page colors: Light, Dark, Sepia, Mint",
                                value = prefs.readerTheme.displayName,
                                onClick = { editorSection = "Appearance" }
                            ),
                            SettingRowData(
                                icon = Icons.Default.TextFields,
                                iconTint = Color(0xFF3B82F6),
                                iconBg = Color(0xFFDBEAFE),
                                title = "Text & Display",
                                subtitle = "Page spacing, rendering quality",
                                value = "${prefs.pageSpacing} dp",
                                onClick = { editorSection = "Text & Display" }
                            ),
                            SettingRowData(
                                icon = Icons.Default.Description,
                                iconTint = Color(0xFFF59E0B),
                                iconBg = Color(0xFFFEF3C7),
                                title = "Page Navigation",
                                subtitle = "Volume keys, screen orientation",
                                value = prefs.orientation.displayName,
                                onClick = { editorSection = "Page Navigation" }
                            ),
                            SettingRowData(
                                icon = Icons.Default.Visibility,
                                iconTint = Color(0xFF8B5CF6),
                                iconBg = Color(0xFFEDE9FE),
                                title = "Screen & Auto Lock",
                                subtitle = "Keep screen awake, brightness",
                                value = if (prefs.keepScreenOn) "Always On" else "System",
                                onClick = { editorSection = "Screen & Auto Lock" }
                            )
                        )
                    ),
                    SettingGroup(
                        title = "Annotations",
                        items = listOf(
                            SettingRowData(
                                icon = Icons.Default.BorderColor,
                                iconTint = Color(0xFFEAB308),
                                iconBg = Color(0xFFFEF3C7),
                                title = "Highlight Colors",
                                subtitle = "Manage highlight color palette",
                                isColorPalette = true,
                                onClick = { editorSection = "Highlight Colors" }
                            ),
                            SettingRowData(
                                icon = Icons.Default.Create,
                                iconTint = Color(0xFFF43F5E),
                                iconBg = Color(0xFFFFE4E6),
                                title = "Pen & Drawing",
                                subtitle = "Stroke size, color, stylus settings",
                                value = "${"%.1f".format(prefs.penWidth)} dp",
                                onClick = { editorSection = "Pen & Drawing" }
                            )
                        )
                    ),
                    SettingGroup(
                        title = "Library & Files",
                        items = listOf(
                            SettingRowData(
                                icon = Icons.Default.Layers,
                                iconTint = Color(0xFF6366F1),
                                iconBg = scheme.primaryContainer,
                                title = "Library View",
                                subtitle = "Grid/List view, sort order",
                                value = if (prefs.libraryGrid) "Grid" else "List",
                                onClick = { editorSection = "Library View" }
                            ),
                            SettingRowData(
                                icon = Icons.Default.DeleteOutline,
                                iconTint = Color(0xFF0D9488),
                                iconBg = Color(0xFFCCFBF1),
                                title = "Storage & Cache",
                                subtitle = "Free up temporary PDF render cache",
                                value = formatBytes(state.cacheSizeBytes),
                                onClick = { showStorageDialog = true }
                            )
                        )
                    ),
                    SettingGroup(
                        title = "Privacy & Security",
                        items = listOf(
                            SettingRowData(
                                icon = Icons.Default.Lock,
                                iconTint = Color(0xFFE11D48),
                                iconBg = Color(0xFFFFE4E6),
                                title = "Privacy",
                                subtitle = "Your data stays on your device",
                                value = "100% Offline",
                                onClick = { showPrivacyDialog = true }
                            )
                        )
                    ),
                    SettingGroup(
                        title = "About",
                        items = listOf(
                            SettingRowData(
                                icon = Icons.Default.Info,
                                iconTint = Color(0xFF7C3AED),
                                iconBg = Color(0xFFEDE9FE),
                                title = "About BookFlow",
                                subtitle = "Version and app information",
                                value = "v${com.bookflow.app.BuildConfig.VERSION_NAME}",
                                onClick = { showAboutDialog = true }
                            ),
                            SettingRowData(
                                icon = Icons.Default.Lock,
                                iconTint = Color(0xFF2563EB),
                                iconBg = Color(0xFFDBEAFE),
                                title = "Privacy policy",
                                subtitle = "How BookFlow handles your data",
                                onClick = { onOpenLegal(com.bookflow.app.presentation.screens.legal.LegalDocument.PRIVACY) }
                            ),
                            SettingRowData(
                                icon = Icons.Default.Description,
                                iconTint = scheme.onSurfaceVariant,
                                iconBg = scheme.surfaceVariant,
                                title = "Terms of use",
                                subtitle = "Rules for using BookFlow",
                                onClick = { onOpenLegal(com.bookflow.app.presentation.screens.legal.LegalDocument.TERMS) }
                            ),
                            SettingRowData(
                                icon = Icons.Default.Code,
                                iconTint = Color(0xFF0891B2),
                                iconBg = Color(0xFFCFFAFE),
                                title = "Open-source licenses",
                                subtitle = "Libraries that BookFlow is built with",
                                onClick = onOpenLicenses
                            ),
                            SettingRowData(
                                icon = Icons.Default.BugReport,
                                iconTint = Color(0xFFD97706),
                                iconBg = Color(0xFFFEF3C7),
                                title = "Report a problem",
                                subtitle = "Share a crash report or describe an issue",
                                onClick = { showReportDialog = true }
                            ),
                            SettingRowData(
                                icon = Icons.Default.Share,
                                iconTint = Color(0xFF059669),
                                iconBg = Color(0xFFD1FAE5),
                                title = "Share App",
                                subtitle = "Share BookFlow with friends",
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "BookFlow — a fast, private PDF reader for Android: https://play.google.com/store/apps/details?id=${context.packageName}"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share BookFlow"))
                                }
                            )
                        )
                    )
                )

                // Render groups
                groups.forEach { group ->
                    val filteredItems = group.items.filter {
                        searchQuery.isBlank() ||
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                it.subtitle.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredItems.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = group.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                                )

                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 0.5.dp,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        filteredItems.forEachIndexed { index, itemData ->
                                            SettingRow(item = itemData)
                                            if (index < filteredItems.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    thickness = 1.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // Preference Editors
    editorSection?.let { section ->
        ReadingPreferencesEditor(
            initial = prefs,
            section = section,
            onDismiss = { editorSection = null },
            onSave = { updated ->
                viewModel.savePreferences(updated)
                editorSection = null
            }
        )
    }

    // Storage & Cache Bottom Sheet
    if (showStorageDialog) {
        BookFlowConfirmationSheet(
            onDismissRequest = { showStorageDialog = false },
            title = "Storage & Cache",
            message = "Temporary render cache currently occupies ${formatBytes(state.cacheSizeBytes)}.\n\nClearing the cache frees up device storage without deleting any of your books, annotations, bookmarks, or reading progress.",
            confirmText = "Clear Cache",
            icon = Icons.Default.DeleteOutline,
            onConfirm = { viewModel.clearCache() }
        )
    }

    // Privacy Bottom Sheet
    if (showPrivacyDialog) {
        BookFlowBottomSheet(
            onDismissRequest = { showPrivacyDialog = false },
            title = "Your Books Stay With You",
            titleIcon = {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFE11D48).copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFE11D48),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Understood", fontWeight = FontWeight.SemiBold)
                }
            }
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                PrivacyBulletPoint("100% On-Device", "No account required, no server uploads, and no cloud database. Everything is stored locally.")
                PrivacyBulletPoint("Zero Telemetry", "No background tracking, analytics, or behavioral logging.")
                PrivacyBulletPoint("Safe File Management", "Removing a book from BookFlow only deletes the app's local record — your original PDF document is never deleted or modified.")
            }
        }
    }

    if (showThemeDialog) {
        BookFlowBottomSheet(
            onDismissRequest = { showThemeDialog = false },
            title = "App Theme",
            subtitle = "Reader pages keep their own Page Theme"
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                com.bookflow.app.domain.model.AppThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = appTheme == mode,
                                role = androidx.compose.ui.semantics.Role.RadioButton,
                                onClick = { viewModel.setAppTheme(mode); showThemeDialog = false }
                            )
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = appTheme == mode, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = BrandPurple))
                        Spacer(Modifier.width(12.dp))
                        Text(mode.displayName, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }

    // Report a problem: share the newest on-device crash log (nothing is sent automatically)
    if (showReportDialog) {
        val crashReport = remember { com.bookflow.app.core.util.CrashLog.latestReport(context) }
        BookFlowBottomSheet(
            onDismissRequest = { showReportDialog = false },
            title = "Report a problem",
            subtitle = if (crashReport != null) "A crash report is saved on this device" else "No crashes recorded",
            confirmButton = {
                Button(
                    onClick = {
                        val body = buildString {
                            appendLine("Describe what happened:")
                            appendLine()
                            appendLine()
                            appendLine("— BookFlow ${com.bookflow.app.BuildConfig.VERSION_NAME} (${com.bookflow.app.BuildConfig.VERSION_CODE}), " +
                                "Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                            crashReport?.let { appendLine(); append(it) }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "BookFlow problem report")
                            putExtra(Intent.EXTRA_TEXT, body)
                        }
                        context.startActivity(Intent.createChooser(intent, "Send report"))
                        showReportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Share report", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = if (crashReport != null) {
                {
                    TextButton(onClick = { com.bookflow.app.core.util.CrashLog.clear(context); showReportDialog = false }) {
                        Text("Delete crash logs")
                    }
                }
            } else null
        ) {
            Text(
                "Your report opens in the app you choose (for example email), so you can review and edit it before " +
                    "sending. It includes the app version and your device model" +
                    (if (crashReport != null) ", plus technical details of the last crash." else "."),
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // About Bottom Sheet
    if (showAboutDialog) {
        BookFlowBottomSheet(
            onDismissRequest = { showAboutDialog = false },
            title = "BookFlow",
            subtitle = "Version ${com.bookflow.app.BuildConfig.VERSION_NAME} (Build ${com.bookflow.app.BuildConfig.VERSION_CODE}) • Your Reading Companion",
            titleIcon = { BookFlowEmblem(Modifier.size(40.dp)) },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    "BookFlow is a modern, high-performance Android PDF reader built for smooth document reading, text selection, annotation, and organization.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 19.sp
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
                TextButton(onClick = { showAboutDialog = false; onOpenLicenses() }) {
                    Text("View open-source licenses", color = BrandPurple, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private data class SettingRowData(
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color,
    val title: String,
    val subtitle: String,
    val value: String? = null,
    val isColorPalette: Boolean = false,
    val onClick: () -> Unit
)

private data class SettingGroup(
    val title: String,
    val items: List<SettingRowData>
)

@Composable
private fun SettingRow(item: SettingRowData) {
    Surface(
        onClick = item.onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = item.iconBg,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = item.iconTint,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = item.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }

            if (item.isColorPalette) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    listOf(
                        Color(0xFFFFE600),
                        Color(0xFF4ADE80),
                        Color(0xFF38BDF8),
                        Color(0xFFF472B6),
                        Color(0xFFA78BFA)
                    ).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
            } else if (item.value != null) {
                Text(
                    text = item.value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun PrivacyBulletPoint(title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "• ",
            fontWeight = FontWeight.Bold,
            color = BrandPurple,
            fontSize = 14.sp
        )
        Column {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
