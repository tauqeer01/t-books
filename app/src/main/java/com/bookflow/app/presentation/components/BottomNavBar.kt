package com.bookflow.app.presentation.components

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bookflow.app.R
import com.bookflow.app.core.theme.BrandPurple
import com.bookflow.app.core.theme.BrandPurpleSoft
import com.bookflow.app.presentation.navigation.Screen

data class NavTabItem(
    val route: String,
    val labelRes: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val testTag: String
)

val NavTabs = listOf(
    NavTabItem(
        route = Screen.Home.route,
        labelRes = R.string.nav_home,
        filledIcon = Icons.Filled.Home,
        outlinedIcon = Icons.Outlined.Home,
        testTag = "nav_home_tab"
    ),
    NavTabItem(
        route = Screen.Library.route,
        labelRes = R.string.nav_library,
        filledIcon = Icons.Filled.MenuBook,
        outlinedIcon = Icons.Outlined.MenuBook,
        testTag = "nav_library_tab"
    ),
    NavTabItem(
        route = Screen.Collections.route,
        labelRes = R.string.nav_collections,
        filledIcon = Icons.Filled.Folder,
        outlinedIcon = Icons.Outlined.Folder,
        testTag = "nav_collections_tab"
    ),
    NavTabItem(
        route = Screen.Search.route,
        labelRes = R.string.nav_search,
        filledIcon = Icons.Filled.Search,
        outlinedIcon = Icons.Outlined.Search,
        testTag = "nav_search_tab"
    ),
    NavTabItem(
        route = Screen.Settings.route,
        labelRes = R.string.nav_settings,
        filledIcon = Icons.Filled.Settings,
        outlinedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings_tab"
    )
)

@Composable
fun BookFlowBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTabs.forEach { tab ->
                    val isSelected = currentRoute == tab.route

                    val label = stringResource(tab.labelRes)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .selectable(
                                selected = isSelected,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab
                            ) {
                                if (!isSelected) {
                                    onNavigate(tab.route)
                                }
                            }
                            // One announcement per tab ("Library, tab, selected") instead of icon + text twice
                            .clearAndSetSemantics { contentDescription = label }
                            .padding(vertical = 4.dp)
                            .testTag(tab.testTag)
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                            contentDescription = stringResource(tab.labelRes),
                            tint = if (isSelected) BrandPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) BrandPurple else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Side navigation for wide windows (tablets, foldables, landscape), replacing the bottom bar at >= 600dp.
 */
@Composable
fun BookFlowNavigationRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.NavigationRail(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        header = { BookFlowEmblem(Modifier.padding(vertical = 12.dp).size(40.dp)) }
    ) {
        Spacer(Modifier.weight(1f))
        NavTabs.forEach { tab ->
            val isSelected = currentRoute == tab.route
            val label = stringResource(tab.labelRes)
            androidx.compose.material3.NavigationRailItem(
                selected = isSelected,
                onClick = { if (!isSelected) onNavigate(tab.route) },
                icon = { Icon(if (isSelected) tab.filledIcon else tab.outlinedIcon, contentDescription = null) },
                label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                    selectedIconColor = BrandPurple,
                    selectedTextColor = BrandPurple,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .semantics { contentDescription = label }
                    .testTag(tab.testTag)
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
