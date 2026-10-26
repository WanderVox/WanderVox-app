# Entradas públicas — Etapa 1: Switch "Compartir con la comunidad" en `SaveToLogSheet` (`:features:translator`)

## Objetivo cumplido de la etapa
Al guardar una traducción en la Bitácora desde el Traductor, el usuario ahora puede decidir si la entrada es **pública**. La hoja de guardado tiene un switch nuevo, "Compartir con la comunidad", y su valor llega hasta el `TravelLogEntry` que se persiste (`isPublic`). Así, la pestaña Comunidad puede recibir entradas por primera vez.

## Archivos creados o modificados
| Archivo | Componentes afectados |
|---|---|
| `features/translator/src/main/java/com/wandervox/features/translator/presentation/components/SaveToLogSheet.kt` | • `SaveToLogSheet` y `SaveToLogForm`: el callback pasa a ser `onConfirm: (category, notes, isPublic: Boolean) -> Unit`.<br>• Estado nuevo en el formulario: `var isPublic by rememberSaveable { mutableStateOf(false) }`.<br>• Composable privado nuevo `ShareWithCommunityRow`, ubicado entre las notas y el botón Guardar.<br>• Se actualizó el `@Preview`. |
| `features/translator/src/main/java/com/wandervox/features/translator/presentation/TranslatorUiEvent.kt` | `OnSaveToLog` suma `val isPublic: Boolean = false`. |
| `features/translator/src/main/java/com/wandervox/features/translator/presentation/TranslatorViewModel.kt` | `saveToLog(...)` arma el `TravelLogEntry` con `isPublic = event.isPublic`. |
| `features/translator/src/main/java/com/wandervox/features/translator/presentation/TranslatorScreen.kt` | • `pendingSave` pasa de `Pair<LogCategory, String>` a `Triple<LogCategory, String, Boolean>`.<br>• `onConfirmSave` en `TranslatorScreen` y en `TranslatorContent` recibe `isPublic`.<br>• El callback de la hoja reenvía el valor después de la animación de cierre.<br>• Se actualizó el preview. |
| `features/translator/src/main/res/values/strings.xml` | 3 strings nuevos: `translator_share_label` ("Compartir con la comunidad"), `translator_share_off_hint` ("Solo vos vas a ver esta entrada") y `translator_share_on_hint` ("Otros viajeros la van a ver en Comunidad"). |
| `features/translator/src/test/java/com/wandervox/features/translator/presentation/TranslatorViewModelTest.kt` | 2 tests nuevos. |

No se tocaron `:core:domain` (`TravelLogEntry.isPublic` ya existía), `:core:data` ni `:core:network`. El módulo `:features:translator` sigue dependiendo solo del contrato `LogRepository`, sin dependencias de datos ni de red.

## Manejo de estado y decisiones de diseño
- **Privada por defecto (`isPublic = false`):** compartir con la comunidad siempre es una decisión explícita, para no publicar sin querer el texto, las notas o la ubicación de alguien. El mismo valor por defecto está en el formulario, en el evento y en el dominio.
- **Estado efímero del formulario con `rememberSaveable`:** igual que la categoría y las notas, el switch sobrevive a la rotación mientras la hoja está abierta. El ViewModel no lo guarda porque es un borrador de UI; recién se vuelve dato cuando el usuario confirma (UDF: el evento `OnSaveToLog` lleva el valor elegido).
- **Toda la fila es el control:** `Modifier.toggleable(role = Role.Switch)` va sobre la fila entera y el `Switch` tiene `onCheckedChange = null`. Así el área táctil incluye el texto y TalkBack anuncia un solo control "switch" con su etiqueta, en lugar de dos elementos sueltos.
- **El subtítulo cambia según el valor** ("Solo vos vas a ver esta entrada" / "Otros viajeros la van a ver en Comunidad"), para que se entienda qué pasa antes de guardar.
- **El permiso de ubicación no pierde el valor:** si al confirmar hace falta pedir la ubicación, el guardado queda en espera en `pendingSave` hasta la respuesta del diálogo del sistema. Por eso `pendingSave` ahora guarda también `isPublic`. Se usa `Triple` porque es `Serializable`, igual que el `Pair` que había, y se puede guardar con `rememberSaveable`.
- **`isPublic` con valor por defecto en `OnSaveToLog`:** las llamadas que ya existían siguen compilando y mantienen el comportamiento anterior (privada).
- **El resto del camino ya funcionaba:** se revisó y `OfflineFirstLogRepository` copia `isPublic` a Room y `LogEntryFirestoreMapper` lo escribe en el campo `isPublic` de Firestore. Esto se va a verificar a fondo en la Etapa 3.

### ⚠️ Riesgo de privacidad para revisar
`OfflineFirstLogRepository.saveEntry` completa `authorName = user.email`. Ahora que hay entradas públicas, **el email del autor se va a ver en la pestaña Comunidad** ("Por usuario@mail.com") para todos los usuarios. No se cambió en esta etapa porque es parte de `:core:data` y está fuera del alcance pedido. Se recomienda decidir antes del merge si se usa la parte del email antes de la `@`, un nombre visible del perfil o "Viajero anónimo".

## Resultados de las pruebas y comandos ejecutados
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
.\gradlew.bat :features:translator:testDebugUnitTest :features:translator:assembleDebug
```
- Resultado: **BUILD SUCCESSFUL in 11s** (48 tareas accionables).

| Suite | Tests | Fallos | Errores |
|---|---|---|---|
| `TranslatorViewModelTest` | 12 (10 existentes + 2 nuevos) | 0 | 0 |
| `ShakeDetectorTest` | 6 | 0 | 0 |
| `ExampleUnitTest` | 1 | 0 | 0 |

Tests nuevos:
1. `save is private unless the user shares it`: sin indicar nada, la entrada se guarda con `isPublic = false`.
2. `shared save marks the entry as public`: con `isPublic = true`, se guarda pública y en estado `PENDING`, lista para subir a Firestore.

No se probó en un dispositivo ni en un emulador.

## Git
No se hizo ningún commit, merge ni push. Los cambios quedan en el working directory para que los revises.
