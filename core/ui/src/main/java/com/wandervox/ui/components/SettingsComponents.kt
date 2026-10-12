package com.wandervox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.ui.theme.Forest
import com.wandervox.ui.theme.Nature
import com.wandervox.ui.theme.TextPrimary
import com.wandervox.ui.theme.TextSecondary
import com.wandervox.ui.theme.WanderVoxTheme

private val SectionShape = RoundedCornerShape(16.dp)

/** Grupo de ajustes: título de sección y una tarjeta blanca con las filas separadas por divisores. */
@Composable
fun WanderVoxSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Card(
            shape = SectionShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    }
}

/**
 * Fila de ajuste: ícono, título, descripción opcional y un elemento al final (switch, badge...).
 * Si [onClick] es null la fila no es interactiva; con [enabled] en false se muestra atenuada.
 */
@Composable
fun WanderVoxSettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(Forest.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .alpha(if (enabled) 1f else 0.5f)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Forest, modifier = Modifier.size(22.dp))
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .alpha(if (enabled) 1f else 0.6f)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        trailing?.invoke()
    }
}

/** Fila de ajuste con un [Switch] al final; toda la fila alterna el valor. */
@Composable
fun WanderVoxSettingsSwitchItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    WanderVoxSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Nature
                )
            )
        }
    )
}

/** Divisor entre filas dentro de una [WanderVoxSettingsSection], alineado con el texto. */
@Composable
fun WanderVoxSettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = TextSecondary.copy(alpha = 0.15f),
        modifier = modifier.padding(start = 72.dp)
    )
}

/** Etiqueta de estado tipo píldora, ej. "Concedido" en verde o "Sin permiso" en gris. */
@Composable
fun WanderVoxStatusChip(
    text: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (isPositive) Forest else TextSecondary
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F5)
@Composable
private fun SettingsComponentsPreview() {
    WanderVoxTheme {
        WanderVoxSettingsSection(title = "Permisos", modifier = Modifier.padding(16.dp)) {
            WanderVoxSettingsSwitchItem(
                icon = Icons.Outlined.CloudOff,
                title = "Modo offline",
                subtitle = "Usar solo datos del dispositivo",
                checked = true,
                onCheckedChange = {}
            )
            WanderVoxSettingsDivider()
            WanderVoxSettingsItem(
                icon = Icons.Outlined.Mic,
                title = "Micrófono",
                onClick = {},
                trailing = { WanderVoxStatusChip(text = "Concedido", isPositive = true) }
            )
            WanderVoxSettingsDivider()
            WanderVoxSettingsItem(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                title = "Ayuda y soporte",
                enabled = false,
                trailing = { DevelopmentBadge() }
            )
        }
    }
}
