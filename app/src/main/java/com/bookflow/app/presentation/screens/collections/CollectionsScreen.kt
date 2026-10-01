package com.bookflow.app.presentation.screens.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.domain.model.BookCollection
import com.bookflow.app.presentation.components.BookCoverCard

data class CollectionShowcase(
    val id: String,
    val name: String,
    val bookCount: String,
    val icon: ImageVector,
    val bgColor: Color,
    val iconColor: Color,
    val previews: List<Pair<String, Color>>
)

@Composable
fun CollectionsScreen(
    viewModel: CollectionsViewModel,
    onBookClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.selectedCollection != null) {
        viewModel.selectCollection(null)
    }

    val showcases = listOf(
        CollectionShowcase(
            id = "col_aviation",
            name = "Aviation",
            bookCount = "8 books",
            icon = Icons.Default.Flight,
            bgColor = Color(0xFFEFF6FF),
            iconColor = Color(0xFF2563EB),
            previews = listOf(
                "Aircraft Systems" to Color(0xFF0C2340),
                "Avionics" to Color(0xFF1E293B),
                "Flight Mechanics" to Color(0xFF0369A1)
            )
        ),
        CollectionShowcase(
            id = "col_medical",
            name = "Medical",
            bookCount = "6 books",
            icon = Icons.Default.AddBox,
            bgColor = Color(0xFFECFDF5),
            iconColor = Color(0xFF059669),
            previews = listOf(
                "Human Anatomy" to Color(0xFF991B1B),
                "Microbiology" to Color(0xFF6D28D9),
                "Pharmacology" to Color(0xFFD97706)
            )
        ),
        CollectionShowcase(
            id = "col_exam",
            name = "Exam Preparation",
            bookCount = "9 books",
            icon = Icons.Default.School,
            bgColor = Color(0xFFFEFCE8),
            iconColor = Color(0xFFCA8A04),
            previews = listOf(
                "Aviation Regs" to Color(0xFF475569),
                "Airframe" to Color(0xFF1E293B),
                "Power Plant" to Color(0xFF334155)
            )
        ),
        CollectionShowcase(
            id = "col_study",
            name = "Study Material",
            bookCount = "12 books",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            bgColor = Color(0xFFF5F3FF),
            iconColor = Color(0xFF7C3AED),
            previews = listOf(
                "Physics" to Color(0xFF4338CA),
                "Mathematics" to Color(0xFF1E293B),
                "Organic Chem" to Color(0xFF64748B)
            )
        ),
        CollectionShowcase(
            id = "col_favorites",
            name = "Favorites",
            bookCount = "18 books",
            icon = Icons.Default.Favorite,
            bgColor = Color(0xFFFFF1F2),
            iconColor = Color(0xFFE11D48),
            previews = listOf(
                "Aircraft Systems" to Color(0xFF0C2340),
                "Thermodynamics" to Color(0xFF0284C7),
                "Human Anatomy" to Color(0xFF991B1B)
            )
        ),
        CollectionShowcase(
            id = "col_research",
            name = "Research Papers",
            bookCount = "4 books",
            icon = Icons.Default.Description,
            bgColor = Color(0xFFFFF7ED),
            iconColor = Color(0xFFEA580C),
            previews = listOf(
                "Aerodynamics" to Color(0xFF0369A1),
                "Jet Engine" to Color(0xFF334155),
                "Structures" to Color(0xFF475569)
            )
        )
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        containerColor = Color(0xFFF8FAFC)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header: Title + Subtitle + Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.selectedCollection != null) {
                        IconButton(onClick = { viewModel.selectCollection(null) }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Column {
                        Text(
                            text = state.selectedCollection?.name ?: "Collections",
                            style = MaterialTheme.typography.displayMedium,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 28.sp
                        )
                        Text(
                            text = state.selectedCollection?.description ?: "Organize your books with collections",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                if (state.selectedCollection == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF334155))
                        }
                        IconButton(onClick = {}, modifier = Modifier.size(36.dp)) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More", tint = Color(0xFF334155))
                        }

                        // "+ New Collection" Purple Pill
                        Button(
                            onClick = viewModel::openCreateDialog,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "New Collection", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (state.selectedCollection != null) {
                // Inside a Collection: list books
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.collectionBooks, key = { it.id }) { book ->
                        BookCoverCard(
                            book = book,
                            onClick = { onBookClick(book.id, book.currentPage) },
                            onToggleFavorite = {},
                            onRemoveFromLibrary = {}
                        )
                    }
                }
            } else {
                // All Collections Dashboard Grid (Matching Screenshot)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().testTag("collections_grid")
                ) {
                    // Top 6 Quick Cards (Study Material, Aviation, Favorites, Medical, Exam Prep, Research)
                    items(showcases.take(6)) { item ->
                        QuickCollectionPillCard(
                            showcase = item,
                            onClick = {
                                val col = state.collections.find { it.id == item.id }
                                viewModel.selectCollection(col)
                            }
                        )
                    }

                    // Section Title: "All Collections" + "Sort: A to Z"
                    item(span = { GridItemSpan(2) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "All Collections",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                fontSize = 18.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { }
                            ) {
                                Text(
                                    text = "Sort: A to Z",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrandPurple,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = BrandPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Bottom 2-column cards with 3 book thumbnails each!
                    items(showcases) { item ->
                        CollectionWithThumbnailsCard(
                            showcase = item,
                            onClick = {
                                val col = state.collections.find { it.id == item.id }
                                viewModel.selectCollection(col)
                            }
                        )
                    }

                    item(span = { GridItemSpan(2) }) {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (state.isCreateDialogOpen) {
        CreateCollectionDialog(
            onDismiss = viewModel::closeCreateDialog,
            onConfirm = viewModel::createCollection
        )
    }
}

@Composable
fun CreateCollectionDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, pastelColorHex: String, iconName: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableStateOf("#DCEBFA") }
    var selectedIcon by remember { mutableStateOf("folder") }

    val pastelColors = listOf(
        "#DCEBFA",
        "#ECE3FA",
        "#FFE6DC",
        "#DFF3E4",
        "#FFF0D0",
        "#FDE2EC"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Collection",
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Collection Name") },
                    placeholder = { Text("e.g. Modern Physics") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Short shelf summary") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Pastel Palette",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    pastelColors.forEach { hex ->
                        val isSelected = selectedColorHex == hex
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), description.trim(), selectedColorHex, selectedIcon)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun QuickCollectionPillCard(
    showcase: CollectionShowcase,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = showcase.bgColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = showcase.icon,
                        contentDescription = null,
                        tint = showcase.iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = showcase.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = showcase.bookCount,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CollectionWithThumbnailsCard(
    showcase: CollectionShowcase,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with Icon + Title + Count + Arrow >
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(showcase.bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = showcase.icon,
                            contentDescription = null,
                            tint = showcase.iconColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = showcase.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = showcase.bookCount,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3 Thumbnail Covers in a row (Matching Screenshot)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                showcase.previews.forEach { (title, color) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title.take(2).uppercase(),
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF334155),
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
