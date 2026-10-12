package com.wandervox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wandervox.ui.theme.Forest
import com.wandervox.ui.theme.Marigold
import com.wandervox.ui.theme.TextPrimary
import com.wandervox.ui.theme.TextSecondary
import com.wandervox.ui.theme.WanderVoxTheme

/** Etiqueta Marigold para marcar secciones que todavía no están disponibles. */
@Composable
fun DevelopmentBadge(
    modifier: Modifier = Modifier,
    text: String = "Próximamente"
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp),
        color = TextPrimary,
        maxLines = 1,
        modifier = modifier
            .background(Marigold, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/** Pantalla de relleno para las secciones que aún están en desarrollo. */
@Composable
fun UnderDevelopmentScreen(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    message: String = "Esta sección se encuentra en desarrollo."
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(112.dp)
                    .background(Forest.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Forest,
                    modifier = Modifier.size(52.dp)
                )
            }
            DevelopmentBadge(text = "En desarrollo")
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun UnderDevelopmentScreenPreview() {
    WanderVoxTheme {
        UnderDevelopmentScreen(title = "Mapa", icon = Icons.Outlined.Map)
    }
}
