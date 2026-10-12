package com.wandervox.features.translator.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.core.ui.theme.WanderVoxTheme
import com.wandervox.translator.R

/**
 * Primary call to action. A circular Marigold button; while [isListening] two rings pulse
 * outward from it. Implemented as a clickable [Surface] rather than a FAB because it needs an
 * `enabled` state (disabled while offline models are being prepared).
 */
@Composable
fun MicButton(
    isListening: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(PulseAreaSize),
    ) {
        if (isListening) {
            ListeningPulse(color = MaterialTheme.colorScheme.tertiary)
        }
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            shadowElevation = 6.dp,
            modifier = Modifier.size(ButtonSize),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(if (isListening) R.drawable.ic_stop else R.drawable.ic_mic),
                    contentDescription = stringResource(
                        if (isListening) R.string.translator_stop_listening else R.string.translator_start_listening
                    ),
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}

@Composable
private fun ListeningPulse(color: Color) {
    val transition = rememberInfiniteTransition(label = "listeningPulse")
    // Two rings offset by half a cycle give a continuous "sound wave" feel.
    val progressA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(PULSE_DURATION_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseRingA",
    )
    val progressB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(PULSE_DURATION_MILLIS, delayMillis = PULSE_DURATION_MILLIS / 2, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "pulseRingB",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val minRadius = ButtonSize.toPx() / 2
        val maxRadius = size.minDimension / 2
        listOf(progressA, progressB).forEach { progress ->
            drawCircle(
                color = color.copy(alpha = (1f - progress) * MAX_PULSE_ALPHA),
                radius = minRadius + (maxRadius - minRadius) * progress,
            )
        }
    }
}

private val ButtonSize = 88.dp
private val PulseAreaSize = 136.dp
private const val PULSE_DURATION_MILLIS = 1_400
private const val MAX_PULSE_ALPHA = 0.45f

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F5)
@Composable
private fun MicButtonPreview() {
    WanderVoxTheme {
        Row(Modifier.padding(16.dp)) {
            MicButton(isListening = false, enabled = true, onClick = {})
            MicButton(isListening = true, enabled = true, onClick = {})
            MicButton(isListening = false, enabled = false, onClick = {})
        }
    }
}
