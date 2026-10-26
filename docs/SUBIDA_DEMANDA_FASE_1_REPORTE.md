# Subida a demanda — Fase 1: Limpieza de Ajustes (`:features:settings`)

## Objetivo cumplido de la fase
Se eliminó de Ajustes la sección **"Conectividad"** completa, con sus dos filas:
- **"Modo offline forzado"** (switch);
- **"Sincronización de Bitácora"** (botón de sincronización manual, agregado en la Etapa 4 del polish).

También se quitó del ViewModel todo el estado y la lógica que solo usaban esas filas. Ajustes queda con tres bloques: **Tu cuenta**, **Permisos** y **Acerca de y legales**.

## Archivos creados o modificados
| Archivo | Cambios |
|---|---|
| `features/settings/src/main/java/com/wandervox/settings/SettingsViewModel.kt` | • Se eliminó el `enum class LogSyncState`.<br>• Se quitaron de `SettingsUiState` los campos `isOfflineModeEnabled` (con su `TODO`) y `logSyncState`.<br>• Se sacó `LogRepository` del constructor: queda `SettingsViewModel(authRepository: AuthRepository)`.<br>• Se eliminaron `onOfflineModeChange(...)` y `syncPending()`. |
| `features/settings/src/main/java/com/wandervox/settings/SettingsScreen.kt` | • `SettingsScreen` ya no pasa `onOfflineModeChange` ni `onSyncLogClick`.<br>• `SettingsContent` pierde esos dos parámetros.<br>• Se eliminó del `LazyColumn` el `item { WanderVoxSettingsSection(title = "Conectividad") { … } }`.<br>• Se eliminó el composable privado `LogSyncItem`.<br>• Se quitaron los imports que quedaron sin uso: `Icons.Outlined.CloudOff`, `Icons.Outlined.Sync` y `WanderVoxSettingsSwitchItem`.<br>• Se actualizó el `@Preview`. |
| `features/settings/src/test/java/com/wandervox/settings/SettingsViewModelTest.kt` | • Se eliminaron los 4 tests de sincronización y el `FakeLogRepository`, con sus imports.<br>• El test `offline mode and permissions update the state` pasó a llamarse `permissions update the state` y ahora solo verifica los permisos.<br>• El ViewModel se crea con `SettingsViewModel(repository)`. |

**Diferencia contra `HEAD`** (`git diff --stat -- features/settings`): 3 archivos, +1 / −35 líneas. Todo lo que había agregado el polish quedó revertido, y además se quitó el modo offline, que ya existía en `HEAD`.

## Decisiones técnicas de diseño y flujo de datos
- **Se eliminó la sección entera, no solo las filas:** sin sus dos filas, la sección "Conectividad" quedaba vacía, y una cabecera sin contenido es ruido visual.
- **"Modo offline forzado" no se podía rescatar:** era un switch que solo vivía en memoria (`TODO: persistir y conectar…`) y no estaba conectado a nada. Ni el repositorio ni Firestore lo leían. Quitarlo no cambia ningún comportamiento real.
- **Sin sincronización manual global:** con la política de la Fase 2, una entrada solo sube a la nube cuando el usuario la comparte desde su tarjeta, así que un botón "sincronizar todo" no tiene sentido. `LogRepository.syncPending()` **sigue existiendo** en el dominio y en `:core:data` (no se tocó `:core:domain`). En la Fase 2 se decide si se usa internamente para reintentos.
- **Menos dependencias:** `SettingsViewModel` vuelve a depender solo de `AuthRepository`. Hilt no necesita cambios, porque `LogRepository` sigue registrado para los demás consumidores.
- **UDF sin cambios:** `SettingsUiState` sigue siendo inmutable, se expone como `StateFlow` y se lee con `collectAsStateWithLifecycle()`. `SettingsContent` sigue sin estado.
- **Componentes compartidos que quedaron sin uso:** `WanderVoxSettingsSwitchItem` (en `core/ui/.../SettingsComponents.kt`) ya no se usa en ningún lado. **No se eliminó** porque es parte del kit de diseño de `:core:ui`, que está fuera del alcance de esta fase. Se puede borrar después si no se planea usarlo. `DevelopmentBadge` se sigue usando en la bottom bar.

## Resultados de las pruebas ejecutadas
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat :features:settings:testDebugUnitTest :features:settings:assembleDebug
```
- Resultado: **BUILD SUCCESSFUL in 18s** (48 tareas accionables). Sin errores ni warnings del módulo.
- `SettingsViewModelTest`: **4 tests, 0 fallos, 0 errores**:
  1. `exposes the signed in user's email`
  2. `sign out success marks the session as logged out`
  3. `sign out failure shows error and keeps the session`
  4. `permissions update the state`
- Se buscó en `features/settings/src/main` y no quedan referencias a `Conectividad`, `OfflineMode`, `LogSync`, `syncPending` ni `LogRepository`.
- Solo se compiló `:features:settings`. El build completo del proyecto (incluido `:app`, que crea `SettingsScreen`) se corre en la Fase 3. `:app` no usaba los parámetros eliminados, así que no se espera ningún impacto.
- No se probó en un dispositivo ni en un emulador.

## Git
No se hizo ningún commit, merge ni push. Los cambios quedan en el working directory para que los revises.
