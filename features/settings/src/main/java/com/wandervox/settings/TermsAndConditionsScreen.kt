package com.wandervox.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.core.ui.theme.WanderVoxTheme

private data class TermsClause(val title: String, val body: String)

private const val TERMS_TITLE = "TÉRMINOS Y CONDICIONES DE USO - WANDERVOX"

private val TermsClauses = listOf(
    TermsClause(
        title = "1. NATURALEZA DEL SERVICIO:",
        body = "WanderVox es una herramienta digital de asistencia para viajes que provee funciones de " +
            "traducción local, registro personal de bitácora y mapeo referencial. La aplicación se " +
            "suministra \"tal cual\" y \"según disponibilidad\" (\"as is\"), sin garantías expresas o " +
            "implícitas sobre su infalibilidad."
    ),
    TermsClause(
        title = "2. DESCARGO DE RESPONSABILIDAD SOBRE TRADUCCIONES Y CONTENIDO:",
        body = "Las traducciones y reconocimientos de voz generados mediante modelos automatizados u " +
            "offline son únicamente de carácter orientativo. WanderVox y sus desarrolladores no asumen " +
            "responsabilidad civil, penal o económica alguna por errores de interpretación, inexactitudes " +
            "lingüísticas, malos entendidos o perjuicios derivados de traducciones erróneas en contextos " +
            "médicos, legales, contractuales, de emergencia o situaciones críticas."
    ),
    TermsClause(
        title = "3. DESCARGO SOBRE GEOLOCALIZACIÓN Y RECOMENDACIONES:",
        body = "Las coordenadas y puntos de interés registrados en la bitácora o mapas comunitarios son " +
            "de carácter informativo. WanderVox no se responsabiliza por desvíos en rutas, accidentes, " +
            "pérdidas de itinerario, zonas inseguras o condiciones climáticas imprevistas en los destinos " +
            "visitados."
    ),
    TermsClause(
        title = "4. ALMACENAMIENTO OFFLINE Y SINCRONIZACIÓN:",
        body = "El usuario es responsable de verificar la sincronización de sus datos cuando disponga de " +
            "conexión. No se garantiza la recuperación de información localmente almacenada ante borrado " +
            "de caché, desinstalación o fallas del dispositivo."
    ),
    TermsClause(
        title = "5. PROPIEDAD Y CONDUCTA:",
        body = "El usuario se compromete a no utilizar WanderVox para finalidades ilícitas y a respetar " +
            "las normativas locales de cada jurisdicción. El uso continuado de la app implica la plena " +
            "aceptación de estos términos."
    )
)

/** Pantalla de solo lectura con los términos de uso y el descargo de responsabilidad. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsAndConditionsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Términos y Condiciones", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Text(
                    text = TERMS_TITLE,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
            items(TermsClauses) { clause ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(text = clause.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = clause.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F5)
@Composable
private fun TermsAndConditionsScreenPreview() {
    WanderVoxTheme {
        TermsAndConditionsScreen(onNavigateBack = {})
    }
}
