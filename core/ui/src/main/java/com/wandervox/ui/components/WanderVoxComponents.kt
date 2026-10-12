package com.wandervox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.ui.theme.Forest
import com.wandervox.ui.theme.TextPrimary
import com.wandervox.ui.theme.TextSecondary
import com.wandervox.ui.theme.WanderVoxTheme

private val FieldShape = RoundedCornerShape(12.dp)
private val ErrorColor = Color(0xFFB3261E)

/** Logotipo tipográfico: "Wander" en texto primario y "Vox" resaltado en Forest. */
@Composable
fun WanderVoxLogo(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = TextPrimary)) { append("Wander") }
            withStyle(SpanStyle(color = Forest)) { append("Vox") }
        },
        style = style,
        modifier = modifier
    )
}

/** Botón principal (CTA) de ancho completo. Muestra un spinner mientras [isLoading]. */
@Composable
fun WanderVoxPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = FieldShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Forest,
            contentColor = Color.White,
            disabledContainerColor = Forest.copy(alpha = 0.5f),
            disabledContentColor = Color.White
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Pista informativa bajo un campo, ej. los requisitos de la contraseña. */
@Composable
fun WanderVoxFieldHint(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
fun WanderVoxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
        textStyle = MaterialTheme.typography.bodyLarge,
        leadingIcon = leadingIcon?.let { icon -> { Icon(imageVector = icon, contentDescription = null) } },
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        singleLine = true,
        enabled = enabled,
        isError = isError,
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Forest,
            focusedLabelColor = Forest,
            focusedLeadingIconColor = Forest,
            cursorColor = Forest,
            unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
            unfocusedLabelColor = TextSecondary,
            unfocusedLeadingIconColor = TextSecondary,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            errorContainerColor = Color.White,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Campo de contraseña con ícono de candado y toggle de visibilidad. */
@Composable
fun WanderVoxPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    WanderVoxTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        leadingIcon = Icons.Outlined.Lock,
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                    tint = TextSecondary
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = keyboardActions,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        enabled = enabled,
        isError = isError,
        modifier = modifier
    )
}

@Composable
fun WanderVoxErrorMessage(message: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(ErrorColor.copy(alpha = 0.08f), FieldShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(imageVector = Icons.Outlined.ErrorOutline, contentDescription = null, tint = ErrorColor)
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = ErrorColor)
    }
}

/** Texto de navegación secundaria, ej. "¿No tenés cuenta? Registrate". */
@Composable
fun WanderVoxTextLink(
    prompt: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(text = prompt, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        TextButton(onClick = onClick, enabled = enabled) {
            Text(text = action, style = MaterialTheme.typography.labelMedium, color = Forest)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F5)
@Composable
private fun WanderVoxComponentsPreview() {
    WanderVoxTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            WanderVoxLogo()
            WanderVoxPasswordField(value = "secreto", onValueChange = {}, label = "Contraseña")
            WanderVoxErrorMessage("Email o contraseña incorrectos.")
            WanderVoxPrimaryButton(text = "Iniciar sesión", onClick = {})
            WanderVoxFieldHint(text = "Mínimo 8 caracteres, con letras y números.")
            WanderVoxTextLink(prompt = "¿No tenés cuenta?", action = "Registrate", onClick = {})
        }
    }
}
