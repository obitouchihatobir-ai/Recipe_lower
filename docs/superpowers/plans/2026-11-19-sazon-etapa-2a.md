# Sazón · Entrega 2A (diario y despensa) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** publicar la 0.1.0 oficial y después la 0.2.0, con diario, despensa, la primera migración de la base de datos (con copia previa) y copias de seguridad v2.

**Arquitectura:**
- La lógica pura (estadísticas del diario, coincidencias con la despensa, orden y filtros nuevos, rotación) va en `logic/`, con TDD local.
- Room pasa a la versión 2 con **todas** las tablas de la etapa 2 en una sola `MIGRATION_1_2` que solo crea tablas.
- Antes de abrir Room, `DatabaseUpgradeGuard` copia la base de datos tal cual.
- Las pantallas siguen el patrón `XxxRoute`/`XxxContent` de la etapa 1.

**Tecnología:** Kotlin, Compose, Room (`room-testing` para `MigrationTestHelper`), DataStore y kotlinx.serialization. Sin bibliotecas nuevas salvo `androidx.room:room-testing` en las pruebas.

**Diseño:** `docs/superpowers/specs/2026-11-19-sazon-etapa-2-design.md` (§5, §6 y §7.1–7.2). También se lee el diseño general `docs/superpowers/specs/2026-11-13-recetario-design.md` y `CLAUDE.md`.

## Restricciones globales

- **Interfaz y código:**
  - interfaz en español; identificadores en inglés; mensajes de commit en español con las líneas `Co-Authored-By` y `Claude-Session` de la sesión;
  - contraste ≥ 4,5:1 sin atenuar con transparencia; áreas táctiles ≥ 48 dp;
  - animaciones breves, que no bloquean el toque y respetan `LocalReducedMotion`.
- **Base de datos:**
  - **nunca** migraciones destructivas;
  - `MIGRATION_1_2` solo hace `CREATE TABLE` y `CREATE INDEX`;
  - sin `fallbackToDestructiveMigration`.
- **Copias:**
  - el archivo previo es `antes-de-actualizar-v<vieja>-<AAAA-MM-DD-HHmm>[-N].db` en `filesDir/backups/`; se conservan 2;
  - si la copia falla, **no se migra**.
- **`BACKUP_SCHEMA_VERSION` = 2.** Las copias v1 se importan; las v3 se rechazan con `NEWER_BACKUP`.
- **Básicos de la despensa por defecto:** sal, agua, aceite, pimienta, azúcar.
- **Versión:** `versionName=0.2.0`.
- **Repositorio público:** ninguna ubicación ni dato personal en el código, las pruebas ni las capturas.
- **`:app` se prueba solo en CI:** push a `ccr-3fe56a79-p1q16y` y `wait-ci.sh`. `logic/` se prueba en local con `./gradlew -p logic test`.

## Puntos de atención para la revisión

1. **Instalar 0.2.0 encima de 0.1.0 con datos reales.** Lo esperable: todo sigue igual, existe una `antes-de-actualizar-v1-…db` y la app no vuelve a migrar ni a copiar en el siguiente arranque. → Prueba en la Tarea 3: `guard_copiesOnlyOnce_thenOpensNormally`.
2. **Disco lleno al copiar.** Lo esperable: pantalla de error, sin migrar y sin cerrarse en bucle; la base de datos v1 queda intacta. → Prueba en la Tarea 3: `guard_copyFails_doesNotMigrate`.
3. **Borrar una receta que tiene diario.** Lo esperable: las entradas siguen, con el título guardado, y sus fotos no se borran. → Prueba en la Tarea 4: `deleteRecipe_keepsLogEntriesAndPhotos`.
4. **Importar una copia v1 (de la 0.1.0) en la 0.2.0**, con «Combinar» y con «Reemplazar todo». Lo esperable: se importa; diario y despensa quedan vacíos con «Reemplazar» e intactos con «Combinar». → Prueba en la Tarea 6: `importV1_replace_clearsNewTables` y `importV1_merge_keepsNewTables`.
5. **Ingrediente de la despensa que es parte de una palabra** («sal» frente a «salsa», «ajo» frente a «ajonjolí»). Lo esperable: no coincide, porque se comparan palabras completas. → Prueba en la Tarea 1: `pantryMatch_wholeWordsOnly`.

---

### Task 0: Publicar la 0.1.0 oficial (con el usuario; la hace el controlador, no un implementador)

- [ ] **Paso 1:** CI en verde en la punta de la rama.
- [ ] **Paso 2:** abrir un pull request de `ccr-3fe56a79-p1q16y` a `main`, titulado «Etapa 1 de Sazón (0.1.0)». Pedir al usuario su confirmación y fusionarlo solo con ella. `main` es un antecesor directo de la rama, así que es un avance rápido.
- [ ] **Paso 3:** guiar al usuario, con `docs/instalar.md`:
  1. crear el secreto `SIGNING_PASSPHRASE`, de 20 caracteres o más, inventado por él y guardado en un lugar seguro;
  2. lanzar «Crear llave de firma» en Actions, sobre `main`;
  3. comprobar que «Publicar APK» publica `v0.1.0` con `Sazon-0.1.0.apk`.
- [ ] **Paso 4:** guiar el paso de los datos:
  1. «Exportar copia» en el APK de prueba;
  2. desinstalarlo;
  3. instalar `Sazon-0.1.0.apk` desde Releases;
  4. «Importar copia» con «Reemplazar todo».
- [ ] **Paso 5:** anotar en el registro de avance la versión publicada.

### Task 1: Lógica del diario y de la despensa (`logic/`)

**Archivos:**
- Crear:
  - `logic/src/main/kotlin/app/recetario/diary/CookLog.kt`
  - `logic/src/main/kotlin/app/recetario/diary/CookStats.kt`
  - `logic/src/main/kotlin/app/recetario/pantry/PantryMatch.kt`
- Modificar: `logic/src/main/kotlin/app/recetario/recipes/search/RecipeFilter.kt`
- Pruebas:
  - `logic/src/test/kotlin/app/recetario/diary/CookStatsTest.kt`
  - `logic/src/test/kotlin/app/recetario/pantry/PantryMatchTest.kt`
  - `RecipeFilterTest.kt`, que ya existe

**Interfaces que produce:**
- `data class CookLogEntry(val id: String, val recipeId: String?, val recipeTitle: String, val date: LocalDate, val rating: Int?, val note: String, val photos: List<String>, val createdAt: Instant, val updatedAt: Instant)`
  - `rating` es null o va de 1 a 5; `photos` tiene 4 como máximo.
- `data class CookStats(val count: Int, val lastDate: LocalDate?, val averageRating: Double?)`
- `fun statsByRecipe(entries: List<CookLogEntry>): Map<String, CookStats>`
  - ignora las entradas con `recipeId` null;
  - la media solo cuenta las entradas valoradas.
- `fun roundToHalfStar(avg: Double): Double`: 3,74 → 3,5 y 3,75 → 4,0.
- `fun cookStatsLine(stats: CookStats?, today: LocalDate): String`
  - «Aún no la has hecho»;
  - «La hiciste 1 vez · hoy»;
  - «La hiciste 3 veces · última hace 2 semanas · ★★★★☆», con media estrella ⯪ si hace falta;
  - sin valoraciones, la línea acaba tras la fecha;
  - fechas relativas: «hoy», «ayer», «hace N días» (2–13), «hace N semanas» (2–8), «hace N meses» (2–11), «hace más de un año».
- `fun longTimeNoCook(stats: Map<String, CookStats>, today: LocalDate, minDays: Int = 30, limit: Int = 6): List<String>`: ids, el más antiguo primero.
- `val DEFAULT_PANTRY_BASICS = listOf("sal", "agua", "aceite", "pimienta", "azúcar")`
- `fun pantryCovers(pantryName: String, ingredientName: String): Boolean`
  - compara con `normalize` y, además, singular y plural básico (`-es`/`-s`);
  - coincide si todas las palabras de uno están, como palabras completas, en el otro.
- `data class PantryCoverage(val recipeId: String, val have: Int, val total: Int, val missing: List<String>)`
- `fun pantryCoverage(recipes: List<RecipeSummary>, pantry: List<String>, basics: List<String>): List<PantryCoverage>`
  - sin las recetas con `total == 0`;
  - ordenado por `have/total` descendente y luego por título normalizado.
- `RecipeFilter`:
  - nuevo campo `pantryOnly: Boolean = false`;
  - `SortOrder` añade `MOST_COOKED` y `BEST_RATED`;
  - `applyFilter(f, stats: Map<String, CookStats> = emptyMap(), pantry: List<String> = emptyList(), basics: List<String> = DEFAULT_PANTRY_BASICS)`.

- [ ] **Paso 1: pruebas en rojo.**
  - `CookStatsTest`:
    - `stats_countLastAndAverage`: entradas con valoración 5, 4 y null → `count` 3 y media 4,5;
    - `stats_ignoresOrphanEntries`;
    - `line_noEntries` → «Aún no la has hecho»;
    - `line_threeWithRating` → «La hiciste 3 veces · última hace 2 semanas · ★★★★☆», con 14 días y media 4,2;
    - `line_halfStar`, media 3,75 → «★★★★☆»; media 3,4 → «★★★⯪☆»;
    - `relativeDates` (tabla con los cortes de arriba);
    - `longTimeNoCook_onlyCookedOlderThan30_oldestFirst_max6`.
  - `PantryMatchTest`:
    - `pantryMatch_wholeWordsOnly`: «sal» no cubre «salsa de tomate» y «ajo» no cubre «ajonjolí»;
    - `pantryMatch_plurals`: «tomates» cubre «tomate saladet»;
    - `pantryMatch_accents`: «limón» cubre «limon»;
    - `coverage_excludesBasics`;
    - `coverage_order`;
    - `coverage_missingNames`;
    - `coverage_skipsEmptyRecipes`.
  - En `RecipeFilterTest`:
    - `pantryOnly_requiresAllNonBasic`;
    - `sort_mostCooked_tiesByTitle`;
    - `sort_bestRated_unratedLast`.
- [ ] **Paso 2:** `./gradlew -p logic test`: FAIL (referencias sin resolver).
- [ ] **Paso 3:** implementar las interfaces de arriba.
- [ ] **Paso 4:** `./gradlew -p logic test`: PASS, con todas las anteriores en verde.
- [ ] **Paso 5:** commit «Lógica del diario y de la despensa».

### Task 2: Room versión 2 y `MIGRATION_1_2`

**Archivos:**
- Crear: `app/src/main/java/app/recetario/core/data/Migrations.kt` y entidades y DAOs en `diary/data/`, `pantry/data/`, `plan/data/` y `pairing/data/`.
- Modificar: `AppDatabase.kt` (versión 2, `.addMigrations(MIGRATION_1_2)`) y `app/build.gradle.kts` (`room-testing` en `testImplementation` y `room.schemaLocation` como hoy).
- Generar: `app/schemas/app.recetario.core.data.AppDatabase/2.json`. Si no se puede compilar en local, se copia desde el paso de CI «Comprobar que el esquema…», como dice `CLAUDE.md`.
- Prueba: `app/src/test/java/app/recetario/core/data/MigrationTest.kt`.

**Tablas** (diseño §6.1; nombres exactos):
- `cook_log`:
  - columnas `id` (TEXT, PK), `recipeId` (TEXT, null), `recipeTitle`, `date` (TEXT ISO), `rating` (INTEGER, null), `note`, `createdAt` y `updatedAt`;
  - FK a `recipes(id)` con `ON DELETE SET NULL`;
  - índice `(recipeId, date)`.
- `cook_log_photos`:
  - columnas `logId`, `position` y `fileName`; PK `(logId, position)`;
  - FK a `cook_log(id)` con `ON DELETE CASCADE`.
- `pantry_items`: `id` (PK), `name`, `normalized` (con índice único) y `addedAt`.
- `meal_plan`:
  - columnas `id` (PK), `date`, `slot` (TEXT), `recipeId` y `servings`;
  - índice único `(date, slot)` e índice `(date)`;
  - FK a `recipes(id)` con `ON DELETE CASCADE`.
- `pairing_ratings`:
  - columnas `pairKey` (PK), `aKind`, `aId`, `bKind`, `bId`, `liked` (INTEGER) y `ratedAt`;
  - índices `(aId)` y `(bId)`;
  - sin FK; el borrado lo hace el repositorio de recetas en la Tarea 4.

**Interfaces que produce:**
- `DiaryDao`, `PantryDao`, `MealPlanDao` y `PairingRatingDao`, con al menos `observeAll`, `upsert`, `delete` y `deleteAll` (los usa la Tarea 6).
- `AppDatabase.diaryDao()`, `pantryDao()`, `mealPlanDao()` y `pairingRatingDao()`.

- [ ] **Paso 1: prueba en rojo `MigrationTest`.**
  - `migrate1to2_keepsAllData`: crea la v1 con `MigrationTestHelper`, con una receta (ingredientes, pasos, foto de paso, etiqueta), una categoría propia y un artículo de compra. Tras `runMigrationsAndValidate(2, true, MIGRATION_1_2)`, todas las filas siguen iguales, columna por columna.
  - `migrate1to2_createsEmptyNewTables`.
  - `migrate1to2_onlyCreates`: el SQL de `MIGRATION_1_2` no contiene `DROP`, `ALTER` ni `DELETE`.
- [ ] **Paso 2:** push y CI: FAIL.
- [ ] **Paso 3:** implementar las entidades, los DAOs, `MIGRATION_1_2` y `2.json`.
- [ ] **Paso 4:** CI: PASS, con todas las pruebas anteriores en verde.
- [ ] **Paso 5:** commit «Base de datos v2: tablas de la etapa 2 y migración 1→2».

### Task 3: Copia previa a la migración

**Archivos:**
- Crear:
  - `app/src/main/java/app/recetario/core/data/DatabaseUpgradeGuard.kt`
  - `app/src/main/java/app/recetario/core/data/UpgradeErrorScreen.kt`
- Modificar:
  - `logic/.../backup/BackupTexts.kt`: `LocalBackupKind.BEFORE_UPDATE("Antes de actualizar")`, su nombre y su lectura;
  - `logic/.../backup/BackupRotation.kt`: `BEFORE_UPDATE` conserva los 2 más recientes, `UPDATE_COPIES_KEPT = 2`;
  - `RecetarioApp.kt`/`AppContainer`: la base de datos se abre después de la guarda;
  - `MainActivity.kt`: si la guarda falla, muestra la pantalla de error;
  - `BackupManager.kt` y `BackupScreen.kt`: lista, «Guardar fuera» y «Restaurar» para las `.db`.
- Pruebas:
  - `logic/.../backup/BackupRotationTest.kt` y `BackupTextsTest.kt`, que ya existen;
  - `app/src/test/.../core/data/DatabaseUpgradeGuardTest.kt`.

**Interfaces que produce:**
- `class DatabaseUpgradeGuard(dbFile: File, backupsDir: File, clock: Clock, targetVersion: Int = AppDatabase.VERSION)` con `fun prepare(): GuardResult`.
  - `GuardResult` es `Ready`, `Copied(fileName)` o `Failed(message)`.
  - Si el archivo no existe o `user_version >= targetVersion`, devuelve `Ready`.
  - Si no:
    1. abre con `SQLiteDatabase.openDatabase(… OPEN_READWRITE)`;
    2. ejecuta `PRAGMA wal_checkpoint(TRUNCATE)` y cierra;
    3. copia a `.tmp` con fsync, la abre en modo de solo lectura y comprueba `PRAGMA integrity_check = ok`, y renombra sin pisar (`-2`, `-3`…);
    4. aplica la rotación de `BEFORE_UPDATE`.
  - Ante cualquier excepción, borra el `.tmp` y devuelve `Failed`.
- `BackupTexts.UPDATE_COPY_FAILED = "No hay espacio para hacer la copia de seguridad antes de actualizar. Libera espacio y vuelve a abrir Sazón."`
- `BackupTexts.MIGRATION_FAILED = "No se pudo actualizar la base de datos. Tus recetas siguen guardadas en la copia previa."`
- `BackupManager.restoreBeforeUpdate(fileName): BackupResult`:
  1. escribe la copia previa a importar habitual;
  2. cierra la base de datos;
  3. sustituye `recetario.db`, borrando `-wal` y `-shm`;
  4. pide reiniciar el proceso con `ProcessPhoenix`-like: `Intent` a `MainActivity` con `FLAG_ACTIVITY_CLEAR_TASK` más `Runtime.exit`, en una función inyectable para las pruebas.

- [ ] **Paso 1: pruebas en rojo.**
  - Lógica:
    - `parse_beforeUpdate` (`antes-de-actualizar-v1-2026-11-20-0915.db`);
    - `rotation_beforeUpdate_keeps2`;
    - `rotation_otherKindsUntouched`.
  - `DatabaseUpgradeGuardTest` (Robolectric, con una v1 real creada con `MigrationTestHelper`):
    - `guard_noDatabase_ready`;
    - `guard_v1_copiesThenReady`: la copia se abre y tiene las recetas;
    - `guard_copiesOnlyOnce_thenOpensNormally`: tras migrar, un segundo `prepare()` da `Ready` y no crea otra copia;
    - `guard_copyFails_doesNotMigrate`: `backupsDir` es un archivo, no una carpeta, así que da `Failed`, la base de datos sigue en `user_version` 1 y no hay `.tmp`;
    - `guard_keepsTwoNewest`.
  - Pruebas de pantalla:
    - `upgradeError_showsMessageAndSaveOutside`;
    - `backupList_showsBeforeUpdateEntry`;
    - `restoreBeforeUpdate_replacesDatabase` (con el reinicio falso).
- [ ] **Paso 2:** FAIL, en local la lógica y en CI la app.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** PASS en local y en CI.
- [ ] **Paso 5:** commit «Copia de la base de datos antes de actualizar».

### Task 4: Diario en la app (repositorio, «¡Listo!», detalle y pantalla Diario)

**Archivos:**
- Crear:
  - `app/src/main/java/app/recetario/diary/{DiaryRepository.kt,DiaryScreen.kt,DiaryViewModel.kt,LogEntryPanel.kt}`
- Modificar:
  - `cooking/CookingParts.kt`: tarjeta «Guardar en el diario» bajo «¡Listo!»;
  - `recipes/detail/*`: la línea de estadísticas, «Lo hice» y el historial en un `BottomPanel`;
  - `recipes/data/RecipeRepository.kt`: `isPhotoReferenced` también mira `cook_log_photos`, y borrar una receta borra sus `pairing_ratings`;
  - `home/*`: la sección «Hace tiempo que no la haces» y un acceso al Diario en la cabecera;
  - `recipes/list/FiltersSheet.kt`: las órdenes nuevas;
  - `navigation/Routes.kt` y `AppNavHost.kt`: destino `Diary`;
  - `AppContainer`: `diaryRepository`.
- Pruebas:
  - `app/src/test/.../diary/DiaryRepositoryTest.kt`
  - `DiaryScreenTest.kt`
  - `CookingDiaryTest.kt`
  - `DetailDiaryTest.kt`
  - la ampliación de `HomeScreenTest`

**Interfaces que produce:**
- `interface DiaryRepository`:
  - `fun observeEntries(): Flow<List<CookLogEntry>>`;
  - `fun observeStats(): Flow<Map<String, CookStats>>`;
  - `suspend fun save(entry: CookLogEntry)`, que fija `updatedAt` con el reloj;
  - `suspend fun delete(id: String): CookLogEntry?`, que devuelve la entrada para «Deshacer»;
  - `suspend fun restore(entry: CookLogEntry)`.
- Las fotos usan `PhotoStore`, como las de los pasos. Borrar una entrada borra sus fotos si nadie más las usa, pero solo después de que caduque «Deshacer» (4 s) o al salir de la pantalla.

**Comportamiento** (diseño §7.1):
- La tarjeta de «¡Listo!» tiene estrellas opcionales, nota, «Añadir foto» (cámara o galería, como el editor), «Guardar» y «Ahora no». Guardar muestra «✓ Guardado en el diario», y ninguna de las dos acciones bloquea «Volver a la receta».
- «Lo hice» en el detalle abre `LogEntryPanel` con la fecha editable (hoy por defecto; no se permiten fechas futuras).
- El Diario agrupa por meses («noviembre de 2026»), de más reciente a más antiguo. Una entrada sin receta muestra el título guardado y no se puede tocar para ir al detalle.

- [ ] **Paso 1: pruebas en rojo.**
  - `DiaryRepositoryTest`:
    - `save_thenObserve`;
    - `deleteRecipe_keepsLogEntriesAndPhotos`: la entrada queda con `recipeId` null y la foto sigue en disco;
    - `photoReferenced_byLog`;
    - `deleteRecipe_removesItsPairingRatings`.
  - `CookingDiaryTest`:
    - `listo_showsDiaryCard_saveCreatesEntry`;
    - `listo_ahoraNo_hidesCard`.
  - `DetailDiaryTest`:
    - `detail_showsStatsLine`;
    - `loHice_createsEntryWithChosenDate`.
  - `DiaryScreenTest`:
    - `groupsByMonth_newestFirst`;
    - `delete_undo_restores`;
    - `orphanEntry_notClickable`.
  - `HomeScreenTest`:
    - `longTimeNoCook_sectionShowsOldCooked`;
    - `longTimeNoCook_hiddenWhenEmpty`.
  - `RecipesScreenTest`: `sort_mostCooked`.
- [ ] **Paso 2:** CI: FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** CI: PASS, con todo en verde.
- [ ] **Paso 5:** commit «Diario de cocina».

### Task 5: Despensa en la app

**Archivos:**
- Crear:
  - `app/src/main/java/app/recetario/pantry/{PantryRepository.kt,PantryScreen.kt,PantryViewModel.kt,WhatCanIMakeScreen.kt}`
- Modificar:
  - `shopping/ShoppingScreen.kt`: pestañas internas **Lista · Despensa · Semana**; «Semana» es un estado vacío «Llega pronto: el menú de la semana» hasta la 2C; también «ya lo tienes» y la acción «A la despensa» en el aviso al marcar;
  - `recipes/list/*`: el filtro «Con mi despensa»;
  - `settings/*`: «Despensa › Básicos», una lista editable guardada en `ajustes` (`pantryBasics`);
  - `AppContainer`: `pantryRepository`.
- Pruebas:
  - `PantryRepositoryTest`
  - `PantryScreenTest`
  - `WhatCanIMakeTest`
  - la ampliación de `ShoppingScreenTest`
  - `SettingsBasicsTest`

**Interfaces que produce:**
- `interface PantryRepository`:
  - `fun observe(): Flow<List<PantryItem>>`;
  - `suspend fun add(name: String): Boolean`, que es false si ya estaba (por `normalized`);
  - `suspend fun delete(id: String): PantryItem?`;
  - `suspend fun restore(item: PantryItem)`.
- `data class PantryItem(val id: String, val name: String, val normalized: String, val addedAt: Instant)`, en `logic/.../pantry/`.
- `SettingsRepository.pantryBasics: Flow<List<String>>` y `setPantryBasics(list)`.

**Comportamiento** (diseño §7.2):
- **Añadir:**
  - el autocompletado da hasta 6 sugerencias, de los ingredientes de todas las recetas, sin repetir y sin lo que ya está en la despensa;
  - Intro o «Añadir» guardan;
  - si ya estaba, el aviso dice «Ya está en la despensa».
- **«¿Qué puedo hacer?»**, un botón en la pestaña Despensa:
  - lista `pantryCoverage` con «Tienes 5 de 6 · falta: cilantro» (como mucho 3 faltas y «…»);
  - con todo disponible, «¡Tienes todo!».
- **«A la despensa»:** la acción del aviso al marcar como comprado, que dura 4 s.

- [ ] **Paso 1: pruebas en rojo.**
  - `PantryRepositoryTest`: `add_dedupByNormalized`.
  - `PantryScreenTest`:
    - `add_withSuggestion`;
    - `delete_undo`;
    - `alreadyThere_message`.
  - `WhatCanIMakeTest`: `ordersByCoverage_showsMissing`.
  - `ShoppingScreenTest`:
    - `itemInPantry_showsYaLoTienes`;
    - `check_offersToPantry_addsIt`.
  - `RecipesScreenTest`: `filter_conMiDespensa`.
  - `SettingsBasicsTest`: `editBasics_changesCoverage`.
- [ ] **Paso 2:** CI: FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** CI: PASS.
- [ ] **Paso 5:** commit «Despensa».

### Task 6: Copias de seguridad v2

**Archivos:**
- Modificar:
  - `logic/.../backup/BackupModel.kt`:
    - `BACKUP_SCHEMA_VERSION = 2`;
    - `BackupFile` añade `cookLog: List<BackupCookLog> = emptyList()`, `pantry: List<BackupPantryItem> = emptyList()`, `mealPlan: List<BackupMealPlan> = emptyList()` y `pairingRatings: List<BackupPairingRating> = emptyList()`;
    - `BackupSettings` añade `pantryBasics: List<String>? = null`, que con null deja los de por defecto;
    - `ImportData` y `photoNames` incluyen las fotos del diario;
    - validación de ids, fechas, `rating` 1–5, `slot` e `aKind`/`bKind`.
  - `BackupExporter.kt`, `BackupImporter.kt`, `BackupDao.kt` y `BackupManager.preview`. El resumen cuenta también las entradas del diario.
- Pruebas:
  - `BackupModelTest`, que ya existe;
  - `BackupRoundTripTest`;
  - `BackupImporterTest`.

**Reglas** (diseño §6.4):
- Las copias v1 se leen con listas vacías.
- **«Combinar»:**
  - diario por `id`, gana el `updatedAt` más reciente;
  - despensa por `normalized`;
  - menú: solo si `(date, slot)` está libre;
  - valoraciones por `pairKey`, gana el `ratedAt` más reciente;
  - nunca se borra nada.
- **«Reemplazar»:** sustituye todo.
- **Fotos:** las del diario cuentan como en uso para la limpieza posterior del importador.

- [ ] **Paso 1: pruebas en rojo.**
  - Lógica:
    - `decode_v1_hasEmptyNewLists`;
    - `decode_v3_isNewer`;
    - `validate_badRating_rejected`;
    - `validate_badSlot_rejected`;
    - `photoNames_includeDiary`.
  - App:
    - `roundTrip_v2_identical`, con todas las tablas y fotos del diario;
    - `importV1_replace_clearsNewTables`;
    - `importV1_merge_keepsNewTables`;
    - `merge_diaryNewerWins`;
    - `merge_pantryByNormalized`;
    - `merge_mealPlanSlotTaken_keepsLocal`;
    - `merge_ratingsNewerWins`;
    - `replace_keepsDiaryPhotosInUse`.
- [ ] **Paso 2:** FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** PASS en local y en CI.
- [ ] **Paso 5:** commit «Copias de seguridad v2 con diario, despensa, menú y valoraciones».

### Task 7: Cierre de la 2A (0.2.0)

**Archivos:**
- Modificar:
  - `app/version.properties` (`versionName=0.2.0`);
  - `CapturasTest` (Diario, Despensa, «¿Qué puedo hacer?», la tarjeta de «¡Listo!» y la copia «Antes de actualizar», en los 3 temas);
  - `CLAUDE.md` (estado, estructura, diario, despensa, migración, copia previa y copias v2);
  - `docs/instalar.md` (actualizar encima sin desinstalar);
  - `docs/superpowers/pendientes-del-proyecto.md`: quitar lo que ya entra en el diseño de la etapa 2.
- Crear: `app/src/test/java/app/recetario/RecorridoEtapa2Test.kt`. Sobre `MainActivity` recorre:
  1. crear una receta y cocinarla hasta «¡Listo!»;
  2. guardar en el diario con 4 estrellas;
  3. ver la línea en el detalle;
  4. añadir 2 ingredientes a la despensa;
  5. «¿Qué puedo hacer?»;
  6. filtro «Con mi despensa»;
  7. exportar e importar con «Combinar», sin cambios.
  - Guarda capturas en `app/build/capturas/recorrido-2a/`.

- [ ] **Paso 1:** escribir el recorrido y las capturas nuevas.
- [ ] **Paso 2:** CI en verde con el recuento de «Contar las pruebas» anotado.
- [ ] **Paso 3:** mirar las capturas del artefacto (con Read) y anotar diferencias visibles en el informe.
- [ ] **Paso 4:** commit «Cerrar la entrega 2A: versión 0.2.0» y push.
- [ ] **Paso 5** (controlador, con el usuario): revisión final de la entrega. Después, pull request a `main` y fusión **solo con la confirmación del usuario**. Comprobar que «Publicar APK» publica `v0.2.0`, y pedir al usuario que la instale **encima** de la 0.1.0 y confirme que sus recetas siguen ahí (Puntos de atención, 1).
