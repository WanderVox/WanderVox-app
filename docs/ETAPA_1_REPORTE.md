# Etapa 1 — Dependencias del catálogo y contratos base (Track Dev B)

**Fecha:** 2026-10-19 · **Rama:** `Feat/Luc-Bitacora` · **Responsable:** Lucas (Dev B)

## 1. Objetivo cumplido

- Ya estaban en `gradle/libs.versions.toml` desde el Day-0 freeze (commit `c68af24`) Coil, Firestore y el
  cliente HTTP para Cloudinary. **No se agregó ninguna entrada al catálogo** y no hay duplicados.
- Esas dependencias quedaron conectadas a los módulos de Dev B: `:features:log` y `:core:network`.
- Se revisaron los contratos de dominio (`LogRepository`, `LogCategory`, `TravelLogEntry`). Alcanzan para
  las etapas 2 a 5 y **no se modificó `:core:domain`**.
- `./gradlew help` y `./gradlew assembleDebug` compilan sin errores.

## 2. Archivos creados o modificados

| Archivo | Cambio |
|---|---|
| `features/log/build.gradle.kts` (línea 67) | `implementation(libs.coil.compose)` |
| `core/network/build.gradle.kts` (líneas 61–65) | `platform(libs.firebase.bom)`, `libs.firebase.firestore`, `libs.kotlinx.coroutines.play.services`, `libs.okhttp` |
| `docs/DEV_LOG.md` (sección 11, nueva) | Justificación de las dependencias, como pide `CLAUDE.md` |
| `docs/ETAPA_1_REPORTE.md` (nuevo) | Este reporte |

Entradas del catálogo que se usan (ya existían):

```toml
coil = "2.7.0"            # coil-compose = io.coil-kt:coil-compose
okhttp = "4.12.0"         # okhttp = com.squareup.okhttp3:okhttp
firebaseBom = "33.7.0"    # firebase-firestore (sin versión, la fija el BOM → 25.1.1)
kotlinxCoroutines = "1.8.1" # kotlinx-coroutines-play-services
```

## 3. Decisiones de diseño y justificación

### 3.1 Cloudinary vía OkHttp, no `com.cloudinary:cloudinary-android` ⚠️

El plan pedía el SDK de Cloudinary para Android. **Lo dejé afuera a propósito**, siguiendo la decisión ya
documentada en `DEV_LOG.md §10.2` y `Refactor_Log.md`:

- `cloudinary-android` (y también `cloudinary-android-core` 3.1.x) trae como dependencias de runtime
  `appcompat`, `material` (Views), `legacy-support-v4` y `work-runtime`. Eso choca con el invariante
  "solo Compose".
- Para un **Unsigned Upload Preset** alcanza con un único `POST` multipart a
  `https://api.cloudinary.com/v1_1/<cloud>/image/upload` con el campo `upload_preset`. OkHttp lo resuelve
  y en el APK solo queda el nombre público del preset, nunca el API secret.
- Los reintentos de subida los maneja `LogRepository.syncPending()`. No hace falta la integración del SDK
  con WorkManager.

Si igual prefieren usar el SDK, se agrega en una línea, pero hay que justificar en `DEV_LOG.md` las
dependencias de Views que trae.

### 3.2 `firebase-firestore`, no `firebase-firestore-ktx`

Desde el BOM 32.5.0 las APIs KTX vienen en el artefacto principal y los módulos `-ktx` están deprecados.
La versión la fija el BOM 33.7.0, que es el mismo que ya usa `:core:data` para Auth.

### 3.3 `kotlinx-coroutines-play-services` en `:core:network`

Da `Task.await()`, que convierte las llamadas de Firestore en `suspend` y permite envolverlas en
`Result<T>`, como pide `CLAUDE.md`. Es la misma librería que usa `:core:data` para Firebase Auth.

### 3.4 Coil 2.7.0 (no Coil 3) en `:features:log`

Coil 2 es solo Android/Compose y es compatible con Kotlin 2.0.21 y el Compose BOM actual. Coil 3 cambia el
group id, usa el layout KMP y necesita un artefacto de red aparte, sin ninguna ventaja para este proyecto.

### 3.5 Dependencias transitivas de Views (auditoría)

Revisé `:app:debugRuntimeClasspath`:

- `androidx.appcompat:appcompat:1.6.1` **ya estaba antes de este cambio**. Lo trae
  `com.google.mlkit:common` (ML Kit Translate, Track A). Lo comprobé con `git stash` y la misma consulta.
- Coil 2.7.0 solo suma `appcompat-resources`, que usa para inflar drawables. Ningún módulo lo usa desde
  código.
- `com.google.android.material:material` (Views) **no aparece**.

### 3.6 Contratos de dominio: revisados y sin cambios

| Contrato | Qué tiene | Etapa en que se usa |
|---|---|---|
| `LogCategory` | `FOOD, TRANSPORT, LODGING, SHOPPING, EMERGENCY, GENERAL`. Se persiste el `name` y las etiquetas van en strings de cada feature | 3 (chips de filtro) |
| `TravelLogEntry` | `localPhotoPath`, `remotePhotoUrl`, `likes: List<String>`, `authorName`, `isPublic`, `updatedAt`, `syncStatus` | 3, 4, 5 |
| `SyncStatus` | `PENDING, SYNCED, FAILED` | 3 (badge) |
| `LogRepository` | `getUserEntries`, `getCommunityEntries`, `getEntry`, `saveEntry`, `toggleLike`, `deleteEntry`, `syncPending`, todos con `Result<Unit>` / `Flow` | 2 (ViewModel) |

`:core:domain` sigue siendo Kotlin JVM puro (solo depende de `kotlinx-coroutines-core`).
`InMemoryLogRepository` sigue registrado en `DataModule` (`:core:data`), así que en la Etapa 2 se puede
avanzar sin esperar Room.

Observación para la Etapa 5: `LogRemoteDataSource` vive en `:core:network`, y `:core:network` ya depende
de `:core:domain`. Así que puede recibir y devolver `TravelLogEntry` directamente, mapeado a un DTO de
Firestore dentro del paquete `remote`.

## 4. Comandos de verificación y resultados

Nota: en esta terminal no estaba definido `JAVA_HOME`. Usé el JDK 17 que Gradle ya había descargado
(`~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`).

| Comando | Resultado |
|---|---|
| `./gradlew help` | ✅ BUILD SUCCESSFUL. El catálogo es válido |
| `./gradlew assembleDebug` | ✅ BUILD SUCCESSFUL en 3m 7s (317 tareas) |
| `./gradlew :app:dependencies --configuration debugRuntimeClasspath` | `firebase-firestore:25.1.1`, `coil-compose:2.7.0`, `okhttp:4.12.0` (también resuelve el `okhttp:3.0.0` que pide ML Kit Translate), `firebase-bom:33.7.0` |
| `./gradlew :app:dependencyInsight --dependency androidx.appcompat:appcompat` | Viene de ML Kit (`mlkit:common`). Coil solo suma `appcompat-resources` |

No se corrieron tests unitarios porque en esta etapa no hay código nuevo. No se probó en un dispositivo.

## 5. Pendiente para la Etapa 2

- Agregar a `:features:log` `lifecycle-runtime-compose` (`collectAsStateWithLifecycle`) y, para tests,
  `kotlinx-coroutines-test`, cuando se cree el `LogViewModel`.

> **Actualización (Etapa 5):** se quitó `kotlinx-coroutines-play-services` de `:core:network`, porque
> el módulo ya tenía `util/awaitResult()` para convertir `Task` en llamadas `suspend`. Ver
> `ETAPA_5_REPORTE.md` §3.8.
