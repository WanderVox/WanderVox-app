# Polish — Etapa 4: Botón de sincronización en Ajustes (`:features:settings`)

## Objetivo cumplido de la etapa
La fila **"Sincronización de Bitácora"** de Ajustes ya funciona:
- se quitó el badge "Próximamente";
- al tocarla se llama a `LogRepository.syncPending()`;
- la fila muestra en vivo el estado: inactiva, sincronizando, éxito o error.

Además se verificó el proyecto completo (11 módulos) con `clean assembleDebug testDebugUnitTest`.

## Archivos creados o modificados
| Archivo | Cambio |
|---|---|
| `features/settings/src/main/java/com/wandervox/settings/SettingsViewModel.kt` | • `enum class LogSyncState { IDLE, SYNCING, SUCCESS, ERROR }`.<br>• Campo nuevo `SettingsUiState.logSyncState` (por defecto `IDLE`).<br>• `LogRepository` inyectado en el constructor, junto a `AuthRepository`.<br>• Función nueva `syncPending()`: si ya hay una sincronización en curso no hace nada; si no, pasa a `SYNCING`, lanza `logRepository.syncPending()` en `viewModelScope` y termina en `SUCCESS` o `ERROR` según el `Result`. |
| `features/settings/src/main/java/com/wandervox/settings/SettingsScreen.kt` | • `SettingsScreen` pasa `onSyncLogClick = viewModel::syncPending`.<br>• `SettingsContent` recibe el parámetro nuevo `onSyncLogClick: () -> Unit`.<br>• La fila vieja (con `enabled = false` y `DevelopmentBadge()`) se reemplazó por el composable privado `LogSyncItem(state, onClick)`.<br>• Se quitó el import de `DevelopmentBadge`, que el archivo ya no usa (la bottom bar sigue usándolo).<br>• Se actualizó el `@Preview`. |
| `features/settings/src/test/java/com/wandervox/settings/SettingsViewModelTest.kt` | • El constructor ahora recibe un `FakeLogRepository`.<br>• Se agregaron 4 tests de sincronización. |

No se agregaron dependencias de Gradle: `:features:settings` ya dependía de `:core:domain`, y `LogRepository` ya está registrado en Hilt (`:core:data`). Como no entra ninguna librería externa nueva, no hace falta una entrada en `DEV_LOG.md`. No se tocó `:core:domain`.

## Decisiones técnicas de diseño y estado de la UI
- **Un `enum` para el estado**, en lugar de varios booleanos (`isSyncing`, `syncError`…): los cuatro estados se excluyen entre sí y el `when` de la UI es exhaustivo.
- **Cómo se ve la fila en cada estado:**

| Estado | Subtítulo | Al final de la fila | ¿Se puede tocar? |
|---|---|---|---|
| `IDLE` | "Subir tus entradas guardadas a la nube" | Flecha `›` | Sí |
| `SYNCING` | "Sincronizando tus entradas…" | `CircularProgressIndicator` de 20 dp | No |
| `SUCCESS` | "Todas tus entradas están en la nube" | Chip "Sincronizado" (positivo) | Sí |
| `ERROR` | "Algunas entradas no se subieron. Tocá para reintentar." | Chip "Error" | Sí (reintenta) |

- **Doble protección contra toques repetidos:** mientras sincroniza, la fila está deshabilitada en la UI y además el ViewModel ignora llamadas a `syncPending()`.
- **No se muestra el mensaje de la excepción:** `OfflineFirstLogRepository.syncPending()` falla con una `SyncIncompleteException` técnica. En su lugar, el subtítulo muestra un texto pensado para el usuario.
- **Comportamiento offline:** sin conexión, la sincronización falla de forma controlada (`Result.failure`) y la fila queda en `ERROR` con la invitación a reintentar. Las entradas siguen guardadas en Room como `PENDING`/`FAILED`, así que no se pierde nada.
- **El estado vive en memoria del ViewModel:** al salir y volver a Ajustes, la fila vuelve a `IDLE`. Es intencional, porque es el resultado de una acción puntual y no un estado persistente. El estado real de cada entrada ya se ve en el badge de cada tarjeta de la Bitácora.
- **Textos hardcodeados en español**, igual que el resto de `SettingsScreen.kt`, que no usa `strings.xml`.

## Comandos de verificación ejecutados y resultados
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat clean assembleDebug testDebugUnitTest
```
- Resultado: **BUILD SUCCESSFUL in 1m 44s** (419 tareas accionables: 383 ejecutadas, 36 al día; build limpio).
- Módulos incluidos en `settings.gradle.kts`: **11**, todos compilados.
- APK generado: `app/build/outputs/apk/debug/app-debug.apk`.
- Tests unitarios: **147 tests en 23 suites, 0 fallos, 0 errores**.
  - `SettingsViewModelTest`: 8/8. Son los 4 que ya existían más estos 4 nuevos:
    1. `log sync starts idle`
    2. `log sync shows syncing and then success`: usa un `CompletableDeferred` para comprobar el estado intermedio `SYNCING`.
    3. `log sync failure shows the error state and can be retried`
    4. `log sync ignores taps while a sync is running`: comprueba que se llama una sola vez al repositorio.
- No se probó en un dispositivo ni en un emulador.

---

## Resumen final del polish (Etapas 1 a 4)
| Etapa | Módulo | Resultado |
|---|---|---|
| 1 | `:features:log` | Las tarjetas de la Bitácora limitan el original y la traducción a 3 líneas con `…`. |
| 2 | `:features:log` | Pantalla de detalle nueva (`LogDetailUiState` / `ViewModel` / `Screen` / `Content`) y 7 tests. |
| 3 | `:app` | Ruta `log/detail/{entryId}`, que se abre al tocar una tarjeta de la Bitácora o un pin del Mapa. |
| 4 | `:features:settings` | Botón de sincronización manual con estados de carga, éxito y error, y 4 tests. |

**Pendiente de validar a mano antes del PR:** en un dispositivo o emulador, tocar una tarjeta y un pin para abrir el detalle, volver, y sincronizar desde Ajustes con y sin conexión.

## Git
No se hizo ningún commit, merge ni push en ninguna etapa. Todos los cambios quedan en el working directory para que los revises.
