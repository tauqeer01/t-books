package com.bookflow.app.presentation.components

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
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavTabs.forEach { tab ->
                    val isSelected = currentRoute == tab.route

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (!isSelected) {
                                    onNavigate(tab.route)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag(tab.testTag)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) BrandPurpleSoft else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = stringResource(tab.labelRes),
                                tint = if (isSelected) BrandPurple else Color(0xFF64748B),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) BrandPurple else Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}
