# Polish — Etapa 3: Cableado de navegación (`:app`)

## Objetivo cumplido de la etapa
La pantalla de detalle de la Etapa 2 ya se puede abrir desde dos lugares:
- **Bitácora:** al tocar cualquier tarjeta, en "Mis viajes" o en "Comunidad".
- **Mapa:** al tocar un pin. Se reemplaza la burbuja por defecto de OSMDroid.

Las dos acciones navegan a la ruta `log/detail/{entryId}`. El botón de volver del detalle regresa a la pestaña desde la que se abrió.

## Archivos creados o modificados

### `:app`
| Archivo | Cambio |
|---|---|
| `app/src/main/java/com/wandervox/app/navigation/WanderVoxNavHost.kt` | **Rutas:** `Routes.LOG_DETAIL = "log/detail/{entryId}"`, armada con `LogDetailViewModel.ENTRY_ID_ARG`, y el helper `Routes.logDetail(entryId)`, que usa `Uri.encode`. **Destino nuevo:** `composable(route = Routes.LOG_DETAIL, arguments = listOf(navArgument(ENTRY_ID_ARG) { type = NavType.StringType }))`, que renderiza `LogDetailScreen(onNavigateBack = …)`. **En `Routes.HOME`:** una lambda `openLogEntry`, compartida entre `LogScreen(onEntryClick = …)` y `MainScaffold(onOpenLogEntry = …)`. |
| `app/src/main/java/com/wandervox/app/main/MainScaffold.kt` | Parámetro nuevo `onOpenLogEntry: (entryId: String) -> Unit` (documentado en el KDoc). La pestaña Mapa ahora usa `MapScreen(onMarkerClick = onOpenLogEntry)`. |

### `:features:log` (cambios mínimos para que el clic llegue a la tarjeta)
| Archivo | Cambio |
|---|---|
| `.../presentation/LogScreen.kt` | Parámetro nuevo obligatorio `onEntryClick: (entryId: String) -> Unit`, que se pasa a `LogContent`. |
| `.../presentation/LogContent.kt` | Parámetro `onEntryClick` en `LogContent` y en `LogBody`. Cada tarjeta recibe `onClick = { onEntryClick(entry.id) }`. Se actualizó el helper de previews. |
| `.../presentation/components/LogEntryCard.kt` | Parámetro opcional `onClick: (() -> Unit)? = null`. Si tiene valor, se usa la variante clickeable `ElevatedCard(onClick = …)`; si es `null`, la tarjeta no es clickeable, igual que antes. El contenido se extrajo a un `LogEntryCardBody` privado para no duplicarlo entre las dos variantes. No cambió el layout. |

### `:features:map`
No hizo falta tocarlo: `MapScreen` ya exponía `onMarkerClick: ((entryId: String) -> Unit)?`, y `OsmMapView` ya consume el toque (devuelve `true`) cuando la lambda tiene valor, así que la burbuja de OSMDroid deja de aparecer.

## Decisiones técnicas de diseño y estado de la UI
- **El detalle está en el NavHost raíz, no dentro de `MainScaffold`:** se abre a pantalla completa (sin bottom bar ni top bar del Home), igual que Ajustes. Home queda en el back stack con su pestaña guardada, así que volver restaura la Bitácora o el Mapa, según desde dónde se haya entrado.
- **Una sola lambda `openLogEntry`** para la Bitácora y el Mapa, así las dos entradas no se desincronizan.
- **`MainScaffold` recibe la lambda como parámetro:** el `navController` interno de las pestañas no puede navegar al grafo raíz, y así el contenedor no conoce rutas externas. Es el mismo patrón que `onNavigateToSettings`.
- **`launchSingleTop = true`** al navegar: un doble toque rápido sobre una tarjeta no apila dos detalles iguales.
- **Volver:** `popBackStack(Routes.LOG_DETAIL, inclusive = true)` en lugar de un `popBackStack()` simple, con el mismo criterio que Ajustes y Términos. Si se toca "atrás" dos veces seguidas, el segundo pop no encuentra la ruta y no hace nada, en lugar de sacar también a Home y dejar la app sin pantalla.
- **`Uri.encode(entryId)`:** protege la ruta si un id llega a tener caracteres como `/` o `?`. Navigation decodifica el argumento solo, así que el ViewModel recibe el id original.
- **Nombre del argumento único:** la ruta usa `LogDetailViewModel.ENTRY_ID_ARG`, la misma constante que lee el `SavedStateHandle`.
- **Tarjeta clickeable con dos variantes de `ElevatedCard`**, y no una sola con `enabled = false`, porque una tarjeta deshabilitada cambia a los colores de "disabled". Los botones internos (me gusta, foto, eliminar) siguen haciendo su propia acción y no abren el detalle.

## Comandos de verificación ejecutados y resultados
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat assembleDebug testDebugUnitTest
```
- Resultado: **BUILD SUCCESSFUL in 48s** (408 tareas accionables: 96 ejecutadas, 312 al día).
- APK generado: `app/build/outputs/apk/debug/app-debug.apk`.
- Tests unitarios de todos los módulos: **143 tests, 0 fallos, 0 errores** (23 suites). Las de este trabajo:
  - `LogDetailViewModelTest`: 7/7
  - `LogViewModelTest`: 17/17
  - `MapViewModelTest`: 6/6
- **No se probó en un dispositivo ni en un emulador.** La navegación (tocar una tarjeta o un pin, y volver) conviene validarla a mano antes del merge.

## Git
No se hizo ningún commit, merge ni push. Los cambios quedan en el working directory para que los revises.
