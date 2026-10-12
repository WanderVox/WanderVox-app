package com.wandervox.features.translator.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.core.ui.theme.WanderVoxTheme
import com.wandervox.translator.R

/**
 * Status badge (not a chip) shown in the top app bar. It is informational only, so it is
 * deliberately not clickable: a fake button would mislead screen-reader users.
 */
@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.translator_offline_badge_description)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cloud_off),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(text = stringResource(R.string.translator_offline_badge), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Preview
@Composable
private fun OfflineBadgePreview() {
    WanderVoxTheme { OfflineBadge() }
}
