# Entradas públicas — Etapa 3: Verificación end-to-end, build completo y documentación

## Objetivo cumplido de la etapa
1. Se revisó **todo el camino de `isPublic`** de punta a punta:
   - UI (hoja de guardado y tarjeta);
   - ViewModel;
   - `OfflineFirstLogRepository`;
   - Room;
   - sincronización;
   - `FirestoreLogRemoteDataSource`;
   - consulta pública;
   - pestaña Comunidad.

   Para cubrir lo que no tenía tests, se agregaron 4 tests nuevos en `:core:data` y `:core:network`.
2. `./gradlew clean assembleDebug testDebugUnitTest` pasa en los 11 módulos: **157 tests, 0 fallos**.
3. Este reporte cierra la implementación e incluye la explicación técnica de cómo funciona Comunidad.

## Archivos creados o modificados en esta etapa
Solo se agregaron **tests**; no se cambió código de producción.

| Archivo | Cambio |
|---|---|
| `core/network/src/test/java/com/wandervox/core/network/remote/log/LogEntryFirestoreMapperTest.kt` | Test nuevo `visibility is always written under the field the community query filters on`. |
| `core/data/src/test/java/com/wandervox/core/data/repository/OfflineFirstLogRepositoryTest.kt` | 3 tests nuevos en la sección `// --- public entries ---` (y los imports necesarios). |

### Resumen de las 3 etapas (todo sin commitear)
| Etapa | Módulo | Cambio |
|---|---|---|
| 1 | `:features:translator` | Switch "Compartir con la comunidad" en `SaveToLogSheet`. `OnSaveToLog(isPublic)` → `TravelLogEntry.isPublic`. |
| 2 | `:features:log` | Indicador Pública/Privada y botón para cambiarla en las tarjetas propias de "Mis viajes". Visibilidad visible en el detalle. |
| 3 | `:core:data` y `:core:network` (solo tests) | Verificación del camino de sincronización y build completo. |

## Verificación del camino de sincronización

### Paso por paso
1. **Guardado (UI → Room):** `TranslatorViewModel` (o `LogViewModel.toggleVisibility`) llama a `LogRepository.saveEntry(entry.copy(isPublic = …))`.
2. **Room:** `OfflineFirstLogRepository.saveEntry` hace upsert de la fila con `isPublic` y `syncStatus = PENDING`, y devuelve el control enseguida.
3. **Sincronización:** en `appScope` se ejecuta `syncEntry` → `uploadAndRecord`. Si hay foto, la sube a Cloudinary, y después llama a `remote.upsert(document)`.
4. **Firestore:** `FirestoreLogRemoteDataSource.upsert` hace `set(toFirestore(entry), SetOptions.merge())`. El mapper **siempre** escribe la clave `"isPublic"`, también con valor `false`, así que el merge vuelve privado un documento que antes era público.
5. **Confirmación:** si el servidor confirma, la fila de Room pasa a `SYNCED`. Si no, queda `FAILED` y se reintenta con `syncPending()` (botón de Ajustes) o en el próximo guardado.
6. **Feed:** `observePublicEntries()` = `whereEqualTo("isPublic", true)` + `addSnapshotListener(MetadataChanges.INCLUDE)`. El listener vuelve a emitir cuando un documento entra o sale del resultado de la consulta.

### Tests que cubren cada paso
| Paso | Test |
|---|---|
| UI → entidad con `isPublic` | `TranslatorViewModelTest`: `save is private unless the user shares it` y `shared save marks the entry as public` (Etapa 1). |
| Cambio de visibilidad → `PENDING` | `LogViewModelTest`: `toggling a private entry makes it public and pending sync` y otros 3 (Etapa 2). |
| Room ↔ entidad | `TravelLogMapperTest` (ya existía, incluye `isPublic = true`). |
| **Repositorio → remoto con `isPublic = true`** | **Nuevo:** `saveEntry_publicEntry_uploadsIsPublicTrue`: el documento que se sube tiene `isPublic = true` y la fila queda `SYNCED`. |
| **Volver a privada → se resube `false`** | **Nuevo:** `saveEntry_madePrivate_reuploadsIsPublicFalse`: las subidas son `[true, false]`. |
| **Clave del documento = campo de la consulta** | **Nuevo:** `visibility is always written under the field the community query filters on`: `toFirestore(...)["isPublic"]` es `true` o `false`, nunca falta. |
| **Feed en vivo** | **Nuevo:** `communityFeed_showsEntryOnceSyncedAsPublic_andDropsItWhenPrivate`. Un remoto falso guarda lo que se sube y su "consulta pública" es un filtro en vivo sobre esos datos. La entrada aparece en `getCommunityEntries()` cuando se sincroniza como pública y desaparece cuando se vuelve privada. |

### Lo que los tests de JVM **no** pueden verificar (pendiente en un dispositivo)
- **Firestore real:** los tests usan un remoto falso. Que Firestore reciba `isPublic: true` y que el listener lo detecte se verifica en un dispositivo o emulador con:
  - **Firebase Console** → colección `log_entries` → el documento debe tener `isPublic: true`;
  - un **segundo usuario**, para ver la entrada en su pestaña Comunidad.
- **Reglas de seguridad de Firestore:** no están en el repositorio (se administran desde la consola). Para que Comunidad funcione deben permitir `list` cuando la consulta está acotada a `isPublic == true`, por ejemplo: `allow read: if request.auth != null && (resource.data.isPublic == true || resource.data.userId == request.auth.uid);`. Las reglas de Firestore **no son filtros**: si la regla no acepta la consulta, el listener falla con `PERMISSION_DENIED` y la app pasa al fallback de Room (ver la explicación más abajo).

## Manejo de estado y decisiones de diseño
- **Sin cambios de producción en esta etapa:** la revisión mostró que la capa de datos ya manejaba `isPublic` de punta a punta. El problema original (Comunidad vacía) era solo que la UI nunca permitía elegir `true`.
- **Tests en `:core:data` y `:core:network`:** el contrato que importa (lo que se sube y la clave del documento) vive en esas capas. Probarlo desde un feature hubiera necesitado dependencias de datos o de red, y eso viola el aislamiento de módulos.
- **Un ajuste en el test del feed:** la primera versión fallaba porque `advanceUntilIdle()` no ejecuta las corrutinas de `backgroundScope`, así que el colector no llegaba a procesar la emisión. Se corrigió colectando con `UnconfinedTestDispatcher(testScheduler)`, el mismo patrón que usan los otros tests del proyecto. No era un error del código de producción.

## Resultados de las pruebas y comandos ejecutados
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat clean assembleDebug testDebugUnitTest
```
- **Primera ejecución:** `BUILD FAILED`. Falló 1 test nuevo (`communityFeed_…`, `expected:<[e1]> but was:<[]>`) por el problema de dispatcher explicado arriba.
- **Después de corregir el test:** **BUILD SUCCESSFUL in 1m 51s** (419 tareas accionables: 383 ejecutadas; build limpio).
- APK generado: `app/build/outputs/apk/debug/app-debug.apk`.
- **157 tests en 23 suites, 0 fallos, 0 errores.** Las suites relevantes:

| Suite | Tests |
|---|---|
| `OfflineFirstLogRepositoryTest` | 46 (43 + 3 nuevos) |
| `LogEntryFirestoreMapperTest` | 5 (4 + 1 nuevo) |
| `LogViewModelTest` | 21 |
| `TranslatorViewModelTest` | 12 |
| `SettingsViewModelTest` | 8 |
| `TravelLogMapperTest` | 4 |

---

## Explicación técnica: cómo funciona Comunidad

### 1. La consulta y el listener en tiempo real
`FirestoreLogRemoteDataSource.observePublicEntries()`:
```kotlin
collection.whereEqualTo(LogFields.IS_PUBLIC, true).observeEntries()
```
- **Filtrado en el servidor:** `whereEqualTo("isPublic", true)` hace que Firestore devuelva **solo** los documentos de la colección `log_entries` con ese campo en `true`. Los privados nunca llegan al dispositivo. Un documento sin el campo, o con un valor que no sea booleano, tampoco aparece.
- **`addSnapshotListener(MetadataChanges.INCLUDE)`** dentro de un `callbackFlow`:
  - Al suscribirse, emite el resultado actual completo.
  - Después emite **un snapshot completo nuevo** cada vez que cambia algo del resultado:
    - se crea un documento público (de cualquier usuario);
    - uno existente se vuelve público (entra al resultado) o privado (sale);
    - se edita un documento público;
    - **cambian los likes:** `setLiked` usa `arrayUnion`/`arrayRemove` sobre el campo `likes`, el documento cambia, y todos los dispositivos que escuchan reciben el nuevo snapshot. Así el contador de "me gusta" se actualiza en vivo en todos los teléfonos.
  - `MetadataChanges.INCLUDE` agrega emisiones cuando solo cambia la metadata. Es el caso de una escritura local que el servidor confirma: `hasPendingWrites` pasa de `true` a `false`, y el mapper traduce eso a `PENDING` → `SYNCED`.
  - **Compensación de latencia:** cuando el propio dispositivo llama a `set(...)`, Firestore aplica la escritura en su caché local y el listener emite **antes** de la respuesta del servidor. Por eso la propia entrada pública aparece en Comunidad casi al instante después de sincronizar, aunque la red sea lenta.
  - Al cancelar la colección (sale la pantalla), `awaitClose { registration.remove() }` da de baja el listener y no queda escuchando ni gastando batería.

### 2. Qué entradas se muestran y en qué orden
- **Servidor vs. cliente:** el **filtro** (`isPublic == true`) corre en el servidor. El **orden** corre en el cliente: `trySend(entries.sortedByDescending { it.timestamp })`. No se usa `orderBy` en el servidor porque combinar `whereEqualTo` con `orderBy` sobre otro campo exige un **índice compuesto** desplegado en Firestore; ordenar en el cliente evita esa configuración. A cambio, la consulta no tiene `limit` y se descarga todo el conjunto público. Si la comunidad crece mucho, convendría agregar el índice, `orderBy("timestamp", DESCENDING)` y `limit` o paginación.
- **Filtro por categoría:** se aplica después, en `LogViewModel` (`entries.filter { it.category == category }`), sobre la lista que ya llegó.
- **Entradas propias y ajenas juntas:** la consulta no filtra por `userId`, así que el feed muestra **todas** las entradas públicas, también las del usuario. En la tarjeta:
  - **Entrada ajena:** solo se puede leer y dar "me gusta".
  - **Entrada propia en Comunidad:** tampoco muestra borrar, cambiar foto ni cambiar visibilidad, porque `LogContent` solo pasa esas acciones con `isMyTrips && isOwnEntry`. Para editarla hay que ir a "Mis viajes".
  - **"Me gusta":** se marca como activo si `currentUserId in entry.likes`.

### 3. Qué datos se ocultan o se transforman
| Dato | En Comunidad |
|---|---|
| **Autor** | Se muestra "Por {authorName}" (`showAuthor = !isMyTrips`). Si viene vacío, "Viajero anónimo". ⚠️ Hoy `authorName` es el **email** del autor (lo completa `OfflineFirstLogRepository.saveEntry`), así que queda expuesto públicamente. Se recomienda cambiarlo antes de publicar. |
| **Badge de sincronización** | **Oculto** (`showSyncStatus = isMyTrips`). El estado de subida de otro usuario no tiene sentido para quien mira, y una entrada que viene de Firestore ya está, por definición, en el servidor. |
| **`localPhotoPath`** | Nunca sale del dispositivo (el mapper no lo escribe) y al leer de Firestore se pone en `null`. Las fotos ajenas se cargan desde `remotePhotoUrl` (Cloudinary) con Coil. |
| **`syncStatus`** | No se guarda en Firestore; se calcula a partir de `hasPendingWrites`. |
| **`likes`** | El upsert nunca lo escribe; solo se modifica con `arrayUnion`/`arrayRemove`, así un guardado no borra los likes de otros. |
| **Documentos con campos rotos** | `fromFirestore` usa valores por defecto (categoría `GENERAL`, `isPublic = false`, etc.), para que un documento mal formado no rompa todo el feed. |

### 4. Qué pasa sin conexión
Hay **dos niveles** de respaldo:
1. **Caché de Firestore (offline persistence):** sin red, el listener **no falla**. Emite desde la caché local lo último que se descargó de la consulta pública. El usuario ve el feed como estaba la última vez que tuvo conexión, entradas ajenas incluidas.
2. **Fallback a Room:** si el listener **falla** (por ejemplo, `PERMISSION_DENIED` por reglas o un error del SDK), `OfflineFirstLogRepository.getCommunityEntries()` hace:
   ```kotlin
   remote.observePublicEntries().catch { emitAll(dao.observePublicEntries()…) }
   ```
   y pasa a `SELECT * FROM travel_logs WHERE isPublic = 1`. Room solo guarda **las entradas del propio usuario**, ya que el merge remoto (`startUserSync`) descarga solo las del `userId`. Por eso, en este modo Comunidad muestra **solo las entradas públicas propias**: es una degradación controlada, no el feed completo.
3. **Si todo falla:** si también falla Room, `LogViewModel` muestra el estado de error con "Reintentar".

## Git
No se hizo ningún commit, merge ni push. Todos los cambios de las 3 etapas quedan en el working directory para que los revises.
