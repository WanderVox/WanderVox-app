# Polish — Etapa 2: Pantalla de detalle de entrada (`:features:log`)

## Objetivo cumplido de la etapa
Se creó la pantalla de detalle de una entrada de la Bitácora. Muestra la entrada completa, sin truncar: foto en alta resolución, categoría, texto original y traducción completos, notas, autor, fecha y hora, y la geoetiqueta si existe. La pantalla sigue el patrón UDF del módulo:
- un `LogDetailScreen` con estado que contiene el ViewModel;
- un `LogDetailContent` sin estado que solo dibuja lo que recibe.

Todavía **no está enganchada a la navegación**; eso corresponde a la Etapa 3.

## Archivos creados o modificados

### Nuevos — `features/log/src/main/java/com/wandervox/features/log/presentation/detail/`
| Archivo | Contenido clave |
|---|---|
| `LogDetailUiState.kt` | `sealed interface LogDetailUiState` con tres estados: `Loading`, `NotFound` y `Loaded(entry: TravelLogEntry)`. |
| `LogDetailViewModel.kt` | `@HiltViewModel`. Recibe por inyección `SavedStateHandle` y `LogRepository`. Lee el argumento `ENTRY_ID_ARG = "entryId"` y expone `uiState: StateFlow<LogDetailUiState>`, que se arma con `logRepository.getEntry(entryId)` → `map` → `catch` → `stateIn(WhileSubscribed(5s), initial = Loading)`. |
| `LogDetailScreen.kt` | Composable con estado: `LogDetailScreen(onNavigateBack, modifier, viewModel = hiltViewModel())`. Lee el estado con `collectAsStateWithLifecycle()` y delega el dibujo en `LogDetailContent`. |
| `LogDetailContent.kt` | Composable sin estado. Usa un `Scaffold` con `CenterAlignedTopAppBar` y un botón de volver (`Icons.AutoMirrored.Filled.ArrowBack` → `onNavigateBack`). Tiene un `when` sobre los tres estados, componentes privados (`EntryDetail`, `LabeledText`, `GeotagBadge`) y dos `@Preview` (cargada y no encontrada). |

### Nuevo — tests
| Archivo | Contenido |
|---|---|
| `features/log/src/test/java/com/wandervox/features/log/presentation/detail/LogDetailViewModelTest.kt` | 7 tests, con un `FakeLogRepository` propio del archivo. |

### Modificados
| Archivo | Cambio |
|---|---|
| `features/log/.../presentation/components/LogEntryCard.kt` | `CategoryLabel` pasó de `private` a `internal` para reutilizar en el detalle el mismo chip de categoría de la tarjeta. No se tocó nada más. |
| `features/log/src/main/res/values/strings.xml` | 8 strings nuevos: `log_detail_title`, `log_detail_back`, `log_detail_not_found`, `log_detail_original`, `log_detail_translation`, `log_detail_notes`, `log_detail_location`, `log_detail_location_description`. |

No se tocó `:core:domain`. Se usa el contrato existente `LogRepository.getEntry(entryId): Flow<TravelLogEntry?>`.

## Decisiones técnicas de diseño y estado de la UI
- **`sealed interface` en lugar de un `data class` con flags:** los tres estados se excluyen entre sí, y el `when` exhaustivo en `LogDetailContent` impide que la UI quede en un estado inconsistente.
- **El estado se actualiza solo:** `getEntry` es un `Flow`. Si la entrada se edita o se sincroniza mientras la pantalla está abierta, el detalle se actualiza. Si se borra (por ejemplo, desde otro dispositivo), pasa a `NotFound`.
- **Argumento faltante o en blanco → `NotFound`** sin consultar el repositorio, para cubrir deep links mal formados.
- **Error de lectura → `NotFound`:** la fuente es local (offline-first), así que un error significa que no hay nada para mostrar. Se decidió no agregar un cuarto estado `Error` porque la consigna pedía tres. Si más adelante se necesita "Reintentar", se agrega sin romper nada.
- **`ENTRY_ID_ARG` público en el `companion`:** la Etapa 3 va a usar esta misma constante para declarar la ruta `log/detail/{entryId}`, así el nombre del argumento no se duplica.
- **UI de `Loaded`:**
  - La foto usa el componente existente `LogPhoto` (Coil, prioriza la copia local y si no hay usa la URL remota), con 280 dp de alto. Coil carga la imagen a la resolución del contenedor.
  - Abajo de la foto hay una fila con el chip de categoría y, si hay coordenadas, un badge de ubicación con ícono `Place` y `lat, lon` a 5 decimales (`Locale.ROOT`, para que el separador decimal no se confunda con la coma).
  - Los textos aparecen completos, sin `maxLines`, con etiquetas "Original (ES)" y "Traducción (EN)" según `sourceLanguage` y `targetLanguage`. Las notas van en cursiva y solo si existen.
  - Abajo, después de un divisor, va el autor (o "Viajero anónimo") y la fecha con hora (`DateFormat.LONG` + `SHORT`).
  - Todo el cuerpo hace scroll vertical (`verticalScroll`), para textos largos.
- **`NotFound`:** reutiliza `LogPlaceholder`, con ícono `SearchOff`, el mensaje "Esta entrada ya no está disponible." y la acción "Volver" → `onNavigateBack`.
- **`Loading`:** un `CircularProgressIndicator` centrado.
- El estilo de la top bar copia el de `SettingsScreen`, para que se vea igual en toda la app.

## Comandos de verificación ejecutados y resultados
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat :features:log:assembleDebug :features:log:testDebugUnitTest
```
- Resultado: **BUILD SUCCESSFUL in 14s** (48 tareas accionables).
- Resultados de los tests (`features/log/build/test-results/testDebugUnitTest`):

| Suite | Tests | Fallos | Errores |
|---|---|---|---|
| `LogDetailViewModelTest` (nuevo) | 7 | 0 | 0 |
| `LogViewModelTest` (existente) | 17 | 0 | 0 |
| `ExampleUnitTest` | 1 | 0 | 0 |

Tests de `LogDetailViewModelTest`:
1. `starts loading before anyone collects`: el valor inicial es `Loading`.
2. `existing entry id emits the loaded entry`: un id existente da `Loaded(entry)`, y verifica que se consultó ese id.
3. `unknown entry id emits not found`: un id inexistente da `NotFound`.
4. `missing or blank argument emits not found without querying the repository`: sin argumento o con argumento en blanco da `NotFound`, sin llamar al repositorio.
5. `entry updates are reflected while the screen is open`: una edición en el repositorio actualiza `Loaded`.
6. `deleting the open entry switches to not found`: borrar la entrada abierta pasa a `NotFound`.
7. `failing read emits not found`: si el `Flow` falla, da `NotFound`.

## Git
No se hizo ningún commit, merge ni push. Los cambios quedan en el working directory para que los revises.
