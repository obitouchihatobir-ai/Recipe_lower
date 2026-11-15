# CLAUDE.md — Recetario (Recipe_lower)

## Qué es
Una app **personal de Android** para un solo usuario. Sirve para apuntar recetas propias de comidas, bebidas y postres, cocinarlas con un modo paso a paso, hacer la lista de compra, recibir sugerencias del día (según la hora, la estación y el clima) y ver maridajes (con reglas propias y, opcionalmente, con IA de Claude).

El diseño aprobado está en `docs/superpowers/specs/2026-11-13-recetario-design.md`, con los bocetos HTML en `docs/superpowers/specs/2026-11-13-recetario-bocetos/`. **Léelo antes de tocar nada.** La app se entrega en 3 etapas (§5 del diseño).

## Estado actual
- Etapa 1 en curso según `docs/superpowers/plans/2026-11-13-recetario-etapa-1.md` (léete también su «Enmienda 1»).
- Ya existe el proyecto Gradle: `:app` (Android) y el build incluido `logic/` (Kotlin puro), con integración continua.
- Firma y publicación del APK listas (flujos `release.yml` y `create-signing-key.yml`); solo funcionan cuando el código está en `main`.

## Estructura
```
app/                      Módulo Android :app (Compose, Room, DataStore…), paquete app.recetario
  src/main/java/app/recetario/   RecetarioApp (con AppContainer) y MainActivity; paquetes por función:
                                 navigation/ (Routes, AppNavHost, BottomBar), home/, recipes/{list,editor,detail,data},
                                 cooking/, shopping/, settings/, backup/, pairing/, core/ui/{theme,components,icons,motion}
  src/test/                      Pruebas en la JVM con Robolectric (sdk 35, robolectric.properties)
logic/                    Build incluido, Kotlin/JVM sin Android: lógica pura y sus pruebas
                          (artefacto app.recetario:logic, que :app consume)
gradle/libs.versions.toml Catálogo de versiones, compartido por la raíz y por logic/
.github/workflows/        ci.yml (integración continua), release.yml («Publicar APK»),
                          create-signing-key.yml («Crear llave de firma», se lanza a mano una vez)
app/version.properties    versionName=X.Y.Z; de ahí salen versionName y versionCode
scripts/                  setup-android-sdk.sh (instala el SDK en $HOME/android-sdk)
scripts/signing/          create-keystore.sh, decrypt-keystore.sh y sus pruebas (test-signing-scripts.sh)
signing/release.p12.enc   Llave de firma CIFRADA (la crea el flujo; el único archivo de llave permitido)
docs/instalar.md          Guía de instalación para el usuario (secreto, llave, descarga e instalación)
docs/superpowers/specs/   Diseños aprobados y sus bocetos
docs/superpowers/plans/   Planes de implementación
.superpowers/             Bocetos temporales de las sesiones (ignorado por git)
```

## Navegación y pantallas
- Rutas tipadas `@Serializable` en `navigation/Routes.kt`. `AppNavHost(container)` registra los destinos (cada tarea añade los suyos) dentro de un `SharedTransitionLayout`: la foto de una tarjeta viaja al detalle con la clave `photo-$recipeId` (solo cuando la navegación entra o sale del detalle).
- Las pantallas se parten en `XxxRoute` (ViewModel y contenedor) y `XxxContent` (sin estado), que es lo que usan las capturas.
- Iconos propios de trazo en `core/ui/icons/AppIcons.kt` (no se usa material-icons).

## Lista de compra
Lógica pura en `logic/.../shopping/` (`ShoppingModels`, `SectionClassifier`, `mergeInto`, `asShareText`); Room (`shopping_items`, `ShoppingDao`), `ShoppingRepository` y pantalla en `app/.../shopping/`. `addFromRecipe` escala, clasifica y funde en una transacción y devuelve cuántos ingredientes añadió. Borrar y «Vaciar marcados» ofrecen «Deshacer» (`restore`). La taza viene de `cupMl: Flow<Int>` (sale de `SettingsRepository.cupMl`, 240 por defecto, y se cambia en Ajustes › Unidades). Añadir a mano usa un panel inferior con `BackHandler`. Observación: un primer intento con `AlertDialog` y campos de texto colgó una prueba de Robolectric (sin causa investigada). Cada fila ofrece pulsación larga para cambiar de sección y acciones de accesibilidad «Borrar» y «Cambiar de sección»; borrar devuelve la fila leída en la misma transacción para restaurarla tal cual. Si cambias una tabla de Room y no puedes compilar en local, el paso «Comprobar que el esquema…» de CI imprime el JSON generado: cópialo a `app/schemas/…/1.json` (sin la línea final `##[error]`).

## Detalle de receta
`recipes/detail/`: `DetailRoute` (ViewModel) y `DetailContent` (sin estado, lo usan las capturas), `DetailParts.kt` (botones redondos, corazón animado, tarjeta de sabor, fila de ingrediente, cantidad que destella 300 ms, botón discontinuo, aviso). `formatRecipeAsText` (texto para compartir) vive en `logic/.../recipes/detail/`. Porciones y casillas van en `SavedStateHandle` (sobreviven al giro, no se guardan en disco). La cabecera con la clave `photo-$id` se compone siempre, también mientras carga (sin marcador 🍽 de más); la ruta conserva el último cuadro al borrar. Tocar una cantidad de masa o volumen (con `Role.Button`, «Ver equivalencias») abre `ConversionsPanel` (`BottomPanel`) con `equivalencesFor` (lógica pura en `logic/.../converter/Equivalences.kt`); las de conteo o «al gusto» no son pulsables. «▶ Modo cocina» abre `Cooking(recipeId, servings)` con las porciones elegidas.

## Modo cocina y temporizadores
- `cooking/` en `:app`: `CookingRoute`/`CookingContent` (lista, opción B del boceto) y `CookingParts.kt` (cabecera fija fuera de la lista con «Paso 2 de 4» como región viva, ingredientes plegables, paso actual expandido, botón del temporizador, «Hecho ✓», «¡Listo!»). Nada se atenúa con transparencia (contraste ≥ 4,5:1, lo comprueba `CookingContrastTest` con píxeles): los pasos hechos llevan el círculo de acento opaco con ✓ y texto `muted`; los ingredientes tachados, `muted` y tachados. Con TalkBack (exploración táctil), tras «Hecho ✓» el foco pasa a la tarjeta del paso nuevo (o a «¡Listo!»). La cuenta atrás de la pantalla (`rememberTimerNow`) solo lee el reloj en STARTED. El permiso de notificaciones se pide una sola vez (`SettingsRepository.notificationPermissionAsked`). `CookingViewModel` lee `recipeId` y `servings` del `SavedStateHandle` (argumentos de la ruta) y guarda ahí el paso actual, los pasos hechos (índices) y los ingredientes tachados. `KeepScreenOn` pone `FLAG_KEEP_SCREEN_ON` mientras la pantalla está compuesta.
- En `logic/.../cooking/`: `TimerManager` y `CookingTimer` (reloj inyectado, `SystemClock.elapsedRealtime` en la app), `launchTimerTicker` (llama a `tick` cada 250 ms solo mientras alguno corre), textos (`timerLabel` «Paso 2: Sofríe la cebolla y el ajo…», `formatTimerClock`, líneas de la notificación) y `nextPendingStep`.
- Un solo `TimerManager` en `AppContainer` (con su bucle); los temporizadores usan el id del paso, así sobreviven al giro y a salir y volver. Un terminado sigue en la lista («✓ ¡Tiempo!») hasta que se apaga en la app.
- `TimerService` (primer plano, `specialUse` «cooking_timer»): lo arranca la pantalla al tocar un temporizador; notificación en curso (canal «Temporizadores», baja) con cronómetro hacia el más próximo, actualizada cada segundo; aviso «⏱ ¡Tiempo! — …» (canal «Temporizador terminado», alta, alarma del sistema y vibración, insistente, se retira a los 10 min: `TimerNotifications.ALARM_MS`) con el id del temporizador como etiqueta; tocarlo abre la app y apaga ese temporizador (`MainActivity` lee `EXTRA_TIMER_ID`) y descartarlo también (`TimerDismissReceiver`); wake lock parcial mientras alguno corre (sin él, con la pantalla apagada la CPU se duerme). Si las notificaciones no pueden sonar (permiso denegado), el servicio hace sonar la alarma él mismo (un `MediaPlayer` en bucle con uso de alarma, válido desde Android 8) hasta que se apaga o pasan los mismos 10 minutos. Se para cuando no queda ninguno activo. Decide los avisos a partir de `timers` (no de `finishedEvents`) para no pararse antes de avisar.
- Solo en un teléfono se puede comprobar: el servicio con la app en segundo plano, el sonido con la pantalla apagada y el permiso de notificaciones (Robolectric solo prueba la lógica, las notificaciones y el ciclo del servicio).

## Conversor
`converter/ConverterScreen.kt` (`ConverterRoute`/`ConverterContent`, estado en `rememberSaveable`; acción «Conversor de medidas» en la barra de Recetas, destino `Converter`). Selectores De/A con `FlowRow` (solo masa y volumen; un `LazyRow` dejaría chips sin componer en las pruebas), sugerencias de `Densities.keywords`, «⇅» intercambia, usa `cupMl` de ajustes.

## Ajustes y tema
`SettingsRepository` (DataStore `ajustes`, creado en `AppContainer`; las pruebas pasan uno temporal) guarda el tema (Automático, tema claro de día, horas de noche), la taza (240 o 250 ml, que alimenta a `ShoppingRepository`) y `BackupStatus` para las copias. `MainActivity` no pinta nada hasta leer los ajustes (las pruebas con `MainActivity` esperan con `waitFor`) y obtiene el tema con `rememberResolvedTheme` (se reevalúa cada minuto). Un archivo de ajustes corrupto se sustituye por uno vacío (`createSettingsDataStore`, y los flujos capturan `IOException`). `ThemeRevealHost` rodea a `RecetarioTheme` y le pasa el tema con el que componer: antes de cambiarlo toma una imagen real del contenido (`toImageBitmap`) y la dibuja encima mientras se abre el círculo; Apariencia avisa del punto tocado con `LocalThemeRevealOrigin` y el cambio se abre en círculo; sin aviso o con movimiento reducido es un fundido. Las horas de noche no pueden ser iguales. Los fallos de guardado en Categorías y Etiquetas salen en un aviso (`reportFailure`). Apariencia es una pantalla completa (Ajustes y el icono de Inicio); las horas de noche se editan con «−/+» de media hora. Categorías y Etiquetas usan `BottomPanel` (panel inferior con `BackHandler`, sin `AlertDialog`); borrar una categoría pregunta a cuál pasan sus recetas.

## Copias de seguridad (exportar e importar)
- Formato: zip con `backup.json` + `images/<archivo>`. `BackupFile` y sus DTO viven en `logic/.../backup/BackupModel.kt` (`schemaVersion` 1, fechas ISO-8601, horas `HH:mm`) con el manifiesto `images` (las fotos que de verdad van en el zip; vacío en copias sin fotos). `decodeBackup` lee el JSON una sola vez y mira primero la versión (una copia más nueva se avisa aunque su formato no se entienda); `toImportData()` lo comprueba todo (ids no vacíos ni repetidos, manifiesto con nombres simples, fechas) antes de tocar nada; una foto con ruta en una receta se trata como una foto que falta (se importa sin ella). Límites: JSON 16 MB, cada foto 25 MB. **Sube `BACKUP_SCHEMA_VERSION`** con cualquier cambio que una versión anterior no sepa leer: campo obligatorio nuevo o valor nuevo de un enum (unidad, sección, tema…). Textos, nombres de archivo y orden de las copias del teléfono en `BackupTexts.kt`.
- Contenido: todas las categorías (las de serie con su nombre actual), recetas completas (con `categoryBaseType` por si su categoría falta al importar), compra y ajustes `{tema, taza}`. Nunca: estado de las copias, permiso de notificaciones, borradores ni la clave de la IA.
- `BackupManager` (en `AppContainer`, `backup/` de `:app`): `export(out)`, `preview(in)`, `import(in, mode)` (nunca cierra el flujo del llamador), y para las copias del teléfono `localBackups()`, `previewLocal`, `restoreLocal` (siempre «Reemplazar todo») y `copyLocalTo`; todo en `Dispatchers.IO`, de una en una (`Mutex`), sin lanzar excepciones (devuelven un resultado con el mensaje). `BackupExporter` escribe en flujo; el manifiesto nombra exactamente las fotos escritas (las que no existen o tienen ruta se omiten; si una desaparece a mitad, la exportación falla).
- `BackupImporter`: (1) lee y valida todo, con las fotos en `cacheDir/importar-<uuid>/` (zip-slip y subcarpetas se rechazan sin extraer nada; si falta una foto del manifiesto, «La copia está incompleta: faltan fotos.»); (2) si va a sustituir algo («Reemplazar todo», o «Combinar» cuando una receta local se actualiza) guarda antes `filesDir/backups/antes-de-importar-<AAAA-MM-DD-HHmm>[-N].zip` y solo la da por buena si se puede restaurar; si falla, no sigue; (3) trae a `photos/` las fotos que faltan, sin pisar ninguna; (4) una sola transacción; si falla, borra solo las fotos que acaba de traer; (5) después quita las traídas que nadie usa, al reemplazar borra solo las fotos que usaban las recetas anteriores (las de un borrador se respetan) y aplica los ajustes. Desde (2) no se interrumpe. La carpeta temporal se borra siempre. El resumen cuenta las fotos que no se pudieron copiar.
- «Combinar»: nunca borra; la receta de la copia gana entera solo si su `updatedAt` es posterior. Categorías y compra no tienen fecha de edición: se añaden las que faltan y las locales se dejan como están. Una receta cuya categoría no existe va a la de serie de su tipo base (`DefaultCategories.idFor`). Las categorías de serie nunca se borran. Los ajustes solo cambian al reemplazar.
- La receta completa se escribe con `writeRecipeAggregate` (`recipes/data/RecipeRepository.kt`), compartida con `RoomRecipeRepository.save`.
- Pantalla: `BackupRoute` (selectores del sistema `CreateDocument`/`OpenDocument`, `BackupViewModel`) y `BackupContent`. Al elegir un archivo (o «Restaurar» una copia del teléfono) primero se comprueba entero: si no vale, el error sale enseguida; si vale, el panel de modo y, para «Reemplazar todo», una segunda confirmación roja con «La copia tiene N recetas; ahora tienes M.». «Copias en el teléfono» lista `filesDir/backups/*.zip` (las más recientes primero según su nombre; las que no se pueden restaurar, «Copia dañada» sin acciones) con «Restaurar» y «Guardar fuera» (copia el archivo tal cual; no cambia «Última copia guardada fuera del teléfono»). Los documentos van por `BackupDocuments` (en pruebas, uno falso); el selector se falsea con `LocalActivityResultRegistryOwner` y, en `CapturasTest`, el contenido con `ShadowContentResolver.registerInputStreamSupplier`. Exportar anota `lastExternal`; si falla, borra el archivo a medias. La Tarea 16 añadirá las copias semanales a la misma carpeta y cambiará la copia previa por `writeLocalBackup()`.
- Solo en un teléfono se puede comprobar: Drive y otros proveedores reales (permisos, «wt», archivos grandes) y la protección de rutas de Android 14 (`ZipInputStream` lanza `ZipException`, que también se trata como «no es una copia»).

## Editor de recetas
El borrador se guarda en `filesDir/drafts/` (`new.json` o `<idReceta>.json`) con 500 ms de espera y se borra al guardar o descartar (`EditorViewModel`, `DraftStore`).

## Tecnología prevista
- Kotlin + Jetpack Compose (Material 3 personalizado), Room (SQLite), DataStore.
- Android 8.0 (API 26) como mínimo.
- Interfaz solo en español.
- Gradle (wrapper del proyecto). Compilación y publicación con GitHub Actions.

## Convenciones
- Textos de la interfaz en español; código e identificadores en inglés.
- Desarrollo guiado por pruebas (TDD) en la lógica: escalado de cantidades, conversión de unidades, lista de compra, motor de Hoy, reglas de maridaje y copias de seguridad.
- **Nunca** hagas migraciones destructivas de la base de datos. Cada migración lleva su prueba.
- Las animaciones deben ser breves, no bloquear el toque y respetar la opción del sistema de quitar animaciones.
- Mensajes de commit en español.

## Compilar y probar
Usa siempre el wrapper (`./gradlew`, Gradle 9.9.1) con JDK 21.
- `./gradlew -p logic test`: pruebas de la lógica pura. No necesita el SDK de Android.
- `./gradlew testDebugUnitTest assembleDebug`: pruebas de `:app` (Robolectric) y APK de depuración en `app/build/outputs/apk/debug/`. Necesita el SDK de Android; si no lo tienes, `scripts/setup-android-sdk.sh` lo instala (pide aceptar la licencia de Google).
- Una sola prueba: `./gradlew testDebugUnitTest --tests '*SmokeTest*'`.
- `bash scripts/signing/test-signing-scripts.sh`: pruebas de los scripts de firma (necesita `openssl` y `keytool`, no el SDK).
- `./gradlew assembleRelease`: sin la variable `RECETARIO_KEYSTORE` genera `app-release-unsigned.apk` (sin firmar ni minificar).
- **CI** (`.github/workflows/ci.yml`): en cada push y pull request ejecuta las pruebas de `logic/`, de los scripts de firma y de `:app`, compila los APK debug y release, comprueba que el release sale sin firmar y con la versión de `app/version.properties`, y ensaya la firma con una llave desechable (comprobada con `apksigner`). Si algo falla, sube los informes de prueba como artefacto `informes-de-prueba`.
- **Capturas:** `CapturasTest` (Robolectric con gráficos nativos) guarda Inicio, Recetas, el editor, el detalle, Ajustes, Categorías, Copias de seguridad (con sus paneles) y el modo cocina en los tres temas en `app/build/capturas/`; CI las sube siempre como artefacto `capturas` para revisarlas a ojo frente a los bocetos (no se commitean).
- **Nueva versión:** cambia `versionName` en `app/version.properties` (`versionCode = major*10000 + minor*100 + patch`; minor y patch de 0 a 99). Cada push a `main` publica `Recetario-<versionName>.apk` en la versión `v<versionName>` de Releases; si la versión ya existe, sustituye el APK.
- Versiones: AGP 9.5.1 con Kotlin integrado (no se aplica `org.jetbrains.kotlin.android`), Kotlin 2.4.21, KSP 2.3.13, `compileSdk`/`targetSdk` 37, `minSdk` 26. Todas en `gradle/libs.versions.toml`.

## Claves y secretos (sin valores reales, nunca)
- **El repositorio es PÚBLICO.** No subas claves, contraseñas, llaves de firma en claro ni datos personales.
- **Firma del APK:** el secreto de GitHub `SIGNING_PASSPHRASE` (20 caracteres o más) descifra la llave de firma, que en el repositorio solo se guarda cifrada en `signing/release.p12.enc` (§9 del diseño; AES-256-CBC con PBKDF2, alias `recetario`). Todas las versiones deben firmarse con la misma llave; si cambia, las actualizaciones no se instalan encima. Por eso `create-keystore.sh` y el flujo «Crear llave de firma» se niegan a crear otra si ya existe.
  - La llave en claro solo existe en directorios temporales (fuera del repositorio) y se borra con `trap`. Gradle la recibe por `RECETARIO_KEYSTORE` (ruta) y `SIGNING_PASSPHRASE`. Los scripts nunca imprimen la frase ni la pasan en la línea de órdenes.
- **Clave de Anthropic (IA):** la pega el usuario dentro de la app y se guarda cifrada en el teléfono. Nunca va en el código, en las copias de seguridad ni en el repositorio.
- **Open-Meteo:** no necesita clave. Es gratis para uso no comercial y exige atribución CC BY 4.0 en «Acerca de».

## Problemas conocidos / notas del entorno
- En el contenedor de Claude Code en la nube no hay SDK de Android (sí Java 21) y no se puede aceptar su licencia. Allí solo se prueba `logic/` en local; `:app` se verifica en GitHub Actions haciendo push a la rama de trabajo.
- Maven Central responde a veces `429 Too Many Requests` a través del proxy del contenedor. Basta con repetir el comando de Gradle.
- Los flujos `release.yml` y `create-signing-key.yml` solo funcionan en `main`: aparecen en Actions cuando el código ya está en `main` y fallan a propósito si se lanzan en otra rama (así no se firma ni se publica código sin fusionar, ni se crea una segunda llave). Antes de fusionar, CI ensaya sus pasos con una llave de prueba. Lo que sube un flujo con `GITHUB_TOKEN` no lanza otros flujos: por eso «Crear llave de firma» lanza «Publicar APK» con `gh workflow run`.
- Pruebas Compose: usan `installTestContainer()` (base de datos en memoria y reloj fijo) y la regla `v2`. Los datos de Room llegan en otro hilo: espera con `waitFor`/`waitForGone` (`app/src/test/.../testing/`). `waitForIdle` no adelanta los `delay()` de las animaciones (entrada escalonada): para capturar, adelanta `mainClock`. `performScrollToNode` deja a la vista el nodo buscado, no lo que hay debajo: desplaza hasta el nodo exacto antes de tocarlo.
- Pruebas Compose con Room: el `@After` que cierra la base de datos corre antes de que la regla de Compose deseche la composición, así que una consulta tardía de un `Flow` puede fallar con «connection pool has been closed» (visto una vez en `CategoriesScreenTest.categories_create_savesNewCategory`, CI 43608841436). Si se repite, cerrar la base de datos después de la regla.
- `api.open-meteo.com` está bloqueado por el proxy del contenedor. No afecta a la app (la llamada la hace el teléfono), pero no se puede probar desde aquí.
