# Subida a demanda — Fase 2: Subida a la nube solo al compartir (`:core:data` y `:features:log`)

## Objetivo cumplido de la fase
- **Entrada privada (el valor por defecto):** queda **solo en el dispositivo**. No se sube la foto a Cloudinary, no se escribe nada en Firestore y no se hace ninguna llamada de red.
- **Al compartir** (botón 📤 en la tarjeta + confirmación): recién ahí corre el pipeline completo. Primero sube la foto local a Cloudinary (si hay una y todavía no se subió) y después escribe el documento en Firestore con `isPublic: true`. La entrada aparece en Comunidad.
- **Al volver a privada** (tocar 🌐 en una entrada compartida): **se borra el documento de Firestore**, así que la entrada sale de Comunidad y vuelve a vivir solo en el dispositivo. La fila de Room no se pierde.

## Archivos creados o modificados

### `:core:data`
| Archivo | Líneas clave |
|---|---|
| `core/data/src/main/java/com/wandervox/core/data/repository/OfflineFirstLogRepository.kt` | • **KDoc de la clase:** documenta la política de subida a demanda y el nuevo significado de `SYNCED`.<br>• **L77–86, `saveEntry`:** `needsRemote = entry.isPublic \|\| previous.mayHaveRemoteDocument()`. Si no hace falta red, la fila se guarda como `SYNCED` y no se lanza la sincronización.<br>• **L94, `mayHaveRemoteDocument()`:** devuelve `true` si la fila era pública o tenía trabajo pendiente con el servidor.<br>• **L218–224, `syncEntry`:** `when` con tres caminos: `PENDING_DELETE` → `deleteRemotely`, pública → `uploadAndRecord` (foto + documento) y privada → `unpublish`.<br>• **L278, `unpublish()` (nuevo):** `remote.delete(id)` y después `updateSyncResult` con compare-and-set sobre `updatedAt`. Queda `SYNCED` si sale bien y `FAILED` si falla. Conserva `remotePhotoUrl`.<br>• Se agregó el import de `TravelLogEntity`. |
| `core/data/src/test/.../repository/Fakes.kt` | `FakeLogRemoteDataSource.onDeleted`: hook para simular que el documento sale del store. |
| `core/data/src/test/.../repository/OfflineFirstLogRepositoryTest.kt` | • `translatorEntry` ahora es **pública**, para que los tests de subida que ya existían sigan probando el pipeline. También hay un `privateEntry` nuevo.<br>• La sección `// --- upload on demand ---` reemplaza la sección `public entries` de la tarea anterior y tiene 9 tests (ver abajo). |
| `core/data/src/test/.../repository/LogSyncCoordinatorTest.kt` | La entrada con subida `FAILED` del test de reintento ahora es pública. Con la nueva política, una entrada privada no se sube: se despublica. |

### `:features:log`
| Archivo | Líneas clave |
|---|---|
| `.../presentation/LogUiEvent.kt` | `OnToggleVisibility(entryId)` se reemplazó por `OnSetVisibility(entryId, isPublic)`. |
| `.../presentation/LogViewModel.kt` | • **L117, `setVisibility(entryId, isPublic)`:** recibe el valor destino. Si ya es ese valor, no hace nada; si no, guarda con `saveEntry(entry.copy(isPublic, updatedAt))`. El repositorio decide el `syncStatus` y corre la subida.<br>• Se eliminó el guard `togglingVisibilityIds`. |
| `.../presentation/LogScreen.kt` | Estado `pendingShareId` (`rememberSaveable`). `onShareRequest` abre el diálogo; `onShareConfirm` envía `OnSetVisibility(id, true)`; `onMakePrivate` envía `OnSetVisibility(id, false)` sin confirmación. |
| `.../presentation/LogContent.kt` | Parámetros nuevos: `pendingShareId`, `onShareRequest`, `onMakePrivate`, `onShareConfirm` y `onShareDismiss`. El botón de la tarjeta llama a uno u otro según `entry.isPublic`. Muestra `ShareEntryDialog`. Se actualizó el preview. |
| `.../presentation/components/LogEntryCard.kt` | • **L126:** el badge de sincronización **se oculta** en las entradas privadas que no tienen nada pendiente con el servidor.<br>• **L176–182:** `ShareButton` reemplaza al `IconToggleButton` anterior.<br>• **L218, `ShareButton`:** 📤 `Icons.Outlined.Share` ("Compartir con la comunidad") cuando es privada; 🌐 `Icons.Filled.Public` en color primario ("Hacer privada") cuando es pública; un `CircularProgressIndicator` de 20 dp, en el mismo espacio de 48 dp, mientras `syncStatus == PENDING` (subida o despublicación en curso). |
| `.../presentation/components/ShareEntryDialog.kt` **(nuevo)** | `AlertDialog` "¿Compartir con la comunidad?" que avisa qué datos salen del dispositivo (texto, traducción, notas, ubicación y foto). |
| `main/res/values/strings.xml` | • Strings nuevos: `log_share_in_progress`, `log_share_dialog_title`, `log_share_dialog_text`, `log_share_dialog_confirm` y `log_share_dialog_cancel`.<br>• `log_visibility_private_detail` ahora dice "Guardada solo en este dispositivo". Se ve en el detalle. |
| `test/.../presentation/LogViewModelTest.kt` | Los tests de visibilidad se reescribieron para `OnSetVisibility` (5 tests). Se agregó `saveCount` al fake. |

No se tocó `:core:domain`: `LogRepository`, `TravelLogEntry` y `SyncStatus` siguen igual. La "publicación" usa el contrato `saveEntry` que ya existía, sin agregar `publishToCommunity()` a la interfaz.

## Decisiones técnicas de diseño y flujo de datos

### Qué significa `SyncStatus` ahora
Como el dominio no se cambia, no se puede agregar un estado `LOCAL_ONLY`. Por eso `SyncStatus` pasa a significar **"el servidor está como debería estar para esta entrada"**:

| Entrada | Estado deseado en el servidor | `syncStatus` |
|---|---|---|
| Privada, nunca compartida | Ningún documento | `SYNCED` desde el primer guardado, sin red |
| Pública, subiendo | Documento con `isPublic: true` | `PENDING` → `SYNCED` (o `FAILED`) |
| Vuelta a privada, borrando el documento | Ningún documento | `PENDING` → `SYNCED` (o `FAILED`) |
| Borrada | Ningún documento y ninguna fila | `PENDING_DELETE` (tombstone, sin cambios) |

Ventajas de este enfoque:
- `syncPending()` (que llama `LogSyncCoordinator` al iniciar sesión) **ignora sola** las entradas privadas locales, porque son `SYNCED`.
- Las subidas o despublicaciones que fallaron (`FAILED`) se reintentan sin código extra.
- Sigue funcionando la lógica que ya existía para ediciones durante una sincronización (`superseded`) y el merge remoto, que no pisa filas que no están `SYNCED`.

### Flujo al compartir
1. 📤 en la tarjeta abre `ShareEntryDialog`.
2. "Compartir" envía `OnSetVisibility(id, true)`.
3. `LogViewModel.setVisibility` llama a `saveEntry(isPublic = true)`.
4. El repositorio guarda la fila en Room como `PENDING` y la tarjeta muestra el **spinner**.
5. En `appScope` corre `uploadAndRecord`: sube a Cloudinary (si hay `localPhotoPath` y no hay `remotePhotoUrl`) y después hace `Firestore.set(merge)` con `isPublic: true` y la URL segura.
6. Room pasa a `SYNCED` con `remotePhotoUrl`.
7. La tarjeta muestra 🌐 y el badge "Sincronizada", y el listener de `whereEqualTo("isPublic", true)` la suma a Comunidad.

### Flujo al volver a privada
1. 🌐 envía `OnSetVisibility(id, false)`, sin diálogo, porque es la acción "segura".
2. `saveEntry` ve que la fila anterior era pública, así que `needsRemote = true`, y la guarda como `PENDING`.
3. `unpublish` llama a `remote.delete(id)` y la entrada sale de Comunidad.
4. Room pasa a `SYNCED`, se oculta el badge y la tarjeta vuelve a 📤.
5. Sin conexión, queda `FAILED` (badge "Error al sincronizar") y `syncPending()` lo termina más tarde.

### Otras decisiones
- **Borrar el documento en lugar de `isPublic: false`:** cumple con que lo privado quede "estrictamente local". Firestore no guarda una copia privada que nadie pidió. La operación es idempotente: borrar un documento que no existe no da error.
- **Se conserva `remotePhotoUrl` al despublicar:** si se vuelve a compartir, no se sube otra vez la misma imagen (lo cubre el test `sharingAgain_reusesUploadedPhoto`). La foto sigue en Cloudinary porque el cliente no tiene API para borrarla. Ver limitaciones.
- **Confirmación solo al compartir:** compartir saca del dispositivo datos sensibles (ubicación exacta, foto, notas), así que se pregunta antes. Volver a privada no expone nada, así que es inmediato.
- **Valor destino en lugar de toggle (`OnSetVisibility`):** el evento dice qué valor se quiere, no "invertir". Un doble toque o un evento repetido no hace nada (test `repeating the current visibility saves nothing`), y por eso ya no hace falta el `Set` de ids en curso.
- **Spinner con `syncStatus == PENDING`:** en una entrada propia, `PENDING` ahora solo aparece mientras hay una subida o despublicación en curso. Se usa el mismo dato que ya expone Room, sin estado extra en el ViewModel (UDF).
- **El switch del Traductor sigue funcionando:** si en `SaveToLogSheet` se activa "Compartir con la comunidad", el guardado ya es público y se sube en el momento. Es coherente con la regla ("se sube cuando el usuario decide compartir"), aunque ese camino no muestra el diálogo de confirmación, porque el switch con su texto explicativo cumple ese rol.

### Limitaciones conocidas (para revisar)
1. **Entradas de la política anterior:** las entradas privadas que se subieron con la política "subir todo" siguen teniendo un documento en Firestore con `isPublic: false`. No aparecen en Comunidad, pero no se migraron ni se borraron. Para limpiarlas: compartirlas y volverlas a privadas, o hacer una migración puntual.
2. **Reinstalar o cambiar de dispositivo:** una entrada privada ya no tiene copia en la nube, así que **se pierde si se desinstala la app**. Es consecuencia directa de "estrictamente local". `startUserSync` solo recupera las públicas.
3. **Likes al despublicar:** al borrar el documento se pierden los likes que tenía. Si se vuelve a compartir, arranca en 0.
4. **Foto huérfana en Cloudinary:** queda subida aunque la entrada vuelva a privada.
5. **Borrar una entrada privada:** sigue pasando por el tombstone y `remote.delete`, que es idempotente. Hace una llamada de red innecesaria para entradas que nunca se compartieron, pero es seguro para las de la política anterior, que sí tienen documento. Se dejó así a propósito.
6. **Reintento manual de una subida fallida:** no hay un botón "reintentar" en la tarjeta. Se reintenta al iniciar sesión (`LogSyncCoordinator`) o volviendo a compartir.

## Resultados de las pruebas ejecutadas
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat :core:data:testDebugUnitTest :features:log:testDebugUnitTest :features:log:assembleDebug
```
- **Primera corrida de `:core:data`:** falló `LogSyncCoordinatorTest > signIn_pullsUserEntries_andRetriesPendingWork`. Era un fallo **esperado**: ese test reintentaba una entrada privada `FAILED` y esperaba un upsert, pero con la nueva política esa entrada se despublica. Se corrigió el fixture para que sea pública.
- **Corrida final:** **BUILD SUCCESSFUL in 14s** (96 tareas accionables).

| Suite | Tests | Fallos |
|---|---|---|
| `OfflineFirstLogRepositoryTest` | 52 | 0 |
| `LogSyncCoordinatorTest` | 4 | 0 |
| `LocationProviderSelectionTest` | 6 | 0 |
| `LogViewModelTest` | 22 | 0 |
| `LogDetailViewModelTest` | 7 | 0 |
| `ExampleUnitTest` (×2) | 2 | 0 |

Tests nuevos o reescritos en `OfflineFirstLogRepositoryTest` (sección `upload on demand`):
1. `saveEntry_private_staysLocal_withoutAnyNetworkCall`: una entrada privada con foto no genera ninguna operación remota ni subida, y queda `SYNCED`.
2. `editingANeverSharedEntry_staysLocal`
3. `syncPending_skipsPrivateLocalEntries`
4. `sharing_uploadsPhotoThenWritesPublicDocument`: al compartir sube 1 foto y escribe 1 documento con `isPublic = true` y la URL de Cloudinary.
5. `makingPrivate_deletesRemoteDocument_keepsRowAndPhotoUrl`: las operaciones son `[upsert, delete]`, la fila queda privada y `SYNCED`, y la URL se conserva.
6. `sharingAgain_reusesUploadedPhoto`: se sube 1 sola foto y las operaciones son `[upsert, delete, upsert]`.
7. `unpublishOffline_marksFailed_thenSyncPendingFinishesIt`
8. `visibility_isWrittenToTheUploadedDocument`
9. `communityFeed_showsEntryOnceShared_andDropsItWhenPrivate`: con un "Firestore" simulado, la entrada no aparece mientras es privada, aparece al compartir y desaparece al volver a privada.

En `LogViewModelTest`: `sharing a private entry saves it as public`, `making an entry private drops it from the community`, `repeating the current visibility saves nothing`, `failed visibility change reports an action error` y `visibility change of a missing entry reports an action error`.

El build completo (`:app` y los 11 módulos) se corre en la Fase 3. No se probó en un dispositivo ni en un emulador.

## Git
No se hizo ningún commit, merge ni push. Los cambios quedan en el working directory para que los revises.
