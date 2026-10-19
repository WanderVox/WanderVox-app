# Etapa 4 — Selector de fotos local y visualización con Coil (Track Dev B)

**Fecha:** 2026-10-19 · **Rama:** `Feat/Luc-Bitacora` · **Responsable:** Lucas (Dev B)

## 1. Objetivo cumplido

- **Selector de fotos de Android** con `ActivityResultContracts.PickVisualMedia` (`ImageOnly`). **No
  se pide ningún permiso de almacenamiento.**
- La imagen elegida se **copia al sandbox de la app** (`context.filesDir/log_photos/`). Así sigue
  disponible sin conexión aunque el usuario la borre de la galería.
- La ruta de la copia se guarda en **`TravelLogEntry.localPhotoPath`**, con `syncStatus = PENDING` para
  que la Etapa 5 la suba.
- Las fotos se ven en las tarjetas con **`AsyncImage` de Coil**: crossfade, placeholder de color y un
  mensaje de respaldo si la imagen no carga.
- **5 tests nuevos** del flujo de fotos (17 en total en `LogViewModelTest`), y el build completo pasa.
- No se tocaron `:core:domain`, `:core:database`, `:features:map`, el Traductor ni `:app`.

## 2. Archivos creados o modificados

### Nuevos

| Archivo | Contenido clave |
|---|---|
| `features/log/.../log/photo/LogPhotoStorage.kt` | Interfaz con `importPhoto(sourceUri: String, entryId): Result<String>` y `deletePhoto(path): Result<Unit>` |
| `features/log/.../log/photo/InternalLogPhotoStorage.kt` | Copia con `contentResolver.openInputStream` → `filesDir/log_photos/<entryId>_<uuid>.<ext>`. La extensión sale del MIME vía `MimeTypeMap`. Corre en `Dispatchers.IO`, nunca se traga `CancellationException` y `deletePhoto` solo borra dentro de su carpeta |
| `features/log/.../log/di/LogModule.kt` | `@Binds InternalLogPhotoStorage → LogPhotoStorage` (`SingletonComponent`) |
| `features/log/.../presentation/components/LogPhoto.kt` | `AsyncImage` + `ImageRequest.crossfade(true)`, `ContentScale.Crop`, placeholder/error de color, ícono `BrokenImage` + "La foto no está disponible" y spinner mientras se importa. Prioridad: archivo local si existe; si no, `remotePhotoUrl` |

### Modificados

| Archivo | Cambio |
|---|---|
| `LogUiEvent.kt` | `OnPhotoPicked(entryId, sourceUri: String)` |
| `LogUiState.kt` | `LogActionError.PHOTO` y `importingPhotoEntryId` |
| `LogViewModel.kt` | Inyecta `LogPhotoStorage`. `attachPhoto()`: copiar → `saveEntry(localPhotoPath, remotePhotoUrl = null, PENDING, updatedAt)` → borrar la copia anterior. Si `saveEntry` falla, borra la copia nueva. `deleteEntry()` también borra la foto |
| `LogScreen.kt` | `rememberLauncherForActivityResult(PickVisualMedia())`. La entrada destino se guarda con `rememberSaveable`, porque el selector es otra Activity |
| `LogContent.kt` | Pasa `onPickPhoto` e `isImportingPhoto` a cada tarjeta (solo entradas propias en "Mis viajes") |
| `components/LogEntryCard.kt` | Foto arriba de la tarjeta (180 dp) y botón "Agregar foto / Cambiar foto" |
| `res/values/strings.xml` | `log_add_photo`, `log_change_photo`, `log_photo_description`, `log_photo_unavailable`, `log_error_photo` |
| `features/log/build.gradle.kts` | `activity-compose` (ya estaba en el catálogo) |
| `LogViewModelTest.kt` | `FakePhotoStorage`, upsert en `FakeLogRepository.saveEntry` y 5 tests nuevos |
| `docs/DEV_LOG.md` (sección 14) | Justificación |

## 3. Decisiones de diseño y justificación

1. **Selector de fotos sin permisos.** `PickVisualMedia` no necesita `READ_MEDIA_IMAGES` ni
   `READ_EXTERNAL_STORAGE`. En dispositivos sin el selector del sistema (API 27–29 sin el backport de
   Play Services), el contrato abre `ACTION_OPEN_DOCUMENT`, que tampoco pide permisos. **No se agregó
   ningún permiso al manifest.**
2. **Copia en `filesDir` en vez de guardar el URI.** El permiso sobre el URI del selector es temporal y el
   original puede borrarse. La copia privada no requiere permisos, funciona offline y se elimina al
   desinstalar la app.
3. **Un nombre de archivo nuevo en cada importación** (`<entryId>_<uuid>`). Si se reemplaza una foto,
   Coil nunca muestra la versión vieja desde su caché.
4. **El URI llega al ViewModel como `String`.** Así `LogViewModel` no depende de `android.net.Uri` y se
   testea en la JVM con un fake de `LogPhotoStorage`.
5. **Consistencia entre archivo y registro:**
   - si la copia falla, la entrada no cambia;
   - si `saveEntry` falla, se borra la copia nueva y no quedan archivos huérfanos;
   - recién cuando el guardado sale bien se borra la foto anterior;
   - al eliminar la entrada, se elimina su foto.
6. **Al cambiar la foto se pone `remotePhotoUrl = null` y `syncStatus = PENDING`.** La URL de Cloudinary
   anterior ya no corresponde a la foto, y la Etapa 5 sube la nueva en el próximo `syncPending()`.
7. **Primero la foto local y después la remota (offline-first).** `remotePhotoUrl` se usa para entradas
   de "Comunidad" que vienen de otros dispositivos.
8. **Dónde se puede agregar una foto.** Todavía no hay pantalla para crear entradas desde la Bitácora
   (se crean desde el Traductor), así que la foto se agrega o cambia desde la tarjeta de una entrada
   propia en "Mis viajes".
9. **`Dispatchers.IO` directo en `InternalLogPhotoStorage`.** El qualifier `@IoDispatcher` vive en
   `:core:network`, y que una feature dependa de ese módulo rompería las capas.
10. **Sin recomprimir.** Se copian los bytes originales. Si el tamaño de subida a Cloudinary resulta un
    problema, se puede redimensionar en la Etapa 5.

## 4. Tests nuevos (`LogViewModelTest`, sección "Photos")

| Test | Qué verifica |
|---|---|
| `picked photo is copied and stored as a pending local photo` | `localPhotoPath` queda con la ruta de la copia, `remotePhotoUrl` en null, estado `PENDING` y sin spinner ni error |
| `replacing a photo deletes the previous copy` | Al reemplazar se borra la copia vieja |
| `failed copy keeps the entry unchanged and reports a photo error` | La entrada no cambia y aparece `actionError = PHOTO` |
| `failed save removes the copied photo` | No quedan archivos huérfanos |
| `deleting an entry also deletes its photo` | Al borrar la entrada se borra su archivo |

## 5. Comandos de verificación y resultados

Usé el JDK 17 de `~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`.

| Comando | Resultado |
|---|---|
| `./gradlew :features:log:testDebugUnitTest` | ✅ `LogViewModelTest`: 17 tests, 0 fallas |
| `./gradlew assembleDebug testDebugUnitTest --rerun-tasks` | ✅ BUILD SUCCESSFUL, 0 advertencias de Kotlin, **55/55** tests en todo el proyecto |
| `./gradlew :features:log:lintDebug` | ⚠️ El reporte de `:features:log` tiene **0 errores** y 1 advertencia (`ObsoleteLintCustomCheck`, de la herramienta). La tarea falla porque lint se rompe al analizar `:core:ui`. **El problema ya estaba antes:** sin estos cambios, `:features:translator:lintDebug` falla igual |

**No se probó en un dispositivo.** Pruebas manuales recomendadas:
1. Agregar una foto a una entrada.
2. Cerrar y volver a abrir la app.
3. Borrar la foto original de la galería y confirmar que la tarjeta la sigue mostrando.

Ojo: hoy el repositorio es `InMemoryLogRepository`, así que al cerrar la app se pierden las entradas
(aunque el archivo quede en `filesDir`). La persistencia real llega con el repositorio Room de Dev A.

## 6. Pendientes que siguen abiertos

- Sigue abierto lo de la Etapa 2: las entradas del Traductor no tienen `userId`, así que no aparecen en
  "Mis viajes" y no se les puede agregar foto.
- Crash de lint en `:core:ui`: ya estaba antes y conviene revisarlo con Dev A. La sección 9.4 del
  DEV_LOG había resuelto un problema parecido.
