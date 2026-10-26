# Subida a demanda — Fase 3: Verificación end-to-end, build completo y documentación

## Objetivo cumplido de la fase
1. Se verificó el **ciclo de vida completo** de una entrada con un test end-to-end nuevo en `:core:data`:
   - se crea privada con foto local y no sale del dispositivo;
   - se comparte, se sube la foto a Cloudinary y se escribe el documento público en Firestore;
   - aparece en Comunidad.
2. `./gradlew clean assembleDebug testDebugUnitTest` pasa en los **11 módulos**: **161 tests, 0 fallos, 0 errores**, y se genera el APK.
3. Este reporte resume la solución completa y el estado de los tests.

## Archivos creados o modificados en esta fase
| Archivo | Cambio |
|---|---|
| `core/data/src/test/java/com/wandervox/core/data/repository/OfflineFirstLogRepositoryTest.kt` | Test nuevo `lifecycle_privateWithPhoto_isLocalUntilShared_thenUploadedAndInCommunity`, ubicado antes de la sección `// --- reads ---`. |

No se cambió código de producción en esta fase.

## Verificación del ciclo de vida

### Test end-to-end (`lifecycle_privateWithPhoto_isLocalUntilShared_thenUploadedAndInCommunity`)
Usa un "Firestore" simulado: los upserts se guardan en un store en memoria, y la consulta pública es un filtro en vivo `isPublic == true` sobre ese store. Así reproduce `whereEqualTo` más el snapshot listener.

| Paso | Qué se verifica |
|---|---|
| **1. Crear la entrada** (privada, `localPhotoPath = /files/log_photos/e1.jpg`) | `uploader.uploads` vacío (**Cloudinary no recibe nada**), `remote.operations` vacío (**Firestore no recibe nada**) y el feed de Comunidad vacío. |
| **2. Compartir desde la tarjeta** (`isPublic = true`) | Cloudinary recibe **exactamente** `/files/log_photos/e1.jpg`. El documento en el store tiene `isPublic = true` y `remotePhotoUrl` = la URL segura que devolvió Cloudinary. |
| **3. Comunidad** | El feed emite la entrada con `remotePhotoUrl` de Cloudinary, que es la URL desde donde otros dispositivos cargan la foto. En Room la fila queda `SYNCED` y **se conserva la copia local** de la foto. |

### Otros tests que cubren el resto del ciclo
- `saveEntry_private_staysLocal_withoutAnyNetworkCall`, `editingANeverSharedEntry_staysLocal` y `syncPending_skipsPrivateLocalEntries`: una entrada privada nunca toca la red, ni al editarla ni al iniciar sesión.
- `makingPrivate_deletesRemoteDocument_keepsRowAndPhotoUrl` y `communityFeed_showsEntryOnceShared_andDropsItWhenPrivate`: al volver a privada se borra el documento y la entrada sale de Comunidad.
- `sharingAgain_reusesUploadedPhoto`: compartir de nuevo no vuelve a subir la foto.
- `unpublishOffline_marksFailed_thenSyncPendingFinishesIt` y `LogSyncCoordinatorTest`: los fallos sin conexión se reintentan al iniciar sesión.
- `LogViewModelTest` (sección Visibility): la UI manda el valor destino, y repetirlo no guarda nada.
- `LogEntryFirestoreMapperTest`: el campo se escribe exactamente como `"isPublic"`, que es el que filtra la consulta.

### Lo que falta probar en un dispositivo
Los tests de JVM usan dobles de Cloudinary y Firestore. Antes del merge conviene una prueba manual:
1. **Crear una entrada desde el Traductor y agregarle una foto.** En **Firebase Console**, en `log_entries`, **no** debe aparecer ningún documento nuevo. En el dashboard de Cloudinary no debe haber ninguna imagen nueva.
2. **Tocar 📤 → "Compartir".** La tarjeta debe mostrar el spinner y después 🌐 con el badge "Sincronizada". En la consola debe aparecer el documento con `isPublic: true` y `remotePhotoUrl` apuntando a `res.cloudinary.com`.
3. **Comprobar Comunidad.** La entrada debe aparecer en la pestaña Comunidad, también para un segundo usuario en otro dispositivo, con la foto cargada desde Cloudinary.
4. **Tocar 🌐.** El documento debe desaparecer de la consola y la entrada debe salir de Comunidad.
5. **Repetir el paso 2 en modo avión.** La entrada debe quedar con el badge "Error al sincronizar". Al volver la conexión y reiniciar sesión, debe terminar de subirse.

Las **reglas de seguridad de Firestore** no están en el repositorio. Deben permitir `delete` al dueño del documento (`resource.data.userId == request.auth.uid`), porque ahora "hacer privada" borra el documento.

## Resumen de la solución completa (Fases 1 a 3)
| Fase | Módulo | Resultado |
|---|---|---|
| 1 | `:features:settings` | Se eliminó la sección "Conectividad" ("Modo offline forzado" y "Sincronización de Bitácora") junto con su estado y lógica en `SettingsViewModel`. Ajustes depende solo de `AuthRepository`. |
| 2 | `:core:data` | `saveEntry` solo usa la red si la entrada es pública o si hay que despublicar un documento. `syncEntry` decide entre subir (foto + documento), despublicar (`remote.delete`) o borrar (tombstone). `SYNCED` pasa a significar "el servidor está como debe". |
| 2 | `:features:log` | Botón 📤 / 🌐 / spinner en las tarjetas propias, diálogo de confirmación al compartir, evento `OnSetVisibility` con el valor destino, y badge de sincronización oculto en las entradas solo locales. |
| 3 | `:core:data` (solo tests) | Test end-to-end del ciclo completo y build limpio de los 11 módulos. |

**No se modificó `:core:domain`.** Se usaron solo `LogRepository.saveEntry`, `TravelLogEntry.isPublic` y `SyncStatus`, que ya existían.

### Limitaciones (detalladas en el reporte de la Fase 2)
1. Las entradas privadas subidas con la política anterior siguen en Firestore con `isPublic: false`. No aparecen en Comunidad, pero no se migraron.
2. Las entradas privadas no tienen copia en la nube: se pierden si se desinstala la app.
3. Al despublicar se pierden los likes, y la foto queda huérfana en Cloudinary.
4. Ya no hay un botón para reintentar a mano. Se reintenta al iniciar sesión o volviendo a compartir.
5. ⚠️ Sigue pendiente lo de tareas anteriores: el `authorName` público es el **email** del usuario.

## Resultados de las pruebas ejecutadas
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat clean assembleDebug testDebugUnitTest
```
- Resultado: **BUILD SUCCESSFUL in 1m 17s** (419 tareas accionables: 383 ejecutadas; build limpio).
- APK generado: `app/build/outputs/apk/debug/app-debug.apk`.
- **161 tests en 23 suites, 0 fallos, 0 errores:**

| Módulo | Suite | Tests |
|---|---|---|
| `:app` | `ExampleUnitTest` | 1 |
| `:core:data` | `OfflineFirstLogRepositoryTest` | 53 |
| `:core:data` | `LogSyncCoordinatorTest` | 4 |
| `:core:data` | `LocationProviderSelectionTest` | 6 |
| `:core:data` | `ExampleUnitTest` | 1 |
| `:core:database` | `ConvertersTest` | 8 |
| `:core:database` | `TravelLogMapperTest` | 4 |
| `:core:database` | `ExampleUnitTest` | 1 |
| `:core:network` | `CloudinaryImageUploaderTest` | 5 |
| `:core:network` | `LogEntryFirestoreMapperTest` | 5 |
| `:core:network` | `ExampleUnitTest` | 1 |
| `:core:ui` | `ColorContrastTest` | 2 |
| `:core:ui` | `ExampleUnitTest` | 1 |
| `:features:auth` | `AuthViewModelTest` | 9 |
| `:features:log` | `LogViewModelTest` | 22 |
| `:features:log` | `LogDetailViewModelTest` | 7 |
| `:features:log` | `ExampleUnitTest` | 1 |
| `:features:map` | `MapViewModelTest` | 6 |
| `:features:map` | `ExampleUnitTest` | 1 |
| `:features:settings` | `SettingsViewModelTest` | 4 |
| `:features:translator` | `TranslatorViewModelTest` | 12 |
| `:features:translator` | `ShakeDetectorTest` | 6 |
| `:features:translator` | `ExampleUnitTest` | 1 |

`:core:domain` no tiene tests propios: es JVM puro y se prueba a través de sus consumidores.

## Git
No se hizo ningún commit, merge ni push en ninguna fase. Todos los cambios quedan en el working directory para que los revises.
