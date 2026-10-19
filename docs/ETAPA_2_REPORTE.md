# Etapa 2 — UI base de la Bitácora y navegación (Track Dev B)

**Fecha:** 2026-10-19 · **Rama:** `Feat/Luc-Bitacora` · **Responsable:** Lucas (Dev B)

## 1. Objetivo cumplido

- La pestaña **Bitácora** ya no muestra `UnderDevelopmentScreen`. Ahora tiene una pantalla Compose real
  con dos pestañas: **"Mis viajes"** y **"Comunidad"**.
- La pantalla sigue UDF/MVI: `LogViewModel` publica un `StateFlow<LogUiState>` inmutable, `LogScreen`
  (con estado) lo consume con `collectAsStateWithLifecycle()` y `LogContent` (sin estado) lo dibuja.
- Los datos vienen de `LogRepository`, que Hilt sigue resolviendo con `InMemoryLogRepository` hasta que
  Dev A termine Room.
- `./gradlew assembleDebug testDebugUnitTest` compila y pasa.
- **No se tocaron** `:core:domain`, `:core:database`, `:features:map` ni el Traductor.

## 2. Archivos creados o modificados

### Nuevos (`features/log/src/main/java/com/wandervox/features/log/presentation/`)

| Archivo | Contenido clave |
|---|---|
| `LogUiState.kt` | `enum LogTab { MY_TRIPS, COMMUNITY }` y `data class LogUiState(selectedTab, entries, selectedCategory, isLoading, isError, currentUserId)` |
| `LogUiEvent.kt` | `OnTabSelected(tab)`, `OnCategorySelected(category?)`, `OnRetry` |
| `LogViewModel.kt` | `@HiltViewModel`, inyecta `LogRepository` y `AuthRepository`. `flatMapLatest` por pestaña (líneas 52–57), filtro por categoría (67–69), `stateIn(WhileSubscribed(5s))` (75) y la fuente de cada pestaña con `onStart(Loading)` / `catch(Failed)` (90–99) |
| `LogScreen.kt` | Composable con estado: `hiltViewModel()`, `collectAsStateWithLifecycle()`, traduce los clics a `LogUiEvent` |
| `LogContent.kt` | Composable sin estado: `PrimaryTabRow` (línea 43), estados de carga, error con "Reintentar", vacío por pestaña y una lista mínima (`LazyColumn`). Incluye dos `@Preview` |
| `features/log/src/main/res/values/strings.xml` | Textos `log_*` en español (pestañas, vacíos, error, reintentar) |

### Modificados

| Archivo | Cambio |
|---|---|
| `features/log/build.gradle.kts` | `lifecycle-viewmodel-ktx`, `lifecycle-runtime-compose` y `testImplementation(kotlinx-coroutines-test)` |
| `app/.../main/MainScaffold.kt` (líneas 31, 38, 84) | Nuevo slot `logContent: @Composable (Modifier) -> Unit`, que reemplaza a `UnderDevelopmentScreen` en la ruta `log` |
| `app/.../navigation/WanderVoxNavHost.kt` (líneas 18, 80) | `logContent = { modifier -> LogScreen(modifier = modifier) }` |
| `app/.../main/MainTab.kt` (línea 17) | `Log(... inDevelopment = false)`: se quita la etiqueta "Próximamente" de la barra inferior |
| `docs/DEV_LOG.md` (sección 12) | Justificación de diseño y dependencias |

## 3. Decisiones de diseño y justificación

1. **Mismo patrón que el Traductor.** Mismo paquete `features.<feature>.presentation`, slot
   `(Modifier) -> Unit` en `MainScaffold`, `stateIn(WhileSubscribed(5_000))` y `onEvent(...)`. Así
   `:app` sigue siendo quien arma las features y `:features:log` no depende de `:app`.
2. **Nombres:** el plan permitía `LogRoute` o `LogScreen`. Usé `LogScreen` (con estado) + `LogContent`
   (sin estado), que es la convención que piden las reglas del track.
3. **El ViewModel depende solo de interfaces de dominio.** Cuando Dev A cambie el binding de
   `InMemoryLogRepository` a su repositorio Room en `DataModule`, esta pantalla no necesita cambios.
4. **"Mis viajes" usa el usuario autenticado.** `AuthRepository.currentUser` → `uid` →
   `getUserEntries(uid)`. "Comunidad" usa `getCommunityEntries()`.
5. **`flatMapLatest` sobre (pestaña, reintento, usuario).** Al cambiar de pestaña se cancela la
   suscripción anterior. `OnRetry` incrementa un contador para volver a suscribirse a una fuente que
   falló.
6. **El filtro por categoría ya está en el ViewModel.** `selectedCategory` filtra la lista de la pestaña
   activa. La Etapa 3 solo agrega los chips y los tests.
7. **El error es un flag (`isError`), no un texto.** La UI elige el string de recursos, así el
   ViewModel no tiene textos para el usuario (el Traductor todavía tiene un TODO por eso).
8. **La lista es mínima a propósito** (texto original + traducción en una `ElevatedCard`). La tarjeta
   completa (categoría, autor, estado de sync, like) es parte de la Etapa 3.
9. **`PrimaryTabRow` sin `@OptIn`.** Con el Compose BOM 2026.02.01 la API es estable y compiló sin
   advertencias.

## 4. ⚠️ Problema detectado (coordinar con Dev A)

`TranslatorViewModel.saveToLog` arma el `TravelLogEntry` **sin `userId`** (queda `""`). Por eso las
entradas que se guardan desde el Traductor **no aparecen en "Mis viajes"**, que filtra por el `uid` de
Firebase Auth. No lo corregí porque el Traductor y el repositorio son de Dev A. Hay dos opciones:

- **(a)** Que `saveEntry` del repositorio (Room / `OfflineFirstLogRepository`) complete el `userId`
  desde `AuthRepository`. **Recomendada:** un solo lugar, y también sirve para el futuro alta manual
  desde la Bitácora.
- **(b)** Que el `TranslatorViewModel` inyecte `AuthRepository` y complete `userId` al guardar.

Mientras tanto, la pestaña "Mis viajes" muestra el estado vacío aunque se guarden traducciones.

## 5. Comandos de verificación y resultados

Usé el JDK 17 de `~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`, igual que en la Etapa 1.

| Comando | Resultado |
|---|---|
| `./gradlew assembleDebug testDebugUnitTest` | ✅ BUILD SUCCESSFUL, sin advertencias del compilador Kotlin ni errores |

**No se probó en un dispositivo ni en un emulador.** La navegación (barra inferior → Bitácora → cambio
entre pestañas) está verificada por compilación y revisando el código. Conviene abrir la app una vez
para confirmarlo visualmente. Los tests de `LogViewModel` son parte de la Etapa 3.
