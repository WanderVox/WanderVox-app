# Etapa 5 — Fuentes de datos remotas: Firestore y Cloudinary (Track Dev B)

**Fecha:** 2026-10-19 · **Rama:** `Feat/Luc-Bitacora` · **Responsable:** Lucas (Dev B)

## 1. Objetivo cumplido

- Se creó el paquete **`com.wandervox.core.network.remote`** en `:core:network`, con:
  - **`LogRemoteDataSource`** sobre Firestore: `upsert(entry)`, `delete(entryId)`,
    `observeUserEntries(userId)`, `observePublicEntries()` y `setLiked(...)`. Los me gusta son atómicos
    con `FieldValue.arrayUnion` / `arrayRemove`.
  - **Persistencia offline nativa de Firestore**, activada explícitamente con `PersistentCacheSettings`.
  - **`ImageUploader`** sobre Cloudinary con un **Unsigned Upload Preset**. Devuelve la URL HTTPS segura
    (`secure_url`) para `remotePhotoUrl`. **No hay ningún secreto en el APK.**
- Todo se expone con Hilt en **`NetworkRemoteModule`** (`@Binds` para las interfaces y `@Provides` solo
  para objetos del SDK y de configuración).
- **9 tests nuevos.** `assembleDebug` y `testDebugUnitTest` pasan en todo el proyecto (64/64).
- No se tocaron `:core:domain`, `:core:database`, `:core:data`, `:features:map` ni el Traductor.

## 2. Archivos creados o modificados

### Nuevos — `core/network/src/main/java/com/wandervox/core/network/`

| Archivo | Contenido clave |
|---|---|
| `remote/log/LogRemoteDataSource.kt` | Contrato documentado: semántica de confirmación del servidor, los me gusta fuera del upsert y emisión desde caché offline |
| `remote/log/FirestoreLogRemoteDataSource.kt` | Colección `log_entries`. `set(..., SetOptions.merge())`, `update(likes, arrayUnion/arrayRemove)`, `callbackFlow` + `addSnapshotListener(MetadataChanges.INCLUDE)` y `awaitAcknowledged()` con timeout de 15 s |
| `remote/log/LogEntryFirestoreMapper.kt` | `LogFields` (nombres de campos) y `toFirestore()` / `fromFirestore()`, tolerante a datos faltantes o mal formados |
| `remote/image/ImageUploader.kt` | `suspend fun upload(localPath): Result<String>` |
| `remote/image/CloudinaryImageUploader.kt` | `CloudinaryConfig`, `CloudinaryUploadException(httpCode)`, POST multipart con `file` + `upload_preset`, lectura de `secure_url` (exige `https://`) y llamada OkHttp cancelable |
| `di/NetworkRemoteModule.kt` | `@Binds` FirestoreLogRemoteDataSource → LogRemoteDataSource y CloudinaryImageUploader → ImageUploader. `@Provides` FirebaseFirestore (caché persistente), OkHttpClient (timeouts 15/60/30 s) y CloudinaryConfig (desde BuildConfig) |

### Nuevos — tests (`core/network/src/test/java/com/wandervox/core/network/remote/`)

| Archivo | Tests |
|---|---|
| `log/LogEntryFirestoreMapperTest.kt` | 4: ida y vuelta completa · el mapa de escritura excluye `localPhotoPath`, `syncStatus`, `id` y `likes` · escrituras pendientes → `PENDING` · campos faltantes o mal formados → valores por defecto |
| `image/CloudinaryImageUploaderTest.kt` | 5: éxito (URL, método, `upload_preset`, archivo y que no se envían `api_key` ni `signature`) · error HTTP con el mensaje de Cloudinary y su código · respuesta sin URL https · sin configuración no hace ningún request · archivo inexistente no hace ningún request |

### Modificados

| Archivo | Cambio |
|---|---|
| `core/network/build.gradle.kts` | `buildConfig = true`, `buildConfigField` `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_UPLOAD_PRESET` leídos de `local.properties` (compatible con el configuration cache). **Se quitó** `coroutines-play-services` (ver 3.8). Tests: `coroutines-test` y `org.json` |
| `gradle/libs.versions.toml` | `orgJson = "20240303"` y `org-json` (**solo tests**) |
| `docs/DEV_LOG.md` (sección 15) | Justificación |

## 3. Decisiones de diseño y justificación

1. **Escrituras con timeout de confirmación.** Firestore aplica la escritura en la caché local al
   instante, pero su `Task` recién termina cuando responde el servidor, y sin conexión nunca termina.
   `upsert`, `delete` y `setLiked` esperan como máximo 15 s y, si no hay respuesta, fallan con
   `TimeoutException`. La escritura **igual queda encolada** y se envía al volver la conexión. Así el
   repositorio puede dejar la entrada en `PENDING` sin quedarse colgado, y reintentar es seguro porque
   el upsert es idempotente (`merge`).
2. **Los me gusta nunca van en el upsert.** Solo cambian con `arrayUnion` / `arrayRemove`, que son
   atómicos y funcionan offline. Si el upsert escribiera la lista completa, podría pisar me gusta de
   otros usuarios. No se usa una transacción porque falla sin conexión.
3. **`setLiked(entryId, userId, liked)` en vez de `toggle`.** El repositorio ya sabe el estado actual
   por su copia local. Un toggle en el servidor requeriría leer primero, es decir, una transacción.
4. **Orden en el cliente.** `whereEqualTo` + `orderBy` sobre otro campo requiere un índice compuesto
   creado en la consola. Ordenar en memoria evita esa dependencia de infraestructura.
5. **`MetadataChanges.INCLUDE` + `hasPendingWrites`.** Cada documento llega como `PENDING` mientras no
   está confirmado y como `SYNCED` cuando lo está. El listener vuelve a emitir cuando cambia ese estado.
6. **Lo local no sale del dispositivo.** No se escriben `localPhotoPath` ni `syncStatus`. Las coordenadas
   se guardan como `latitude` / `longitude` (doubles) en vez de `GeoPoint`, así el mapper no depende de
   Firebase y se testea en la JVM.
7. **Cloudinary vía OkHttp con preset unsigned** (sigue la decisión de `DEV_LOG §10.2`). En el APK solo
   van el *cloud name* y el **nombre** del preset. Carpeta, tamaño máximo y formatos permitidos se
   configuran en el preset desde la consola de Cloudinary. La llamada usa `enqueue` y se cancela si se
   cancela la corrutina.
8. **Reutilicé utilidades del módulo.** `util/awaitResult()` (Task → suspend) y `runSuspendCatching`
   (no se traga `CancellationException`) ya existían, así que **quité `kotlinx-coroutines-play-services`**,
   que había agregado en la Etapa 1 y quedaba sin uso.
9. **`org.json` solo en tests.** En runtime Android ya incluye `org.json`, pero el `android.jar` de los
   tests unitarios solo trae stubs que lanzan excepciones. Es una dependencia `testImplementation` y no
   entra al APK.
10. **`FirebaseFirestore` se provee acá.** Ningún módulo lo proveía antes (`:core:data` solo provee
    `FirebaseAuth`), así que no hay bindings duplicados.

## 4. Configuración necesaria (manual)

### 4.1 Cloudinary
1. En la consola de Cloudinary, ir a **Settings → Upload → Add upload preset** y elegir **Signing mode:
   Unsigned**. Conviene fijar la carpeta (`wandervox/log`), los formatos permitidos (jpg, png, webp,
   heic) y un tamaño máximo.
2. Agregar en `local.properties` (no se sube a Git):
   ```properties
   cloudinary.cloudName=<tu cloud name>
   cloudinary.uploadPreset=<nombre del preset unsigned>
   ```
   **Nunca** pongas ahí el API secret. Si no están configurados, la app compila igual y `upload()`
   devuelve `CloudinaryUploadException("Cloudinary is not configured ...")`.

### 4.2 Firestore
Hay que crear la base de datos Firestore en el proyecto Firebase. Reglas de seguridad **sugeridas**
(no se desplegaron; el repositorio no tiene `firestore.rules`):

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /log_entries/{entryId} {
      allow read: if request.auth != null
        && (resource.data.isPublic == true || resource.data.userId == request.auth.uid);
      allow create: if request.auth != null && request.resource.data.userId == request.auth.uid;
      allow update: if request.auth != null && (
        (resource.data.userId == request.auth.uid
          && request.resource.data.userId == resource.data.userId)
        || (resource.data.isPublic == true
          && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['likes'])));
      allow delete: if request.auth != null && resource.data.userId == request.auth.uid;
    }
  }
}
```

Las dos consultas (`userId == uid` e `isPublic == true`) cumplen estas reglas, así que Firestore las
acepta.

## 5. Comandos de verificación y resultados

Usé el JDK 17 de `~/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2`.

| Comando | Resultado |
|---|---|
| `./gradlew :core:network:testDebugUnitTest` | ✅ `CloudinaryImageUploaderTest` 5/5 · `LogEntryFirestoreMapperTest` 4/4 · `ExampleUnitTest` 1/1 |
| `./gradlew assembleDebug testDebugUnitTest --rerun-tasks` | ✅ BUILD SUCCESSFUL en 2m 42s, **0 advertencias de Kotlin**, **64/64 tests** en todo el proyecto |
| BuildConfig generado | `CLOUDINARY_CLOUD_NAME = ""` y `CLOUDINARY_UPLOAD_PRESET = ""`, porque todavía no están en `local.properties` |

**No se probó contra un proyecto Firestore real ni una cuenta de Cloudinary.** Los tests del uploader
usan un `Interceptor` de OkHttp, sin red. `FirestoreLogRemoteDataSource` no tiene tests unitarios
porque depende del SDK; su lógica de mapeo sí está cubierta.

---

## 6. Resumen final de integración (Track Dev B, etapas 1–5)

### 6.1 Qué quedó listo

| Capa | Pieza | Estado |
|---|---|---|
| `:features:log` | Pestañas "Mis viajes" / "Comunidad", tarjetas, filtro por categoría, me gusta, borrado con confirmación y estados vacío/error | ✅ Contra `LogRepository` |
| `:features:log` | Selector de fotos → copia a `filesDir` → `localPhotoPath` → visualización con Coil | ✅ |
| `:core:network/remote` | `LogRemoteDataSource` (Firestore + offline) e `ImageUploader` (Cloudinary unsigned) | ✅ Expuestos por Hilt, **todavía sin consumidor** |
| `:app` | Pestaña Bitácora conectada en `MainScaffold` / `WanderVoxNavHost` | ✅ |
| Tests | 17 `LogViewModelTest` + 9 en `:core:network` | ✅ 64/64 en el proyecto |

### 6.2 Lo que falta para cerrar el circuito (coordinar con Dev A)

El `OfflineFirstLogRepository` de Dev A en `:core:data` (que ya depende de `:core:network`) debe
inyectar `LogRemoteDataSource` e `ImageUploader` y reemplazar a `InMemoryLogRepository` en
`DataModule`. Flujo propuesto para `saveEntry` / `syncPending()`:

1. Guardar en Room con `syncStatus = PENDING`. Esa es la fuente de verdad local.
2. Si hay `localPhotoPath` y no hay `remotePhotoUrl`, llamar a `imageUploader.upload(localPhotoPath)`
   y guardar la URL.
3. Llamar a `remote.upsert(entry)`. Si sale bien, marcar `SYNCED`. Si da timeout, dejar `PENDING`. Ante
   cualquier otro error, marcar `FAILED`.
4. `toggleLike` → actualizar Room y después `remote.setLiked(id, uid, liked)`.
5. `getCommunityEntries()` → `remote.observePublicEntries()`, con el respaldo de la caché offline de
   Firestore.

Con esto, la UI de la Bitácora **no necesita cambios**: solo depende de `LogRepository`.

### 6.3 ⚠️ Bloqueante para la sincronización

Las entradas del Traductor siguen guardándose **sin `userId`** (ver Etapa 2). Con las reglas sugeridas,
Firestore **rechazaría** esas escrituras (`request.resource.data.userId == request.auth.uid`), y además
no aparecen en "Mis viajes". Propuesta: que `saveEntry` del repositorio complete `userId` (y
`authorName`) desde `AuthRepository` antes de guardar.

### 6.4 Otros pendientes
- Crash de lint en `:core:ui`: ya estaba antes, ver Etapa 4.
- Pantalla de detalle de entrada (`getEntry()` ya existe).
- Opcional: redimensionar las fotos antes de subirlas, si el tamaño resulta un problema.
- Nada está commiteado todavía. Commit sugerido:
  `feat(log): travel log UI, local photos and Firestore/Cloudinary remote sources`.
