# Recetario · Etapa 1 «Lo esencial» — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** primera versión instalable (0.1.0) del recetario. Incluye:
- recetas completas con fotos;
- búsqueda y filtros;
- detalle con porciones;
- modo cocina en lista con temporizadores;
- lista de compra;
- conversor;
- compartir como texto;
- copias de seguridad (manual, semanal local y en Google Drive);
- tres temas con Automático;
- APK firmado y publicado por GitHub Actions.

**Arquitectura:**
- Un solo módulo Gradle `:app`, organizado en paquetes por función.
- La interfaz es Jetpack Compose con un tema propio sobre Material 3.
- Datos en Room y ajustes en DataStore.
- Dependencias a mano, con un `AppContainer` creado en `RecetarioApp`.
- Los ViewModels se crean con `viewModelFactory { initializer { … } }`.
- La lógica pura (cantidades, filtros, lista de compra, conversor, temas, temporizadores, copias) vive en clases sin Android y se prueba con JUnit. Room y Compose se prueban en la JVM con Robolectric.

**Tecnología:** Kotlin, Jetpack Compose (Material 3), Navigation Compose con rutas tipadas, Room, DataStore Preferences, WorkManager, kotlinx-serialization, Coil 3 y la biblioteca de reordenación `sh.calvin.reorderable`. Pruebas con JUnit 4, Truth, Turbine, Robolectric y Compose UI Test.

**Spec:** `docs/superpowers/specs/2026-11-13-recetario-design.md` (con los bocetos en `docs/superpowers/specs/2026-11-13-recetario-bocetos/`). Léelo antes de cada tarea; este plan argumenta desde él.

## Restricciones globales

- `applicationId` y `namespace`: `app.recetario`. `minSdk` 26.
- Interfaz **solo en español**; código e identificadores en inglés. Mensajes de commit en español con los trailers de la sesión.
- Repositorio **público**: ningún secreto, llave ni dato personal en git. Solo puede existir `signing/release.p12.enc`.
- IDs de entidades: `String` UUID. Las categorías de serie tienen IDs fijos `cat-comidas`, `cat-bebidas` y `cat-postres`, y se pueden renombrar pero no borrar.
- Room: `exportSchema = true` en `app/schemas/`. Prohibido `fallbackToDestructiveMigration`.
- Animaciones de 150 a 450 ms, sin bloquear toques y desactivadas si `LocalReducedMotion.current` es `true`.
- Contraste de texto ≥ 4,5:1 en los tres temas (§6.1 del spec, con los colores corregidos de la Tarea 3).
- Todo funciona sin conexión. La etapa 1 no hace ninguna llamada de red.
- Fuentes incluidas en `app/src/main/res/font/` (Fraunces, Inter, Nunito y Playfair Display, todas OFL), con sus licencias en `app/src/main/assets/licenses/`.
- Fecha y hora con `java.time` y un `java.time.Clock` inyectado (nunca `System.currentTimeMillis()` en la lógica).
- Robolectric con `sdk=35` en `app/src/test/resources/robolectric.properties`.
- Accesibilidad:
  - textos en `sp` que respetan el tamaño de letra del sistema;
  - áreas táctiles de 48 dp como mínimo;
  - `contentDescription` en español en iconos e imágenes.
- Rendimiento:
  - listas `Lazy*` con `key = id`;
  - miniaturas cargadas por Coil con el tamaño de destino;
  - fluidez con 500 recetas.
- Navegación: cada tarea añade sus destinos al `NavHost` de la Tarea 9.
- Dependencias: cada tarea registra en `AppContainer` lo que crea.

## Foco de revisión

Cinco entradas que el spec implica y que la gente encontrará, aunque ninguna prueba de función las cubra de forma natural:
1. **Cantidades escritas a la española:** `1,5`, `1/2`, `1 1/2` y `½` deben entenderse como 1,5, 0,5, 1,5 y 0,5. Un punto (`1.5`) también vale. → Tarea 7.
2. **Fotos que ya no existen en disco** (borradas o restauradas sin imágenes): se muestra un marcador de posición y la app no se cierra. → Tarea 11.
3. **Salir del editor sin guardar o que Android cierre la app:** el borrador se recupera. → Tarea 7.
4. **Girar el teléfono o volver a la app en modo cocina:** se conservan el paso actual, los ingredientes tachados y los temporizadores. → Tarea 13.
5. **Textos muy largos** (título de 120 caracteres, paso de 1500, 60 ingredientes): las tarjetas cortan con «…» y las pantallas desplazan sin recortar. → Tareas 9 y 11.

---

## Mapa de archivos

```
app/src/main/java/app/recetario/
  RecetarioApp.kt                 Application + AppContainer
  MainActivity.kt                 Activity única, tema + navegación
  core/text/Normalize.kt          normalize()
  core/time/ClockModule.kt        reloj inyectable
  core/ui/theme/                  Palettes.kt, Typography.kt, RecetarioTheme.kt, ThemeResolver.kt, Contrast.kt, ThemeReveal.kt
  core/ui/components/             FlavorWheel.kt, Entrance.kt, Pills.kt, ServingsStepper.kt, AnimatedCheck.kt, PhotoOrPlaceholder.kt
  core/ui/motion/ReducedMotion.kt
  core/data/                      AppDatabase.kt, Converters.kt, DefaultCategories.kt
  recipes/model/                  Recipe.kt (modelos y enums de dominio)
  recipes/data/                   Entities.kt, RecipeDao.kt, CategoryDao.kt, TagDao.kt, RecipeRepository.kt, PhotoStore.kt
  recipes/quantity/               MeasureUnit.kt, QuantityFormatter.kt, QuantityParser.kt
  recipes/search/                 RecipeFilter.kt
  recipes/editor/                 EditorState.kt, EditorViewModel.kt, EditorHelpers.kt, DraftStore.kt, EditorScreen.kt
  recipes/list/                   RecipesScreen.kt, RecipesViewModel.kt
  recipes/detail/                 DetailScreen.kt, DetailViewModel.kt, RecipeTextFormatter.kt
  home/                           HomeScreen.kt, HomeViewModel.kt
  pairing/PairingPlaceholderScreen.kt
  cooking/                        CookingScreen.kt, CookingViewModel.kt, TimerManager.kt, TimerService.kt, KeepScreenOn.kt
  shopping/                       ShoppingModels.kt, SectionClassifier.kt, ShoppingMerge.kt, ShoppingDao.kt, ShoppingRepository.kt, ShoppingScreen.kt, ShoppingViewModel.kt
  converter/                      UnitConverter.kt, Densities.kt, ConverterScreen.kt
  settings/                       SettingsRepository.kt, SettingsScreen.kt, AppearanceScreen.kt, CategoriesScreen.kt, TagsScreen.kt, AboutScreen.kt
  backup/                         BackupModel.kt, BackupExporter.kt, BackupImporter.kt, BackupManager.kt, AutoBackupWorker.kt, BackupRotation.kt, BackupReminder.kt, BackupScreen.kt
  navigation/                     Routes.kt, AppNavHost.kt, BottomBar.kt
app/src/main/res/xml/             backup_rules.xml, data_extraction_rules.xml, file_paths.xml
app/src/test/java/app/recetario/… pruebas espejo de cada paquete
.github/workflows/                ci.yml, release.yml, create-signing-key.yml
scripts/                          setup-android-sdk.sh, signing/{create-keystore.sh,decrypt-keystore.sh,test-signing-scripts.sh}
docs/instalar.md                  guía de instalación en español
```

---

### Tarea 1: Proyecto Android, cadena de herramientas e integración continua

**Archivos:**
- Crear: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`
- Crear: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/app/recetario/{RecetarioApp.kt,MainActivity.kt}`, `app/src/main/res/values/strings.xml` (`app_name` = «Recetario»), icono adaptativo sencillo (un cuenco con el acento terracota)
- Crear: `app/src/test/resources/robolectric.properties`, `app/src/test/java/app/recetario/SmokeTest.kt`
- Crear: `.github/workflows/ci.yml`, `scripts/setup-android-sdk.sh`
- Modificar: `.gitignore` (añadir `.gradle/`, `build/`, `local.properties`, `*.iml`, `.idea/`, `signing/*.p12`, `*.jks`, `*.keystore`), `CLAUDE.md` (sección «Compilar y probar»)

**Interfaces:**
- Produce: `class RecetarioApp : Application { lateinit var container: AppContainer }` y `class AppContainer(context: Context)`, vacío por ahora; las siguientes tareas le añaden propiedades. Produce también `MainActivity`, una `ComponentActivity` con `enableEdgeToEdge()`.

- [ ] **Paso 1: versiones.** Partir de estas versiones estables y verificar que compilan juntas:
  - AGP 9.5.1 + Gradle 9.9.1 (wrapper). Con AGP 9, Kotlin va integrado: **no** aplicar `org.jetbrains.kotlin.android`.
  - Kotlin 2.4.21, con los plugins `org.jetbrains.kotlin.plugin.compose` y `org.jetbrains.kotlin.plugin.serialization`.
  - KSP 2.3.13, plugin `androidx.room` 2.8.5, `compileSdk`/`targetSdk` 37.
  - Compose BOM 2026.09.00, activity-compose 1.13.0, lifecycle 2.11.0, navigation-compose 2.10.2, room 2.8.5, datastore-preferences 1.3.0, work-runtime-ktx 2.12.0, core-ktx 1.19.1, kotlinx-serialization-json 1.13.0, coroutines 1.11.0, coil-compose 3.6.3, reorderable 3.1.0, documentfile 1.1.0.
  - Para pruebas: junit 4.13.2, truth 1.4.5, turbine 1.2.1, robolectric 4.17, androidx.test.ext:junit 1.3.0, compose ui-test-junit4 y ui-test-manifest.
  - **Si** AGP 9 con Kotlin integrado choca con KSP o Room, bajar a AGP 8.13.2 + Gradle 8.14.3 + Kotlin 2.3.21 con `org.jetbrains.kotlin.android`. Bajar `compileSdk` a 36 y las bibliotecas que lo exijan.
  - Las versiones finales quedan en `gradle/libs.versions.toml`, y el cambio se explica en el mensaje de commit.
- [ ] **Paso 2: escribir la prueba de humo.** `SmokeTest` (Robolectric) lanza `MainActivity` con `ActivityScenario` y comprueba con `createAndroidComposeRule<MainActivity>()` que se muestra el texto «Recetario».
- [ ] **Paso 3: ejecutarla y verla fallar** (todavía no existe la Activity). Comando: `./gradlew testDebugUnitTest --tests '*SmokeTest*'`.
- [ ] **Paso 4: crear el proyecto mínimo** para que pase. `testOptions.unitTests.isIncludeAndroidResources = true`; `room { schemaDirectory("$projectDir/schemas") }`; `buildFeatures.compose = true`; Java/Kotlin con destino 17.
- [ ] **Paso 5: verificar.** Comando: `./gradlew testDebugUnitTest assembleDebug`. Resultado esperado: `BUILD SUCCESSFUL` y un APK en `app/build/outputs/apk/debug/`.
  - Si no hay SDK local, ejecutar antes `scripts/setup-android-sdk.sh`, que pide aceptar la licencia de Google de forma interactiva o con `ANDROID_ACCEPT_LICENSES=yes`, una vez que el usuario lo autoriza.
  - El script instala `cmdline-tools` (última versión), `platform-tools`, `platforms;android-<compileSdk>` y `build-tools;<la que pida AGP>` en `$HOME/android-sdk`, y escribe `local.properties`.
- [ ] **Paso 6: `ci.yml`.** En `push` y `pull_request`: checkout, `actions/setup-java@v4` (temurin 21), `gradle/actions/setup-gradle@v4` y `./gradlew testDebugUnitTest assembleDebug`. Sube los informes de prueba si algo falla.
- [ ] **Paso 7: documentar** los comandos en `CLAUDE.md` y hacer commit: «Crear proyecto Android con integración continua».

### Tarea 2: Firma y publicación del APK

**Archivos:**
- Crear: `scripts/signing/create-keystore.sh`, `scripts/signing/decrypt-keystore.sh`, `scripts/signing/test-signing-scripts.sh`
- Crear: `.github/workflows/create-signing-key.yml`, `.github/workflows/release.yml`, `app/version.properties` (`versionName=0.1.0`), `docs/instalar.md`
- Modificar: `app/build.gradle.kts`

**Interfaces:**
- Produce: variables de entorno `RECETARIO_KEYSTORE` (ruta del `.p12` descifrado) y `SIGNING_PASSPHRASE`, y el alias de llave `recetario`.
- Produce: `versionCode = major*10000 + minor*100 + patch`, leído de `app/version.properties`.

- [ ] **Paso 1: escribir `test-signing-scripts.sh`.** Usa una frase de prueba en un directorio temporal y comprueba:
  - (a) `create-keystore.sh` produce `release.p12.enc` y no deja ningún `.p12` en claro;
  - (b) `decrypt-keystore.sh` lo descifra y `keytool -list -keystore … -storepass "$SIGNING_PASSPHRASE"` muestra el alias `recetario`;
  - (c) con una frase incorrecta, el descifrado sale con código distinto de 0;
  - (d) `create-keystore.sh` se niega (código distinto de 0) si `release.p12.enc` ya existe;
  - (e) se niega con una frase de menos de 20 caracteres.
- [ ] **Paso 2: ejecutarlo** (`bash scripts/signing/test-signing-scripts.sh`) y verlo fallar.
- [ ] **Paso 3: implementar los scripts.**
  - `keytool -genkeypair -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 36500 -alias recetario -dname "CN=Recetario"`, con la frase como contraseña del almacén y de la llave.
  - Cifrado: `openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -pass env:SIGNING_PASSPHRASE`.
  - Usar `set -euo pipefail` y borrar el `.p12` en claro con `trap`.
- [ ] **Paso 4: ejecutarlo y verlo pasar.** Resultado esperado: `OK` en las 5 comprobaciones.
- [ ] **Paso 5: Gradle.** Configurar `signingConfigs.release` solo si existe `RECETARIO_KEYSTORE`, y versión y código desde `version.properties`. El build release va sin minificar en la etapa 1 (decisión: menos riesgo de fallos que solo aparecen en release).
- [ ] **Paso 6: flujos de GitHub.**
  - **`create-signing-key.yml`** (lanzamiento manual con `workflow_dispatch`; `permissions: contents: write, actions: write`):
    - falla con un mensaje en español si falta el secreto o si la llave ya existe;
    - genera la llave y hace commit de `signing/release.p12.enc`;
    - lanza `gh workflow run release.yml --ref ${{ github.ref_name }}`.
  - **`release.yml`** (en `push` a `main` y con `workflow_dispatch`):
    - si falta `signing/release.p12.enc`, falla con «Falta la llave: ejecuta primero el flujo «Crear llave de firma»»;
    - descifra la llave en `$RUNNER_TEMP` y ejecuta `./gradlew testDebugUnitTest assembleRelease`;
    - renombra el resultado a `Recetario-<versionName>.apk`;
    - crea la versión `v<versionName>` o sube el archivo con `--clobber` si ya existe (`gh release`).
- [ ] **Paso 7: `docs/instalar.md`** en español, con los pasos de §9 del spec:
  1. Crear el secreto `SIGNING_PASSPHRASE` (frase de 20 caracteres o más, guardada en un gestor de contraseñas). Ruta: Settings › Secrets and variables › Actions.
  2. Ejecutar «Crear llave de firma» en la rama `main` (los flujos solo aparecen una vez que el código está en `main`).
  3. Descargar el APK desde Releases en el móvil.
  4. Permitir «instalar apps desconocidas».
  5. Las actualizaciones se instalan encima.
- [ ] **Paso 8: verificar y hacer commit.** Ejecutar `bash scripts/signing/test-signing-scripts.sh && ./gradlew assembleRelease`; sin `RECETARIO_KEYSTORE` debe generar un APK release sin firmar. Commit: «Añadir firma y publicación automática del APK».

### Tarea 3: Sistema de temas

**Archivos:**
- Crear: `core/ui/theme/{Palettes.kt,Typography.kt,RecetarioTheme.kt,ThemeResolver.kt,Contrast.kt}` y `core/ui/motion/ReducedMotion.kt`
- Crear: `res/font/*` (archivos variables de google/fonts: `Fraunces[SOFT,WONK,opsz,wght].ttf`, `Inter[opsz,wght].ttf`, `Nunito[wght].ttf`, `PlayfairDisplay[wght].ttf`, renombrados en minúsculas) y `assets/licenses/OFL-*.txt`
- Pruebas: `core/ui/theme/ThemeResolverTest.kt`, `ContrastTest.kt`

**Interfaces:**
- Produce:
  - `enum class ThemeChoice { CALIDO, FRESCO, NOCTURNO }`
  - `data class ThemeSettings(val auto: Boolean = true, val choice: ThemeChoice = ThemeChoice.CALIDO, val dayTheme: ThemeChoice = ThemeChoice.CALIDO, val nightStart: LocalTime = LocalTime.of(20, 0), val nightEnd: LocalTime = LocalTime.of(7, 0))`
  - `fun resolveTheme(settings: ThemeSettings, now: LocalTime, systemDark: Boolean): ThemeChoice`
  - `data class RecetarioColors(bg, surface, soft, ink, muted, line, accent, onAccent: Color, todayBrush: Brush, todayInk: Color, todayBorder: Color?, heroBrush: Brush)`
  - `data class RecetarioShapes(val radius: Dp)`
  - `val LocalRecetarioColors`, `val LocalRecetarioShapes`
  - `@Composable fun RecetarioTheme(choice: ThemeChoice, content: @Composable () -> Unit)`, que también fija `MaterialTheme.colorScheme` y la tipografía
  - `fun contrastRatio(fg: Color, bg: Color): Double`
  - `val LocalReducedMotion: ProvidableCompositionLocal<Boolean>`, leído de `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`
- Paletas: las de §6.1 del spec, salvo estos colores corregidos para el contraste:
  - Cálido: `muted #796B5D`, `accent #B34F36`.
  - Fresco: `muted #68746C`, `accent #467C53`.
  - Nocturno no cambia.

- [ ] **Paso 1: escribir las pruebas.**
  - `ThemeResolverTest`:
    - `manual_returnsChoice`: auto=false, choice=FRESCO, a las 23:00 → FRESCO.
    - `auto_day_returnsDayTheme`: 13:00, systemDark=false, dayTheme=FRESCO → FRESCO.
    - `auto_night_returnsNocturno`: a las 20:00 y a las 06:59 → NOCTURNO (la noche cruza la medianoche).
    - `auto_7am_isDay`: 07:00 → dayTheme.
    - `auto_systemDark_returnsNocturno`: 13:00 con systemDark=true → NOCTURNO.
  - `ContrastTest`, para cada `ThemeChoice`:
    - `ink`, `muted` y `accent` sobre `bg` y `surface` ≥ 4.5;
    - `onAccent` sobre `accent` ≥ 4.5;
    - `contrastRatio(Color.Black, Color.White)` ≈ 21.0 (±0.01).
- [ ] **Paso 2: ejecutarlas y verlas fallar** con `./gradlew testDebugUnitTest --tests '*theme*'`.
- [ ] **Paso 3: implementar.**
  - Tipografía por tema: los títulos con la fuente de títulos y el peso de §6.1 (Fraunces 600, Nunito 800, Playfair 600) usando `FontVariation.Settings(FontVariation.weight(n))`; el cuerpo con Inter o Nunito.
  - Radios: 18, 24 y 16 dp.
  - `todayBrush` y `heroBrush`: los degradados del boceto (`receta-y-cocina.html` y `estilo-visual.html`).
- [ ] **Paso 4: ejecutar las pruebas y verlas pasar.**
- [ ] **Paso 5: commit:** «Añadir los tres temas con modo automático y contraste verificado».

### Tarea 4: Modelo de dominio, base de datos y repositorio de recetas

**Archivos:**
- Crear: `recipes/model/Recipe.kt`, `core/text/Normalize.kt`, `core/data/{AppDatabase.kt,Converters.kt,DefaultCategories.kt}`, `recipes/data/{Entities.kt,RecipeDao.kt,CategoryDao.kt,TagDao.kt,RecipeRepository.kt,PhotoStore.kt}`
- Modificar: `RecetarioApp.kt` (`AppContainer` añade `database`, `photoStore`, `recipeRepository` y `clock`)
- Pruebas: `recipes/data/RecipeRepositoryTest.kt` (Robolectric con Room en memoria), `recipes/data/PhotoStoreTest.kt`, `core/text/NormalizeTest.kt`

**Interfaces:**
- Produce `fun normalize(text: String): String`: minúsculas, sin tildes (NFD + quitar diacríticos), sin espacios sobrantes.
- Produce estos enums, cada uno con `val label: String` en español:
  - `BaseType { FOOD, DRINK, DESSERT, OTHER }` («Comida», «Bebida», «Postre», «Otro»)
  - `Difficulty { EASY, MEDIUM, HARD }`
  - `Intensity { LIGHT, MEDIUM, INTENSE }`
  - `ServeTemp { HOT, COLD, ROOM }` («Caliente», «Fría», «Ambiente»)
  - `Moment { BREAKFAST, LUNCH, SNACK, DINNER, NIGHT }` («Desayuno», «Comida», «Merienda», «Cena», «Aperitivo o noche»)
  - `Course { STARTER, MAIN, SIDE }` («Entrada», «Principal», «Acompañamiento»)
- Produce los modelos:
  - `data class FlavorProfile(val sweet: Int = 0, val salty: Int = 0, val sour: Int = 0, val bitter: Int = 0, val spicy: Int = 0, val intensity: Intensity = Intensity.MEDIUM, val rich: Boolean = false)`. Los cinco sabores van de 0 a 3.
  - `data class Ingredient(val id: String, val section: String?, val name: String, val quantity: Double?, val unit: MeasureUnit, val customUnit: String?, val note: String)`. `MeasureUnit` es de la Tarea 5; crearlo aquí vacío si hace falta y completarlo allí.
  - `data class Step(val id: String, val text: String, val photos: List<String>, val timerSeconds: Int?, val ingredientIds: Set<String>)`
  - `data class Recipe(id, title, categoryId, coverPhoto: String?, description: String, totalMinutes: Int?, servings: Int, difficulty, flavor: FlavorProfile, serveTemp: ServeTemp?, moments: Set<Moment>, alcoholic: Boolean?, course: Course?, ingredients: List<Ingredient>, steps: List<Step>, tags: List<String>, notes: String, favorite: Boolean, sourceUrl: String?, aiGenerated: Boolean, createdAt: Instant, updatedAt: Instant)`
  - `data class RecipeSummary(id, title, categoryId, coverPhoto, totalMinutes, flavor, serveTemp, moments, alcoholic, difficulty, favorite, tags: List<String>, ingredientNames: List<String>, updatedAt: Instant)`
  - `data class Category(id, name, emoji, colorHex, baseType: BaseType, sortOrder: Int, isDefault: Boolean)`
- Produce `interface RecipeRepository`:
  - `observeSummaries(): Flow<List<RecipeSummary>>`
  - `observeRecipe(id): Flow<Recipe?>`
  - `suspend fun save(recipe: Recipe)`: inserta o actualiza el agregado en una transacción y sustituye ingredientes, pasos, fotos de paso, relaciones y etiquetas.
  - `suspend fun delete(id)`: borra también los archivos de foto.
  - `suspend fun setFavorite(id, Boolean)`
  - `observeCategories(): Flow<List<Category>>`
  - `suspend fun saveCategory(Category)`
  - `suspend fun deleteCategory(id, reassignTo: String)`: lanza `IllegalArgumentException` si es de serie.
  - `observeTags(): Flow<List<String>>`
  - `suspend fun renameTag(old, new)`, `suspend fun deleteTag(name)`
- Produce `class PhotoStore(dir: File)`:
  - `suspend fun import(source: InputStream): String` comprime a JPEG de calidad 85 con el lado mayor ≤ 1600 px y devuelve el nombre del archivo;
  - `fun file(name): File`
  - `fun delete(name)`
  - `companion fun targetSize(w: Int, h: Int, max: Int = 1600): Pair<Int, Int>`
- Tablas: `recipes`, `ingredients`, `steps`, `step_photos`, `step_ingredients`, `tags` (nombre normalizado único), `recipe_tags` y `categories`. Los hijos se borran en cascada.

- [ ] **Paso 1: escribir las pruebas.**
  - `RecipeRepositoryTest`:
    - `defaultCategories_existOnCreate`: IDs `cat-comidas`/`cat-bebidas`/`cat-postres` con BaseType FOOD/DRINK/DESSERT.
    - `save_thenObserve_roundTripsWholeAggregate`: una receta con 3 ingredientes, 2 pasos (uno con 2 fotos y un ingrediente enlazado) y 2 etiquetas; se lee igual, con el orden conservado.
    - `save_update_replacesChildren`: de 3 ingredientes se pasa a 1 y quedan 1.
    - `delete_cascades_and_deletesPhotoFiles`
    - `deleteCategory_reassignsRecipes`
    - `deleteDefaultCategory_throws`
    - `tags_areDedupedByNormalizedName`: «Rápido» y «rapido» son una sola etiqueta.
  - `PhotoStoreTest`: `targetSize(4000, 3000)` → `(1600, 1200)`; `targetSize(800, 600)` → `(800, 600)`.
  - `NormalizeTest`: `normalize("  Limón  Ácido ")` → «limon acido».
- [ ] **Paso 2: ejecutarlas y verlas fallar.**
- [ ] **Paso 3: implementar.**
  - Las categorías de serie se crean con `RoomDatabase.Callback.onCreate`: «Comidas» 🍲, «Bebidas» 🍹 y «Postres» 🍮.
  - `Moment`, que es un conjunto, se guarda como texto separado por comas mediante un `TypeConverter`.
- [ ] **Paso 4: ejecutar las pruebas y verlas pasar.** Comprobar también que existe `app/schemas/app.recetario.core.data.AppDatabase/1.json`.
- [ ] **Paso 5: commit:** «Añadir modelo de recetas, base de datos y repositorio».

### Tarea 5: Unidades, escalado, formato y conversión de cantidades

**Archivos:**
- Crear: `recipes/quantity/{MeasureUnit.kt,QuantityFormatter.kt}`, `converter/{UnitConverter.kt,Densities.kt}`
- Pruebas: `recipes/quantity/QuantityFormatterTest.kt`, `converter/UnitConverterTest.kt`

**Interfaces:**
- Produce `enum class MeasureUnit(val kind: UnitKind, val singular: String, val plural: String)`:

  | Unidad | Singular | Plural |
  |---|---|---|
  | `GRAM` | «g» | «g» |
  | `KILOGRAM` | «kg» | «kg» |
  | `MILLILITER` | «ml» | «ml» |
  | `LITER` | «l» | «l» |
  | `CUP` | «taza» | «tazas» |
  | `TABLESPOON` | «cucharada» | «cucharadas» |
  | `TEASPOON` | «cucharadita» | «cucharaditas» |
  | `PIECE` | «» | «» |
  | `CLOVE` | «diente» | «dientes» |
  | `PINCH` | «pizca» | — |
  | `TO_TASTE` | «al gusto» | — |
  | `OUNCE` | «oz» | «oz» |
  | `POUND` | «lb» | «lb» |
  | `FLUID_OUNCE` | «fl oz» | «fl oz» |
  | `CUSTOM` | — | — |

  Con `enum class UnitKind { MASS, VOLUME, COUNT, NONE }`.
- Produce las funciones:
  - `fun scaleQuantity(q: Double?, baseServings: Int, targetServings: Int): Double?`
  - `fun formatQuantity(q: Double?, unit: MeasureUnit, customUnit: String?): String`
  - `fun formatFraction(v: Double): String`: redondea a la fracción más cercana entre {0, ¼, ⅓, ½, ⅔, ¾} sobre el entero; nunca devuelve «0» para un v > 0 (el mínimo es «¼»).
  - `sealed interface Conversion { data class Ok(val value: Double, val unit: MeasureUnit) : Conversion; data object NeedsDensity : Conversion; data object Incompatible : Conversion }`
  - `class UnitConverter(cupMl: Int = 240)`:
    - `fun convert(value: Double, from: MeasureUnit, to: MeasureUnit, ingredient: String? = null): Conversion`
    - equivalencias: cucharada 15 ml, cucharadita 5 ml, oz 28,3495 g, lb 453,592 g, fl oz 29,5735 ml;
    - entre volumen y masa usa `Densities`.
  - `object Densities { fun gramsPerMl(ingredient: String): Double? }`:
    - busca por palabra clave normalizada;
    - incluye como mínimo: harina 0,52; azúcar 0,83; azúcar glas 0,5; arroz 0,79; mantequilla 0,95; leche 1,03; agua 1,0; aceite 0,92; miel 1,42; cacao 0,42; avena 0,38; sal 1,2;
    - la coincidencia más larga gana, así que «azúcar glas» va antes que «azúcar».
- Reglas de formato:
  - g y ml: con 1000 o más pasan a kg o l con hasta 2 decimales, coma decimal y sin ceros finales; por debajo de 10 llevan 1 decimal sin ceros finales; entre 10 y 999, entero.
  - oz, lb y fl oz: 1 decimal sin ceros finales.
  - Taza, cucharada, cucharadita, unidad, diente y personalizada: con fracción.
  - `PINCH` → «una pizca» y `TO_TASTE` → «al gusto», sin escalar.
  - Cantidad `null` → «».

- [ ] **Paso 1: escribir las pruebas** con los valores del boceto (`formatQuantity(scaleQuantity(q, 4, n), unit, null)`):
  - 800 g ×6/4 → «1,2 kg»
  - 750 ml ×6/4 → «1,13 l»
  - 2 dientes ×1/4 → «½ diente»
  - 1 unidad ×6/4 → «1½»
  - 100 ml ×1/4 → «25 ml»
  - 20 g ×1/4 → «5 g»
  - 2,5 g → «2,5 g»
  - 1 taza → «1 taza»; 2 → «2 tazas»; 0,33 → «⅓ taza»
  - `PINCH` → «una pizca»; `TO_TASTE` → «al gusto»
  - 0,05 unidades → «¼»
  - `UnitConverterTest`:
    - 1 taza de harina → Ok(124,8, GRAM), que se formatea como «125 g»;
    - 1 cucharada → 15 ml;
    - 8 oz → 226,8 g;
    - 1 lb → 453,6 g;
    - 1 fl oz → 29,57 ml;
    - 1 taza con `cupMl = 250` → 250 ml;
    - 1 taza de «xyz» a g → NeedsDensity;
    - 1 diente a ml → Incompatible;
    - «Azúcar glas» → 0,5.
- [ ] **Paso 2: verlas fallar.**
- [ ] **Paso 3: implementar.**
- [ ] **Paso 4: verlas pasar.**
- [ ] **Paso 5: commit:** «Añadir unidades, escalado y conversión de cantidades».

### Tarea 6: Búsqueda y filtros

**Archivos:**
- Crear: `recipes/search/RecipeFilter.kt`
- Pruebas: `recipes/search/RecipeFilterTest.kt`

**Interfaces:**
- Consume: `RecipeSummary`, `normalize()`.
- Produce:
  - `enum class Taste { SWEET, SALTY }`
  - `fun classifyTaste(sweet: Int, salty: Int): Taste?`: dulce si sweet ≥ 2 y sweet ≥ salty; salada si salty ≥ 2 y salty > sweet; si no, `null`.
  - `enum class SortOrder { RECENT, ALPHABETIC }`. «Más cocinadas» y «mejor valoradas» llegan con el diario en la etapa 2.
  - `data class RecipeFilter(val query: String = "", val categoryIds: Set<String> = emptySet(), val tags: Set<String> = emptySet(), val taste: Taste? = null, val alcoholic: Boolean? = null, val serveTemp: ServeTemp? = null, val maxMinutes: Int? = null, val moment: Moment? = null, val difficulty: Difficulty? = null, val favoritesOnly: Boolean = false, val sort: SortOrder = SortOrder.RECENT)`
  - `fun List<RecipeSummary>.applyFilter(f: RecipeFilter): List<RecipeSummary>`: el texto busca, sin tildes, en título, ingredientes y etiquetas; todos los filtros se combinan con Y.

- [ ] **Paso 1: escribir las pruebas.**
  - `query_isAccentInsensitive`: «limon» encuentra «Limonada con hierbabuena».
  - `query_matchesIngredient`: «calabaza» encuentra «Crema de calabaza» por su ingrediente.
  - `classifyTaste`: (2,2) → SWEET; (2,3) → SALTY; (1,1) → null; (3,0) → SWEET; (0,2) → SALTY.
  - `filters_combineWithAnd`: bebida salada sin alcohol de 2 × 2 × 2.
  - `maxMinutes_excludesUnknownTimes`: si `totalMinutes` es `null`, la receta queda fuera cuando hay tope.
  - `sort_alphabetic_ignoresAccents`: «Ánade» va antes que «Bizcocho».
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: commit:** «Añadir búsqueda y filtros de recetas».

### Tarea 7: Lógica del editor de recetas

**Archivos:**
- Crear: `recipes/quantity/QuantityParser.kt`, `recipes/editor/{EditorState.kt,EditorHelpers.kt,DraftStore.kt,EditorViewModel.kt}`
- Pruebas: `QuantityParserTest.kt`, `EditorHelpersTest.kt`, `EditorViewModelTest.kt`

**Interfaces:**
- Consume: `RecipeRepository`, `PhotoStore`, `Recipe` y sus enums, `normalize()`, `Clock`.
- Produce:
  - `fun parseQuantity(text: String): Double?`: admite «1,5», «1.5», «1/2», «1 1/2», «½», «1½» y «»; devuelve `null` si no lo entiende.
  - `fun detectDurationSeconds(text: String): Int?`: horas (`h|hora|horas`), minutos (`min|minuto|minutos`) y segundos (`s|seg|segundo|segundos`), sumando los de la misma expresión.
  - `fun suggestStepIngredients(stepText: String, ingredients: List<Ingredient>): Set<String>`: coincidencia normalizada del nombre completo o de su primera palabra de 4 letras o más.
  - `fun defaultsFor(base: BaseType): Pair<Set<Moment>, ServeTemp>`:
    - FOOD → ({LUNCH, DINNER}, HOT)
    - DRINK → ({LUNCH, SNACK, DINNER}, COLD)
    - DESSERT → ({LUNCH, SNACK, DINNER}, ROOM)
    - OTHER → ({LUNCH, DINNER}, ROOM)
  - `fun FlavorProfile.markSweet(): FlavorProfile` (sweet = max(sweet, 2), salty = min(salty, 1)) y `markSalty()` (la inversa).
  - `@Serializable data class EditorState(…)`: todos los campos editables de `Recipe` como texto o valor, más `cookingMinutesText`, `servings` y `errors: Map<Field, String>`.
  - `class DraftStore(file: File)`: `save(EditorState)`, `load(): EditorState?` y `clear()`, en JSON.
  - `class EditorViewModel(savedStateHandle, repo, photoStore, draftStore, clock)`:
    - `state: StateFlow<EditorState>` y `fun onEvent(e: EditorEvent)`;
    - `suspend fun save(): String?`: devuelve el ID, o `null` si hay errores;
    - si al iniciar existe un borrador, `pendingDraft: StateFlow<EditorState?>` permite «recuperar» o «descartar».
  - Errores en español: «Ponle un nombre a la receta» y «Elige una categoría».
  - Al elegir una categoría cuyos momentos y temperatura están vacíos, se rellenan con `defaultsFor`, marcados como sugeridos y editables.

- [ ] **Paso 1: escribir las pruebas.**
  - `parseQuantity`: «1,5» → 1.5; «1.5» → 1.5; «1/2» → 0.5; «1 1/2» → 1.5; «½» → 0.5; «1½» → 1.5; «» → null; «abc» → null. *(Foco de revisión 1.)*
  - `detectDurationSeconds`: «Cuece 20 minutos» → 1200; «1 h 30 min» → 5400; «Hornea 1 hora» → 3600; «Reposa 45 segundos» → 45; «Sirve» → null.
  - `suggestStepIngredients`: «Añade la calabaza y el caldo» con [Calabaza, Caldo de verduras, Crema] → {calabaza, caldo}.
  - `markSweet` sobre (0,3) → (2,1), y `classifyTaste` da SWEET.
  - `EditorViewModelTest`:
    - `save_withoutTitle_setsError_andReturnsNull`
    - `save_valid_persistsRecipe`
    - `reorderIngredients_keepsIds`
    - `leavingWithChanges_savesDraft_andNextOpenOffersIt` *(foco de revisión 3)*
    - `successfulSave_clearsDraft`
    - `chooseDrinkCategory_prefillsMomentsAndCold`
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: commit:** «Añadir la lógica del editor con borrador y detección de tiempos».

### Tarea 8: Componentes visuales compartidos

**Archivos:**
- Crear: `core/ui/components/{FlavorWheel.kt,Entrance.kt,Pills.kt,ServingsStepper.kt,AnimatedCheck.kt,PhotoOrPlaceholder.kt}`
- Pruebas: `core/ui/components/FlavorWheelTest.kt` (pura y Compose con Robolectric)

**Interfaces:**
- Produce:
  - `fun flavorPolygon(values: List<Int>, center: Offset, radius: Float): List<Offset>`: el primer eje apunta hacia arriba, en sentido horario; el radio es `v/3 · radius` y el 0 se dibuja al 6 % del radio.
  - `@Composable fun FlavorWheel(profile: FlavorProfile, modifier: Modifier = Modifier, size: Dp = 120.dp, animate: Boolean = true)`:
    - rejilla de 4 pentágonos y ejes con las etiquetas «Dulce», «Salado», «Ácido», «Amargo» y «Picante»;
    - polígono del acento con un relleno de 28 % de opacidad que crece desde el centro;
    - `contentDescription` del tipo «Perfil de sabor: dulce 3 de 3, salado 3 de 3, ácido 1 de 3, amargo 0 de 3, picante 1 de 3».
  - `fun Modifier.staggeredEntrance(index: Int): Modifier`: sube 14 dp y aparece en 450 ms, con 60 ms de retraso por índice hasta un máximo de 6.
  - `@Composable fun TagPill(text, accent: Boolean = false)`
  - `@Composable fun ServingsStepper(value: Int, onChange: (Int) -> Unit, range: IntRange = 1..24)`: muestra «4 porciones» o «1 porción».
  - `@Composable fun AnimatedCheck(checked: Boolean)`: la marca aparece con escala y rebote.
  - `@Composable fun PhotoOrPlaceholder(fileName: String?, photoStore: PhotoStore, emoji: String, modifier)`: si el archivo es `null` o no existe, muestra un degradado con el emoji de la categoría.
  - Todo respeta `LocalReducedMotion`.

- [ ] **Paso 1: escribir las pruebas.**
  - `flavorPolygon`: [3,0,0,0,0] con centro (60,60) y radio 46 → el primer punto es (60,14) ±0,01.
  - `FlavorWheel_hasSpanishContentDescription`
  - `ServingsStepper_singularPlural`
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: commit:** «Añadir rueda de sabores y componentes animados».

### Tarea 9: Navegación, Inicio, lista de recetas y editor

**Archivos:**
- Crear: `navigation/{Routes.kt,AppNavHost.kt,BottomBar.kt}`, `home/{HomeScreen.kt,HomeViewModel.kt}`, `recipes/list/{RecipesScreen.kt,RecipesViewModel.kt}`, `recipes/editor/EditorScreen.kt`, `pairing/PairingPlaceholderScreen.kt`, `res/xml/file_paths.xml`
- Modificar: `MainActivity.kt` (`RecetarioTheme` + `AppNavHost`), `AndroidManifest.xml` (`FileProvider` para la cámara)
- Pruebas: `navigation/NavigationTest.kt`, `recipes/list/RecipesScreenTest.kt`, `recipes/editor/EditorScreenTest.kt`

**Interfaces:**
- Consume: las Tareas 3 a 8.
- Produce las rutas `@Serializable`:
  - `Home`, `Recipes`, `Shopping`, `Pairing` (pestañas)
  - `RecipeDetail(val id: String)`
  - `RecipeEditor(val id: String? = null, val categoryId: String? = null)`
  - `Cooking(val recipeId: String, val servings: Int)`
  - `Converter`, `Settings`, `Appearance`, `Categories`, `Tags`, `Backup`, `About`
- Produce `SharedTransitionLayout` envolviendo el `NavHost`, con la clave compartida `"photo-$recipeId"` para la transición de la tarjeta al detalle.
- Barra inferior: «Inicio», «Recetas», «Compra» y «Maridaje», con los iconos del boceto. Botón flotante «+» en Inicio y Recetas, que se encoge y gira 90° al pulsarlo.
- **Inicio** (etapa 1):
  - Saludo «Buenos días» (6:00–11:59), «Buenas tardes» (12:00–19:59) o «Buenas noches» (resto), con el título «¿Qué cocinamos hoy?».
  - Chips de categorías que abren Recetas filtrado, «Recientes» (6, por `updatedAt`) y «Favoritas».
  - Los iconos de Ajustes y Apariencia de la cabecera se añaden en la Tarea 12.
  - Estado vacío: «Aún no tienes recetas» con el botón «Añadir la primera».
- **Recetas:** buscador, chips de categoría, hoja de filtros (todos los de `RecipeFilter`), orden y cuadrícula o lista. Al reordenar o filtrar, los elementos se animan con `Modifier.animateItem()`. La acción «Conversor» se añade en la Tarea 14.
- **Maridaje:** marcador de posición con el texto «Muy pronto: descubre qué bebida, plato o postre combina con cada receta.»
- **Editor:** según §8.1 del spec, con secciones plegables «Lo básico», «Sabor», «Ingredientes», «Pasos» y «Notas».
  - Sabor: chips que suben de 0 a 3 a cada toque, una mini `FlavorWheel` y los atajos «Dulce» y «Salada».
  - Ingredientes y pasos reordenables con `sh.calvin.reorderable`, con sección opcional de ingredientes («Para la salsa»).
  - Cada paso muestra chips con los ingredientes que usa: los sugiere `suggestStepIngredients` y el usuario los activa o desactiva.
  - Foto de portada y hasta 4 por paso, con cámara (`TakePicture` con FileProvider) o galería (`PickVisualMedia`).
  - Al detectar un tiempo en el texto de un paso aparece el chip «⏱ Añadir temporizador de 20 min».
  - Cuadro de diálogo «¿Recuperar el borrador?».

- [ ] **Paso 1: escribir las pruebas Compose (Robolectric).**
  - `bottomBar_navigatesBetweenTabs`
  - `emptyHome_showsAddFirst_andOpensEditor`
  - `search_limon_showsOnlyLimonada`
  - `categoryChip_filters`
  - `editor_createRecipe_appearsInList`: rellena el título, elige Bebidas, añade un ingrediente con «1,5» y guarda.
  - `longTitle_isEllipsizedInCard`: con 120 caracteres el nodo se muestra y la prueba no lanza error. *(Foco de revisión 5.)*
  - `detailWith60Ingredients_scrollsToLast` va en la Tarea 11.
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: verificación visual.** Hacer capturas con Robolectric (`captureRoboImage` o `onRoot().captureToImage()`) de Inicio y del editor en los 3 temas, y compararlas con los bocetos.
- [ ] **Paso 6: commit:** «Añadir navegación, Inicio, lista de recetas y editor».

### Tarea 10: Lista de compra

**Archivos:**
- Crear: `shopping/{ShoppingModels.kt,SectionClassifier.kt,ShoppingMerge.kt,ShoppingDao.kt,ShoppingRepository.kt,ShoppingViewModel.kt,ShoppingScreen.kt}`
- Modificar: `AppDatabase.kt` (tabla `shopping_items`; sigue siendo la versión 1 porque aún no se ha publicado ninguna), `AppContainer` (añadir `shoppingRepository`), `AppNavHost.kt` (destino `Shopping`)
- Pruebas: `SectionClassifierTest.kt`, `ShoppingMergeTest.kt`, `ShoppingRepositoryTest.kt`

**Interfaces:**
- Produce:
  - `enum class ShopSection(val label: String) { FRUITS_VEG("Frutas y verduras"), DAIRY("Lácteos"), MEAT_FISH("Carnes y pescados"), PANTRY("Despensa"), DRINKS("Bebidas"), OTHER("Otros") }`
  - `data class ShoppingItem(val id: String, val name: String, val quantity: Double?, val unit: MeasureUnit, val customUnit: String?, val section: ShopSection, val checked: Boolean, val sourceRecipeId: String?, val createdAt: Instant)`
  - `object SectionClassifier { fun classify(name: String): ShopSection }`: diccionario de unas 150 palabras clave. Se mira **primero la primera palabra** del nombre normalizado y después el nombre completo; si no hay coincidencia, OTHER.
  - `fun mergeInto(existing: List<ShoppingItem>, incoming: List<ShoppingItem>): List<ShoppingItem>`:
    - se funde con una línea **sin marcar** del mismo nombre normalizado (comparando también sin «s» final) y el mismo tipo de unidad;
    - las masas se suman en g y los volúmenes en ml, convirtiendo con `UnitConverter` (Tarea 5). `ShoppingRepository` recibe `cupMl: Flow<Int>`: en esta tarea es `flowOf(240)` y la Tarea 12 lo conecta a los ajustes;
    - lo contable, si la unidad es la misma;
    - `PINCH` y `TO_TASTE` se quedan en una sola línea sin cantidad;
    - si no se pueden fundir, va una línea nueva.
  - `interface ShoppingRepository`:
    - `observe(): Flow<Map<ShopSection, List<ShoppingItem>>>`
    - `suspend fun addFromRecipe(recipe: Recipe, servings: Int, excludeIngredientIds: Set<String>): Int`
    - `suspend fun addManual(name: String, quantity: Double?, unit: MeasureUnit)`
    - `suspend fun toggle(id)`, `suspend fun delete(id)`, `suspend fun clearChecked()`
    - `fun asShareText(items): String`
- Pantalla:
  - lista agrupada por sección con cabeceras;
  - casilla animada; deslizar para borrar con «Deshacer»;
  - «Vaciar marcados», compartir y añadir a mano (nombre, cantidad con `parseQuantity` y unidad);
  - estado vacío: «Tu lista está vacía. Desde una receta, pulsa «Lo que necesito».»

- [ ] **Paso 1: escribir las pruebas.**
  - `classify`: «Calabaza» → FRUITS_VEG; «Crema / nata» → DAIRY; «Caldo de verduras» → PANTRY (manda la primera palabra); «Pechuga de pollo» → MEAT_FISH; «Agua mineral» → DRINKS; «Xyz» → OTHER.
  - `mergeInto`:
    - 800 g de calabaza + 1 kg de calabaza → una línea de 1800 g;
    - «Cebolla» 1 + «cebollas» 2 → 3;
    - 200 ml de leche + 1 taza de leche → 440 ml;
    - 2 dientes de ajo + 100 g de ajo → dos líneas;
    - sal al gusto ×2 → una línea;
    - si la línea existente está marcada, se crea una nueva sin marcar.
  - `addFromRecipe_scalesAndExcludes`: 6 porciones, excluyendo 1 → 7 artículos con las cantidades escaladas.
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: commit:** «Añadir lista de compra con fusión y secciones».

### Tarea 11: Detalle de receta y compartir como texto

**Archivos:**
- Crear: `recipes/detail/{DetailScreen.kt,DetailViewModel.kt,RecipeTextFormatter.kt}`
- Pruebas: `RecipeTextFormatterTest.kt`, `DetailViewModelTest.kt`, `DetailScreenTest.kt`

**Interfaces:**
- Consume: `RecipeRepository`, `formatQuantity`/`scaleQuantity` y `ShoppingRepository.addFromRecipe(...)` (Tarea 10).
- Produce:
  - `fun formatRecipeAsText(recipe: Recipe, servings: Int): String`, con este formato exacto:
    ```
    🍲 Crema de calabaza
    ⏱ 35 min · 4 porciones · Fácil

    Ingredientes
    • Calabaza — 800 g
    • Sal y pimienta — al gusto

    Pasos
    1. Pela la calabaza…
    2. …

    Compartido desde mi Recetario
    ```
    El emoji es el de la categoría. Las líneas que faltan (tiempo o dificultad) se omiten sin dejar el separador.
  - `class DetailViewModel(id, repo, shopping, photoStore)`:
    - `state: StateFlow<DetailUiState>`, con la receta, `servings`, `checkedIngredientIds` y las cantidades formateadas;
    - `fun setServings(Int)`, `fun toggleIngredient(id)`, `suspend fun sendMissingToShopping(): Int`, `fun toggleFavorite()`, `suspend fun delete()`.
- Pantalla, como el boceto:
  - cabecera con foto (transición compartida), «←» y «♡» animado;
  - título, pills y `FlavorWheel`;
  - ingredientes con `ServingsStepper` y casillas;
  - botón «🛒 Lo que necesito → a la compra», con el mensaje «✓ N ingredientes añadidos a tu lista de compra»;
  - pasos con miniaturas y «Mis notas»;
  - menú con Editar, Compartir y Borrar (borrar pide confirmación);
  - botón fijo «▶ Modo cocina», que lleva a `Cooking(id, servings)`.
- Al cambiar las porciones, cada cantidad que cambia destella en el color de acento durante 300 ms.
- Tocar una cantidad abre una hoja con sus conversiones; se conecta en la Tarea 14.

- [ ] **Paso 1: escribir las pruebas.**
  - `formatRecipeAsText` con la receta del boceto, comparando el texto exacto.
  - `setServings_6_updatesFormatted` → «1,2 kg».
  - `sendMissingToShopping_excludesChecked_returnsCount`: 8 ingredientes con 1 marcado → 7.
  - `delete_removesRecipe`
  - `missingCoverFile_showsPlaceholder_noCrash` *(foco de revisión 2)*
  - `detailWith60Ingredients_scrollsToLast` *(foco de revisión 5)*
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: commit:** «Añadir detalle de receta y compartir como texto».

### Tarea 12: Ajustes, apariencia, categorías y etiquetas

**Archivos:**
- Crear: `settings/{SettingsRepository.kt,SettingsScreen.kt,AppearanceScreen.kt,CategoriesScreen.kt,TagsScreen.kt,AboutScreen.kt}`, `core/ui/theme/ThemeReveal.kt`
- Modificar: `MainActivity.kt` (el tema sale de `resolveTheme(settings, ahora, sistemaOscuro)` y se reevalúa cada minuto), `AppContainer` (añadir `settingsRepository` y pasar su `cupMl` a `ShoppingRepository`), `AppNavHost.kt` (destinos de ajustes), `HomeScreen.kt` (iconos de Ajustes y Apariencia en la cabecera)
- Pruebas: `SettingsRepositoryTest.kt`, `AppearanceScreenTest.kt`

**Interfaces:**
- Produce `class SettingsRepository(dataStore: DataStore<Preferences>)`:
  - `themeSettings: Flow<ThemeSettings>`, `suspend fun setTheme(choice)`, `setAuto(Boolean)`, `setDayTheme(choice)` (solo CALIDO o FRESCO), `setNightWindow(start, end)`
  - `cupMl: Flow<Int>` (240 por defecto; 240 o 250) y `setCupMl`
  - `data class BackupStatus(val lastLocal: Instant? = null, val lastExternal: Instant? = null, val driveUri: String? = null, val lastError: String? = null)`, con `backupStatus: Flow<BackupStatus>` y `suspend fun updateBackupStatus(transform: (BackupStatus) -> BackupStatus)` (los usan las Tareas 15 y 16)
- Produce `@Composable fun ThemeRevealHost(choice: ThemeChoice, origin: Offset?, content: @Composable () -> Unit)`:
  - al cambiar `choice`, guarda el fotograma anterior con `rememberGraphicsLayer()` y dibuja el contenido nuevo recortado por un círculo que crece desde `origin` en 450 ms;
  - sin `origin` o con movimiento reducido, hace un fundido de 200 ms.
- Pantallas:
  - **Ajustes:** Apariencia, Unidades (taza de 240 o 250 ml), Categorías, Etiquetas, Copias de seguridad (Tareas 15 y 16) y Acerca de.
  - **Apariencia:** como el boceto `apariencia.html`. Tres muestras («Cálido», «Fresco», «Nocturno») con «Aa» y punto de acento, interruptor «Automático» con el texto del boceto y horas de noche editables.
  - **Categorías:** crear y editar (nombre, emoji, color y tipo base) y borrar pidiendo a qué categoría pasar sus recetas. Las de serie no muestran «Borrar».
  - **Etiquetas:** renombrar y borrar.
  - **Acerca de:** versión, licencias de las fuentes (OFL) y créditos.

- [ ] **Paso 1: escribir las pruebas.**
  - `defaults_autoTrue_dayCalido_cup240`
  - `setTheme_persists`
  - `setDayTheme_nocturno_isRejected`
  - `appearance_tapFresco_changesThemeInHost`
  - `categories_deleteWithRecipes_asksReassign`
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: commit:** «Añadir ajustes, apariencia con transición circular, categorías y etiquetas».

### Tarea 13: Modo cocina (lista) y temporizadores

**Archivos:**
- Crear: `cooking/{CookingViewModel.kt,CookingScreen.kt,TimerManager.kt,TimerService.kt,KeepScreenOn.kt}`
- Modificar: `AndroidManifest.xml`:
  - permisos `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` y `VIBRATE`;
  - el servicio con `foregroundServiceType="specialUse"` y la propiedad `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = «cooking_timer».
- Modificar: `AppContainer` (añadir `timerManager`)
- Pruebas: `TimerManagerTest.kt`, `CookingViewModelTest.kt`, `CookingScreenTest.kt`

**Interfaces:**
- Produce:
  - `data class CookingTimer(val id: String, val label: String, val totalSeconds: Int, val endsAtElapsedMs: Long?, val remainingMsWhenPaused: Long?, val finished: Boolean)`
  - `class TimerManager(elapsedRealtime: () -> Long)`, único en toda la app:
    - `timers: StateFlow<List<CookingTimer>>`
    - `fun start(label: String, seconds: Int): String`, `pause(id)`, `resume(id)`, `addMinute(id)`, `cancel(id)`
    - `fun remainingMs(id, now: Long): Long`
    - `fun tick(now: Long)`: marca los terminados y emite `finishedEvents: SharedFlow<CookingTimer>`.
  - `TimerService`:
    - mientras haya temporizadores activos, mantiene una notificación en curso con el tiempo restante de cada uno;
    - al terminar uno, reproduce el sonido de alarma del sistema, vibra y publica la notificación «⏱ ¡Tiempo! — Paso 2: Sofríe la cebolla…»;
    - se detiene cuando ya no queda ninguno.
    - La primera vez se pide el permiso de notificaciones (Android 13 o superior).
  - `class CookingViewModel(savedStateHandle, repo, timerManager)`:
    - `currentIndex`, `doneSteps` e `checkedIngredients` viven en el `SavedStateHandle`;
    - `fun markDone()` avanza; `fun goTo(index)`; `fun toggleIngredient(id)`;
    - `val finished: Boolean`.
  - `@Composable fun KeepScreenOn()`: pone `FLAG_KEEP_SCREEN_ON` al entrar y lo quita al salir.
- Pantalla (boceto `receta-y-cocina.html`, opción B):
  - cabecera con ✕ y el título;
  - «Ingredientes» plegable con casillas y cantidades escaladas;
  - lista de pasos: los hechos atenuados y con ✓; el actual expandido con texto de 20 sp, carrusel de fotos, chips de ingredientes, botón de temporizador (estados: «⏱ Iniciar 5:00», «⏸ 4:58» que late, «✓ ¡Tiempo!», y +1 min) y «Hecho ✓»;
  - al marcar un paso, desplazamiento animado al siguiente;
  - al final, la tarjeta «🎉 ¡Listo!» con «Volver a la receta».

- [ ] **Paso 1: escribir las pruebas.**
  - `TimerManagerTest`, con un reloj falso:
    - `start_remainingCountsDown`: 300 s, y a +10 s quedan 290000 ms;
    - `pause_freezes_resume_continues`
    - `addMinute_extendsRunning`
    - `tick_afterEnd_emitsFinishedOnce`
    - `multipleTimers_independent`
  - `CookingViewModelTest`:
    - `markDone_advancesAndFinishesAtEnd`
    - `goTo_previousStep`
    - `state_survivesRecreation_viaSavedStateHandle` *(foco de revisión 4)*
  - `CookingScreenTest`:
    - `tapHecho_expandsNextStep`
    - `quantitiesUseServingsArg`: con servings 6 → «1,2 kg».
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: commit:** «Añadir modo cocina en lista con temporizadores en segundo plano».

### Tarea 14: Pantalla del conversor y conversiones desde la receta

**Archivos:**
- Crear: `converter/ConverterScreen.kt`
- Modificar: `recipes/detail/DetailScreen.kt` (hoja de conversiones al tocar una cantidad), `recipes/list/RecipesScreen.kt` (acción «Conversor» en la barra superior), `AppNavHost.kt` (destino `Converter`)
- Pruebas: `converter/ConverterScreenTest.kt`

**Interfaces:**
- Consume: `UnitConverter` y `Densities` (Tarea 5), con `cupMl` de `SettingsRepository` (Tarea 12).
- Pantalla: valor (con `parseQuantity`), unidad de origen, unidad de destino, ingrediente opcional y resultado formateado con `formatQuantity`.
  - Si falta densidad: «Para pasar de volumen a peso necesito saber el ingrediente (por ejemplo, harina).»
  - Si no se puede: «Esas unidades no se pueden convertir entre sí.»
- Hoja en el detalle: al tocar «1½ tazas» de «Harina» muestra sus equivalencias en g, ml y cucharadas cuando son posibles.

- [ ] **Paso 1: escribir las pruebas.**
  - `converter_1cupFlour_shows125g`
  - `converter_unknownIngredient_showsDensityMessage`
  - `detail_tapQuantity_opensConversionsSheet`
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: commit:** «Añadir pantalla del conversor y conversiones desde la receta».

### Tarea 15: Copia de seguridad manual (exportar e importar)

**Archivos:**
- Crear: `backup/{BackupModel.kt,BackupExporter.kt,BackupImporter.kt,BackupManager.kt,BackupScreen.kt}`
- Modificar: `AppContainer` (añadir `backupManager`), `AppNavHost.kt` (destino `Backup`)
- Pruebas: `BackupRoundTripTest.kt`, `BackupImporterTest.kt`

**Interfaces:**
- Produce:
  - `@Serializable data class BackupFile(val schemaVersion: Int = 1, val appVersion: String, val createdAt: String, val categories: List<…>, val recipes: List<…>, val shoppingItems: List<…>, val settings: BackupSettings)`:
    - `BackupSettings` contiene `themeSettings` y `cupMl`;
    - las recetas van completas, con ingredientes, pasos, fotos de paso, relaciones y etiquetas;
    - las fechas en ISO-8601.
  - Zip: `backup.json` + `images/<fileName>`.
  - `class BackupManager(db, photoStore, settings, clock)`:
    - `suspend fun export(out: OutputStream)`
    - `suspend fun import(input: InputStream, mode: ImportMode): ImportResult`
  - `enum class ImportMode { REPLACE_ALL, MERGE }`
  - `sealed interface ImportResult { data class Ok(val recipes: Int, val images: Int); data class Error(val message: String) }`
  - Reglas de importación:
    - **REPLACE_ALL:** primero hace una copia local automática (Tarea 16 `writeLocalBackup`; hasta entonces, en `filesDir/backups/antes-de-importar-<fecha>.zip`) y después sustituye todo en una sola transacción.
    - **MERGE:** inserta o actualiza por `id`; en conflicto gana el `updatedAt` más reciente; nunca borra.
    - **Validación:** si falta `backup.json` o el JSON no se lee → `Error("Este archivo no es una copia del Recetario.")`; si `schemaVersion` es mayor → `Error("Esta copia es de una versión más nueva de la app. Actualiza la app e inténtalo de nuevo.")`. En ambos casos **los datos no cambian**.
- Pantalla «Copias de seguridad»:
  - «Exportar copia» (`CreateDocument("application/zip")`, nombre `Recetario-AAAA-MM-DD.zip`);
  - «Importar copia» (`OpenDocument`), que pregunta «Reemplazar todo» (con una confirmación en rojo) o «Combinar».
  - Tras exportar a mano, se registra `lastExternal` con `SettingsRepository.updateBackupStatus`.

- [ ] **Paso 1: escribir las pruebas.**
  - `export_wipe_import_replace_restoresIdenticalData`: recetas, categorías propias, etiquetas, compra, ajustes e imágenes, comparando byte a byte.
  - `merge_newerUpdatedAtWins`
  - `merge_neverDeletesLocalOnly`
  - `corruptZip_returnsError_andDataUnchanged`
  - `newerSchema_returnsError_andDataUnchanged`
  - `replaceAll_writesSafetyBackupFirst`
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: commit:** «Añadir exportación e importación de copias de seguridad».

### Tarea 16: Copia semanal automática (teléfono y Google Drive) y recordatorio

**Archivos:**
- Crear: `backup/{AutoBackupWorker.kt,BackupRotation.kt,BackupReminder.kt}`, `res/xml/{backup_rules.xml,data_extraction_rules.xml}`
- Modificar:
  - `BackupManager.kt` (añadir `writeLocalBackup()` y `copyToExternal(uri)`);
  - `BackupScreen.kt` (sección Drive);
  - `HomeScreen.kt` (tarjeta de aviso);
  - `AndroidManifest.xml` (`allowBackup`, `fullBackupContent`, `dataExtractionRules`);
  - `RecetarioApp.kt` (programar el worker).
- Pruebas: `BackupRotationTest.kt`, `BackupReminderTest.kt`, `AutoBackupWorkerTest.kt` (con `work-testing`)

**Interfaces:**
- Produce:
  - `fun filesToDelete(files: List<String>, keep: Int = 4): List<String>`: nombres `Recetario-AAAA-MM-DD-HHmm.zip`; se conservan los 4 más recientes según el nombre.
  - `fun shouldRemindBackup(now: Instant, lastExternal: Instant?, firstRecipeAt: Instant?): Boolean`: avisa si `lastExternal` tiene más de 30 días, o si nunca la hubo y la primera receta tiene más de 30 días.
  - Usa `BackupStatus` y `updateBackupStatus` de la Tarea 12.
  - `suspend fun BackupManager.writeLocalBackup(): File`:
    - escribe en `filesDir/backups/`;
    - **verifica** que el zip abre y que `backup.json` se puede leer;
    - rota las copias.
  - `suspend fun BackupManager.copyToExternal(uri: Uri)`: abre con `openOutputStream(uri, "wt")` y copia la copia ya verificada.
  - `class AutoBackupWorker`: `PeriodicWorkRequest` de 7 días, `ExistingPeriodicWorkPolicy.KEEP`, con el nombre único «copia-semanal».
    - Primero `writeLocalBackup()`; después, si existe `driveUri`, `copyToExternal`.
    - Registra `lastLocal`, `lastExternal` o `lastError` («No se pudo guardar en Google Drive. Vuelve a elegir el archivo.»).
- Sección Drive en «Copias de seguridad»:
  - «Guardar también en Google Drive» abre `CreateDocument` y pide `takePersistableUriPermission` de lectura y escritura;
  - muestra «Última copia en el teléfono: …» y «Última copia en Drive: …», con «Reintentar» y «Elegir otro archivo».
- Inicio muestra una tarjeta si `lastError != null` o si `shouldRemindBackup`.
- Reglas de copia de Android: incluir la base de datos y DataStore; excluir `files/photos/`, `files/backups/` y `files/drafts/`.

- [ ] **Paso 1: escribir las pruebas.**
  - `rotation_keepsNewest4`: 6 archivos → borra los 2 más antiguos.
  - `reminder`:
    - 31 días desde la última externa → true;
    - 29 días → false;
    - nunca hubo copia y la primera receta tiene 31 días → true;
    - sin recetas → false.
  - `worker_writesVerifiedLocal_andCopiesToDrive`: con una Uri falsa de un `ContentProvider` de prueba.
  - `worker_driveFails_recordsError_keepsLocal`
  - `localBackup_isNeverCopiedIfVerificationFails`
- [ ] **Pasos 2 a 4:** verlas fallar, implementar y verlas pasar.
- [ ] **Paso 5: commit:** «Añadir copia semanal en el teléfono y en Google Drive».

### Tarea 17: Cierre de la etapa 1

**Archivos:**
- Modificar: `CLAUDE.md` (estructura real de paquetes, comandos y problemas conocidos), `app/version.properties` (0.1.0), `docs/instalar.md` (repasar)

- [ ] **Paso 1: verificación completa.** Ejecutar `./gradlew clean testDebugUnitTest assembleDebug assembleRelease`. Resultado esperado: `BUILD SUCCESSFUL` con 0 pruebas fallidas. Anotar el número total de pruebas.
- [ ] **Paso 2: recorrido manual con Robolectric o capturas.** Comprobar, en los 3 temas:
  1. crear una receta con foto, 3 ingredientes («1,5» tazas de harina) y 2 pasos (uno con «20 min»);
  2. verla en Inicio y en Recetas, y buscarla sin tildes;
  3. abrir el detalle y cambiar a 6 porciones;
  4. «Lo que necesito»;
  5. modo cocina hasta «¡Listo!»;
  6. exportar e importar con «Combinar»;
  7. cambiar el tema con Automático.
  - Guardar las capturas en `docs/superpowers/plans/capturas-etapa-1/` y compararlas con los bocetos.
- [ ] **Paso 3: CI en verde** en la rama de trabajo. Comprobarlo en la pestaña Actions o con las herramientas de GitHub.
- [ ] **Paso 4: commit** («Cerrar la etapa 1: documentación y versión 0.1.0») y push.

---

## Fuera de esta etapa (no implementar ahora)

Hoy y clima, maridaje, despensa, diario, menú semanal, compartir como imagen, la copia previa a cada migración (llega con la primera migración, en la etapa 2), IA e importar desde enlace.
