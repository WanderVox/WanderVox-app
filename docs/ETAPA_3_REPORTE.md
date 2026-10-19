# Etapa 3 — Tarjetas, filtro por categoría y confirmación de borrado (Track Dev B)

**Fecha:** 2026-10-19 · **Rama:** `Feat/Luc-Bitacora` · **Responsable:** Lucas (Dev B)

## 1. Objetivo cumplido

- **Tarjeta de entrada** con: texto original como título, traducción, notas, chip de categoría, autor
  (en "Comunidad"), badge de sincronización `PENDING` / `SYNCED` / `FAILED` (en "Mis viajes"), fecha,
  botón de me gusta con contador y botón de eliminar.
- **Fila horizontal de filtros** por `LogCategory` ("Todas" + 6 categorías, con ícono).
- **Diálogo de confirmación** antes de eliminar.
- **Estados de la lista:** carga, error con "Reintentar", vacío por pestaña y vacío por filtro con
  "Ver todas".
- **`LogViewModelTest`:** 12 tests de cambio de pestaña, filtrado por categoría, reintento, me gusta y
  borrado. Pasan todos.
- No se tocaron `:core:domain`, `:core:database`, `:features:map`, el Traductor ni `:app`.

## 2. Archivos creados o modificados

### Nuevos — `features/log/src/main/java/com/wandervox/features/log/presentation/components/`

| Archivo | Contenido clave |
|---|---|
| `LogEntryCard.kt` | `LogEntryCard(entry, currentUserId, showAuthor, showSyncStatus, onToggleLike, onDelete?)`. Componentes privados `CategoryLabel`, `SyncStatusBadge`, `Pill` y `LikeButton` (`IconToggleButton` + contador). Incluye `@Preview` |
| `CategoryFilterRow.kt` | `LazyRow` de `FilterChip`. "Todas" equivale a `null`, y tocar la categoría ya elegida limpia el filtro |
| `DeleteEntryDialog.kt` | `AlertDialog` con "Eliminar" en color `error` y "Cancelar" |
| `LogPlaceholder.kt` | Ícono + mensaje centrado + acción opcional, para los estados vacíos y de error |
| `LogCategoryUi.kt` | `LogCategory.labelRes` y `LogCategory.icon` |

### Nuevos — tests

| Archivo | Tests |
|---|---|
| `features/log/src/test/java/com/wandervox/features/log/presentation/LogViewModelTest.kt` | 12 tests, con `FakeLogRepository` y `FakeAuthRepository` |

### Modificados

| Archivo | Cambio |
|---|---|
| `LogUiState.kt` | Nuevo `enum LogActionError { LIKE, DELETE }` y campo `actionError` |
| `LogUiEvent.kt` | Nuevos eventos `OnToggleLike(entryId)`, `OnDeleteEntry(entryId)` y `OnActionErrorShown` |
| `LogViewModel.kt` | `toggleLike()` (usa el `uid` actual) y `deleteEntry()`. Si fallan, cargan `actionError` |
| `LogContent.kt` | Pestañas + `CategoryFilterRow` + `LogBody` (carga / error / vacío filtrado / vacío / lista de `LogEntryCard`), `SnackbarHost` y diálogo de borrado. 4 `@Preview` |
| `LogScreen.kt` | Guarda `pendingDeletionId` con `rememberSaveable` y muestra los errores de acción en un snackbar |
| `features/log/src/main/res/values/strings.xml` | Categorías, estados de sync, autor, me gusta, diálogo y errores, en español |
| `features/log/build.gradle.kts` | `material-icons-extended` |
| `docs/DEV_LOG.md` (sección 13) | Justificación |

## 3. Decisiones de diseño y justificación

1. **Qué muestra cada tarjeta según la pestaña:**

   | Elemento | Mis viajes | Comunidad |
   |---|---|---|
   | Badge de sync | ✅ | — (el estado de sync de otros usuarios no aporta) |
   | Autor | — | ✅ ("Por Ana" o "Por Viajero anónimo" si `authorName` es null) |
   | Me gusta | Solo si la entrada es pública | ✅ |
   | Eliminar | Solo entradas propias (`userId == currentUserId`) | — |

   El me gusta solo aparece en entradas públicas porque en una privada nadie más la ve. Si no hay
   sesión, el botón aparece deshabilitado.
2. **El diálogo de borrado vive en `LogScreen`** (`rememberSaveable`, sobrevive a la rotación) y se pasa
   a `LogContent` como `pendingDeletionId`. Así `LogContent` sigue sin estado y se puede previsualizar.
   El ViewModel recibe `OnDeleteEntry` recién después de la confirmación.
3. **Errores de acción, como en el Traductor.** `actionError` se consume primero
   (`OnActionErrorShown`) y después se muestra el snackbar en el scope de la pantalla, para que limpiar
   el flag no cancele el mensaje.
4. **El filtro se mantiene al cambiar de pestaña.** Es un filtro de la pantalla, y hay un test que lo
   cubre. Si el filtro no deja resultados, se muestra un mensaje propio con "Ver todas".
5. **Etiquetas de categoría propias (`log_category_*`).** Las del Traductor son internas de su módulo.
   Unificarlas en `:core:ui` implicaría tocar código de Dev A, así que queda para una limpieza conjunta.
6. **Sin colores nuevos.** El badge de sync usa `primaryContainer` / `tertiaryContainer` /
   `errorContainer`, que `ColorContrastTest` ya verifica para WCAG AA.
7. **`material-icons-extended`:** es el mismo artefacto que ya usan `:core:ui`, `:features:auth` y
   `:features:settings`. No agrega ninguna librería nueva al APK.

## 4. Tests (`LogViewModelTest`)

| Grupo | Tests |
|---|---|
| Cambio de pestaña | arranca en "Mis viajes" con las entradas del usuario · "Comunidad" muestra solo las públicas · volver a "Mis viajes" restaura la lista · sin sesión "Mis viajes" queda vacía · la lista sigue los cambios del repositorio |
| Filtro por categoría | filtra la pestaña activa · se mantiene al cambiar de pestaña · `null` vuelve a mostrar todo · una categoría sin entradas da lista vacía (sin quedar en carga) |
| Errores y acciones | una fuente que falla activa `isError` y `OnRetry` recupera · el me gusta usa el `uid` actual · el borrado quita la entrada y, si falla, carga `actionError`, que se limpia con `OnActionErrorShown` |

## 5. Comandos de verificación y resultados

Usé el JDK 17 de `~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`.

| Comando | Resultado |
|---|---|
| `./gradlew :features:log:testDebugUnitTest` | ✅ BUILD SUCCESSFUL. `LogViewModelTest`: 12 tests, 0 fallas · `ExampleUnitTest`: 1 |
| `./gradlew assembleDebug testDebugUnitTest --rerun-tasks` | ✅ BUILD SUCCESSFUL, 0 advertencias de Kotlin, **50/50** tests en todo el proyecto (38 anteriores + 12 nuevos) |

**No se probó en un dispositivo.** Los `@Preview` de `LogContent` (Mis viajes, Comunidad, filtro sin
resultados y error) y de `LogEntryCard` permiten revisar el diseño en Android Studio.

## 6. Pendientes que siguen abiertos

- Sigue abierto lo de la Etapa 2: las entradas del Traductor se guardan sin `userId` y no aparecen en
  "Mis viajes". Hay que coordinarlo con Dev A. Además, el botón eliminar solo aparece si
  `entry.userId == currentUserId`.
- Pantalla de detalle de una entrada: el plan la nombra en el título de la Etapa 3, pero no en las
  tareas. `LogRepository.getEntry()` ya existe, así que se puede agregar cuando se defina la navegación
  desde el mapa con Dev A.
