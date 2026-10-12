package com.wandervox.app.main

import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.ui.components.DevelopmentBadge
import com.wandervox.ui.theme.Forest
import com.wandervox.ui.theme.Neutral
import com.wandervox.ui.theme.TextSecondary
import com.wandervox.ui.theme.WanderVoxTheme

@Composable
fun WanderVoxBottomBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = Neutral,
        tonalElevation = 0.dp,
        modifier = modifier
    ) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                icon = {
                    if (tab.inDevelopment) {
                        BadgedBox(badge = { DevelopmentBadge() }) {
                            Icon(imageVector = tab.icon, contentDescription = null)
                        }
                    } else {
                        Icon(imageVector = tab.icon, contentDescription = null)
                    }
                },
                label = { Text(text = tab.label, style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Forest,
                    selectedTextColor = Forest,
                    indicatorColor = Forest.copy(alpha = 0.12f),
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WanderVoxBottomBarPreview() {
    WanderVoxTheme {
        WanderVoxBottomBar(selectedTab = MainTab.Translator, onTabSelected = {})
    }
}
