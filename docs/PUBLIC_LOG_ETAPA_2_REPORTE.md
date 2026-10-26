# Entradas públicas — Etapa 2: Indicador y cambio de visibilidad en la Bitácora (`:features:log`)

## Objetivo cumplido de la etapa
- **Tarjetas de "Mis viajes":** cada tarjeta muestra si la entrada propia es **Pública** o **Privada** y tiene un botón para cambiarlo con un toque.
- **Persistencia del cambio:** se guarda en Room con `syncStatus = PENDING`, así que se vuelve a subir a Firestore con el nuevo valor de `isPublic`.
- **Pantalla de detalle:** también muestra con claridad la visibilidad de la entrada.

## Archivos creados o modificados
Todos bajo `features/log/src/`.

| Archivo | Componentes afectados |
|---|---|
| `main/.../presentation/LogUiEvent.kt` | Evento nuevo `OnToggleVisibility(entryId)`. |
| `main/.../presentation/LogUiState.kt` | `LogActionError` suma `VISIBILITY`. |
| `main/.../presentation/LogViewModel.kt` | • Función nueva `toggleVisibility(entryId)`: lee la entrada con `getEntry(...).first()` y la guarda con `saveEntry(entry.copy(isPublic = !isPublic, updatedAt = now, syncStatus = PENDING))`. Si falla, emite `actionError = VISIBILITY`.<br>• `togglingVisibilityIds`: evita que un doble toque invierta dos veces el mismo valor desactualizado. |
| `main/.../presentation/LogScreen.kt` | Envía `OnToggleVisibility` y muestra el snackbar de error "No se pudo cambiar la visibilidad de la entrada." |
| `main/.../presentation/LogContent.kt` | Parámetro `onToggleVisibility` en `LogContent` y en `LogBody`. Solo se pasa a la tarjeta en **Mis viajes** y si la entrada **es del usuario**, con la misma condición que borrar y cambiar foto. Se actualizó el preview. |
| `main/.../presentation/components/LogEntryCard.kt` | • Parámetro opcional nuevo `onToggleVisibility: (() -> Unit)? = null`.<br>• **Indicador** `VisibilityLabel`: ícono de 14 dp + "Pública" / "Privada", debajo de la fecha.<br>• **Acción** `IconToggleButton(checked = isPublic)` con ícono 🌐 `Public` (color primario) o 🔒 `Lock`, ubicado antes del "me gusta".<br>• Función compartida `internal fun visibilityIcon(isPublic)`. |
| `main/.../presentation/detail/LogDetailContent.kt` | `VisibilityRow` abajo del chip de categoría: ícono + "Pública" / "Privada" + explicación ("Visible para todos en Comunidad" / "Solo vos podés verla"). El preview muestra una entrada pública. |
| `main/res/values/strings.xml` | 7 strings nuevos: `log_error_visibility`, `log_visibility_public`, `log_visibility_private`, `log_visibility_make_public`, `log_visibility_make_private`, `log_visibility_public_detail` y `log_visibility_private_detail`. |
| `test/.../presentation/LogViewModelTest.kt` | 4 tests nuevos. |

No se tocaron `:core:domain`, `:core:data` ni `:core:network`. El ViewModel solo usa el contrato `LogRepository` que ya existía.

## Manejo de estado y decisiones de diseño
- **Room es la única fuente de verdad (UDF):** el toque solo envía `OnToggleVisibility`. El ViewModel no cambia nada en memoria: guarda en el repositorio, y el `Flow` de Room vuelve a emitir la entrada actualizada. Así la tarjeta muestra el cambio al instante y el badge de sincronización pasa a "Pendiente" y después a "Sincronizada", sin estado duplicado.
- **Reutiliza el camino de guardado que ya existía:** `OfflineFirstLogRepository.saveEntry` ya hace el upsert en Room como `PENDING`, sube `updatedAt` y lanza la subida a Firestore en segundo plano. El ViewModel además pone `PENDING` y `updatedAt` en la copia (igual que `attachPhoto`), para no depender de un detalle interno del repositorio.
- **Quién lo ve:** solo el dueño, en "Mis viajes". En Comunidad todas las entradas son públicas por definición, y una entrada ajena no se puede modificar.
- **Indicador y acción por separado:** la etiqueta de texto deja el estado claro a simple vista, y el botón de ícono sigue el estilo de los otros botones de la tarjeta (foto, eliminar). Se descartó un chip en la fila superior porque, con categoría y badge de sincronización, no entra en pantallas angostas.
- **Accesibilidad:** el `IconToggleButton` expone el estado "activado/desactivado", así que TalkBack lee "Compartir con la comunidad, activado". La etiqueta de texto tiene el ícono con `contentDescription = null` para no repetirlo.
- **Doble toque:** un `Set` interno ignora un segundo toque mientras el primero se está guardando. Así no se invierte dos veces el mismo valor ni queda un estado que el usuario no eligió.
- **Errores:** si falla el guardado o la entrada ya no existe, se muestra el error una sola vez con el mismo mecanismo `actionError` que ya usan "me gusta", borrar y foto.
- **Cuándo aparece en Comunidad:** `getCommunityEntries()` lee de Firestore. Una entrada que se hace pública aparece en el feed **cuando termina de sincronizar**, y si no hay conexión, cuando se recupera. Mientras tanto, la tarjeta propia ya muestra "Pública" con el badge "Pendiente". Esto se va a verificar en la Etapa 3.

## Resultados de las pruebas y comandos ejecutados
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat :features:log:testDebugUnitTest :features:log:assembleDebug
```
- Resultado: **BUILD SUCCESSFUL in 15s** (48 tareas accionables).

| Suite | Tests | Fallos | Errores |
|---|---|---|---|
| `LogViewModelTest` | 21 (17 existentes + 4 nuevos) | 0 | 0 |
| `LogDetailViewModelTest` | 7 | 0 | 0 |
| `ExampleUnitTest` | 1 | 0 | 0 |

Tests nuevos:
1. `toggling a private entry makes it public and pending sync`: una entrada `SYNCED` y privada pasa a `isPublic = true` y `PENDING`, y la UI lo refleja.
2. `toggling a public entry makes it private and drops it from the community`: al hacerla privada, desaparece de la pestaña Comunidad.
3. `failed visibility change reports an action error`: si falla el guardado, la entrada no cambia y se emite `VISIBILITY`.
4. `visibility change of a missing entry reports an action error`.

No se probó en un dispositivo ni en un emulador.

### Recordatorio (de la Etapa 1)
El nombre del autor que se guarda es el **email** del usuario, y ahora se ve en Comunidad. Sigue pendiente decidir cómo resolverlo.

## Git
No se hizo ningún commit, merge ni push. Los cambios quedan en el working directory para que los revises.
