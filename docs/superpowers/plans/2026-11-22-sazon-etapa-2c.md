# Sazón · Entrega 2C (menú de la semana y fichas) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** publicar la 0.4.0. Trae el menú de la semana (pestaña Semana de Compra, «Rellenar con sugerencias» y «Generar lista de compra») y las fichas para compartir recetas en texto, imagen PNG y PDF, con formatos, diseños, opciones, vista previa y el giro de la vista previa. Antes, se arreglan las pruebas intermitentes que hicieron fallar la publicación de la 0.3.0.

**Arquitectura:**
- La lógica pura (semana, relleno, lista de la semana, maquetación de la ficha) va en `logic/`, con TDD en local.
- En `:app`:
  - el repositorio del menú, sobre la tabla `meal_plan`, que ya existe;
  - la pestaña Semana;
  - la pantalla Ficha, con vista previa en Compose, imagen capturada con `GraphicsLayer` y PDF con `PdfDocument` + `StaticLayout`, y compartir con `FileProvider`.
    - **Cambiado al implementar (Task 5):** no se captura con `GraphicsLayer`. Un solo `CardPainter` (`StaticLayout`) dibuja la vista previa (en un `Canvas` de Compose), el PNG y las páginas del PDF, y mide para la maquetación. El PDF va detrás de `CardPdfDocument`, sustituible en las pruebas (`FakePdfDocument`).
- **Sin cambios de esquema de Room ni de copias de seguridad.** `meal_plan` y su copia ya existen desde la 2A.

**Tecnología:** Kotlin, Compose, Room, DataStore y `android.graphics.pdf.PdfDocument`. Sin bibliotecas nuevas.

**Diseño:**
- `docs/superpowers/specs/2026-11-19-sazon-etapa-2-design.md` §7.6, §7.7 y §8 (el giro de la ficha).
- Diseño general `2026-11-13-recetario-design.md` §8.8.
- `CLAUDE.md`.

## Restricciones globales

- **Interfaz:**
  - textos en español e identificadores en inglés; commits en español con las líneas de la sesión;
  - contraste ≥ 4,5:1 sin transparencias en el texto; áreas táctiles ≥ 48 dp;
  - `LocalReducedMotion`;
  - TalkBack con textos equivalentes.
- **Datos:**
  - `meal_plan.slot` solo admite los valores de `MEAL_SLOTS` (`DESAYUNO`, `COMIDA`, `CENA`). Cualquier otro haría que todas las copias se rechazaran.
  - Hay un único plato por `(date, slot)`. **Ojo con la trampa de `@Upsert`:** al sustituir un hueco ocupado se reutiliza el id o se borra antes.
- **Semana:**
  - va de lunes a domingo; empieza en la semana actual;
  - «Rellenar» solo rellena huecos vacíos desde hoy hasta el domingo y ofrece «Deshacer»;
  - «Generar lista» cuenta desde hoy hasta el domingo.
- **Ficha:**
  - **PNG:** 1080 × 1080 (cuadrada), 1080 × 1920 (vertical) y 1500 × 1000 (tarjeta horizontal).
  - **PDF:** A4 (595 × 842 pt), Carta (612 × 792 pt) y tarjeta de 10 × 15 cm (283 × 425 pt), en vertical, con varias páginas si hace falta.
  - **Diseños:** Clásico, Minimalista, Foto grande y Tema. Todos con las fuentes incluidas en la app.
  - **Siempre van:** título, porciones (las del detalle) e ingredientes escalados.
  - **Opcionales:** foto, pasos, notas y sabor.
  - **Pie:** «Compartido desde Sazón».
- **Archivos:**
  - los de compartir van en `cacheDir/fichas/`, que se vacía al iniciar la app;
  - «Guardar» usa `CreateDocument` con el nombre `Sazon-<titulo-sin-tildes>.png|pdf`;
  - las últimas opciones de la ficha se guardan en `ajustes`.
- **Pruebas:** ninguna ubicación real en pruebas ni capturas.
- **Versión:** `versionName=0.4.0`.

## Puntos de atención para la revisión

1. **Pruebas intermitentes.** La publicación no puede fallar al azar. → Task 1: cada clase con Room cierra la base de datos después de la regla de Compose, y ninguna prueba deja trabajo pendiente en `Dispatchers.Main`.
2. **Hueco ocupado y semana con huecos en el pasado.** «Rellenar» no pisa nada ni rellena días pasados, y «Deshacer» deja todo como estaba. → Task 2: `fill_onlyEmptyFromToday`; Task 3: `fillWeek_undo_restoresExactly`.
3. **Receta muy larga en la ficha.** Por ejemplo, 40 ingredientes y 25 pasos con notas largas:
   - la imagen no se corta a mitad de una línea: recorta con «…y N pasos más»;
   - el PDF pasa a más páginas y no pierde texto.
   → Task 4: `png_overflow_truncatesWithMore`, `pdf_overflow_paginatesAllText`.
4. **Receta sin foto con el diseño «Foto grande».** Usa el emoji de la categoría sobre el acento y el interruptor «Foto» se desactiva. → Task 5: `photoDesign_withoutPhoto_usesEmoji`.
5. **Borrar una receta que está en el menú.** Su hueco desaparece por la cascada, sin errores. La lista de la semana no la cuenta. → Task 3: `deleteRecipe_removesItsSlots`.

---

### Task 1: Pruebas intermitentes

**Archivos:**
- Modificar: `app/src/test/.../testing/` (una regla común `DatabaseRule`, o el patrón de `ShoppingScreenTest`) y todas las clases de prueba que cierran la base de datos en `@After` con una regla de Compose activa.
- Modificar: `BackupViewModelTest` y cualquier prueba que use `Dispatchers.setMain`. Usar una `MainDispatcherRule` común que deshaga siempre el cambio. Antes de `resetMain`, cerrar o cancelar los `viewModelScope` (con `ViewModelStore.clear()` o cancelando el `CoroutineScope`).
- Fallos vistos:
  - `BackupViewModelTest.driveFile_whenItCannotBeSaved_releasesThePermission_andDeletesTheEmptyDocument`: `IllegalStateException` en `TestMainDispatcher`, línea 43;
  - `DiaryScreenTest.orphanEntry_notClickable`: `SQLException`, con la base de datos ya cerrada.

- [ ] **Paso 1:** buscar todas las clases con `db.close()` o `container.close()` en `@After` junto a una regla de Compose, y pasarlas a la regla exterior. Buscar todos los `Dispatchers.setMain` y pasarlos a la regla común.
- [ ] **Paso 2:** encontrar la causa del `IllegalStateException` de `TestMainDispatcher`, por ejemplo un `viewModelScope` vivo de una prueba anterior, y arreglarla en las pruebas, no en la app, salvo que sea un fallo de la app.
- [ ] **Paso 3:** CI tres veces seguidas en verde sobre el mismo commit. Volver a lanzarlo con `rerun`, o con un commit vacío de prueba si no hay otra forma. Anotar las tres ejecuciones.
- [ ] **Paso 4:** documentar el patrón en `CLAUDE.md` («Problemas conocidos») y hacer commit con «Pruebas: cerrar Room después de Compose y restaurar Dispatchers.Main siempre».

### Task 2: Lógica del menú de la semana

**Archivos:**
- Crear: `logic/src/main/kotlin/app/recetario/plan/{WeekPlan.kt,WeekFill.kt,WeekShopping.kt}`, con sus pruebas.

**Interfaces que produce:**
- `data class PlannedMeal(id: String, date: LocalDate, slot: MealSlot, recipeId: String, servings: Int)`.
  - `enum class MealSlot(val key: String) { BREAKFAST("DESAYUNO"), LUNCH("COMIDA"), DINNER("CENA") }`, con `key` en `MEAL_SLOTS`.
  - `MealSlot.moment: Moment`.
- `fun weekOf(date: LocalDate): List<LocalDate>`: del lunes al domingo.
- `fun weekTitle(monday: LocalDate, today: LocalDate): String`: «Esta semana», «Semana que viene», «Semana pasada» o «Semana del 24 de noviembre».
- `fun fillWeek(week: List<LocalDate>, today: LocalDate, existing: List<PlannedMeal>, planFor: (LocalDate, MealSlot, Set<String>) -> TodayRecipe?, defaultServings: (String) -> Int, newId: () -> String): List<PlannedMeal>`:
  - solo rellena huecos vacíos con `date >= today`;
  - no repite una receta en la semana mientras haya otras: pasa a `planFor` los ids ya usados;
  - devuelve solo los nuevos.
- `fun weekIngredients(meals: List<PlannedMeal>, today: LocalDate, recipes: Map<String, Recipe>, pantry: List<String>, basics: List<String>): List<ShoppingItem>`:
  - cuenta los platos con `date >= today`, escalados a sus porciones con el escalado de siempre;
  - quita lo que cubre la despensa (`pantryCovers`) y los básicos;
  - deja las cantidades listas para `mergeInto`.

- [ ] **Paso 1: pruebas en rojo.**
  - `weekOf_mondayToSunday` (también si hoy es domingo);
  - `weekTitle_cases`;
  - `fill_onlyEmptyFromToday` (un hueco ocupado y un día pasado se quedan como están);
  - `fill_noRepeatsWhileOptions`;
  - `fill_returnsOnlyNew`;
  - `weekIngredients_scaledAndSummed`;
  - `weekIngredients_excludesPantryAndBasics`;
  - `weekIngredients_onlyFromToday`;
  - `slotKeys_areMealSlots`.
- [ ] **Paso 2:** FAIL.
- [ ] **Paso 3:** implementar. `planFor` lo da la app con `planToday`, usando la previsión de ese día si la hay y si no la estación. Aquí solo se inyecta.
- [ ] **Paso 4:** PASS.
- [ ] **Paso 5:** commit «Menú de la semana: lógica».

### Task 3: Pestaña Semana

**Archivos:**
- Crear: `app/src/main/java/app/recetario/plan/{MealPlanRepository.kt,WeekScreen.kt,WeekViewModel.kt}`.
- Modificar:
  - `shopping/ShoppingScreen.kt`: la pestaña Semana deja de ser un marcador;
  - `AppContainer`.
- Pruebas: `MealPlanRepositoryTest` y `WeekScreenTest`.

**Interfaces que produce:**
- `interface MealPlanRepository`:
  - `fun observeWeek(monday: LocalDate): Flow<List<PlannedMeal>>`;
  - `suspend fun set(date, slot, recipeId, servings)`: sustituye el hueco reutilizando el id o borrando antes;
  - `suspend fun clear(date, slot): PlannedMeal?`;
  - `suspend fun addAll(meals): Unit` y `suspend fun removeAll(ids)`, para «Deshacer».

**Comportamiento** (diseño §7.6):
- Cabecera con «‹ título ›»; el desplazamiento entre semanas se guarda en el `SavedStateHandle`.
- 7 días, cada uno con Desayuno, Comida y Cena:
  - un hueco vacío muestra «+»;
  - uno lleno muestra la receta y las porciones.
- Tocar un hueco abre un `BottomPanel` para elegir receta (buscador), cambiar porciones («−/+») o «Quitar».
- «Rellenar con sugerencias»:
  - usa `fillWeek` con `planToday`, la previsión de cada día y `ratings`;
  - avisa con «Se añadieron N comidas» y ofrece «Deshacer».
- «Generar lista de compra»:
  - usa `weekIngredients` y después `mergeInto` en la lista, en una transacción, como «Lo que necesito»;
  - avisa con «✓ N ingredientes añadidos»; sin nada que añadir, «No falta nada para esta semana».
- Sin recetas: «Añade recetas para planear tu semana».

- [ ] **Paso 1: pruebas en rojo.**
  - `set_replacesOccupiedSlot_noUpsertTrap`, `clear_returnsForUndo`;
  - `deleteRecipe_removesItsSlots`;
  - `week_navigation_titles`, `tapSlot_chooseRecipe_saves`, `changeServings`;
  - `fillWeek_addsOnlyEmpty_andUndo_restoresExactly`;
  - `generateList_addsMissing_message`, `generateList_nothingMissing`;
  - `emptyState_noRecipes`;
  - `slotsWritten_alwaysInMealSlots`: lo que se escribe pasa la validación de las copias.
- [ ] **Paso 2:** CI: FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** CI: PASS.
- [ ] **Paso 5:** commit «Menú de la semana».

### Task 4: Maquetación de la ficha (lógica)

**Archivos:**
- Crear: `logic/src/main/kotlin/app/recetario/share/{CardFormat.kt,CardContent.kt,CardLayout.kt}`, con sus pruebas.

**Interfaces que produce:**
- `enum class CardFormat(widthPx/heightPx o pt, isPdf)`: `SQUARE`, `STORY`, `LANDSCAPE`, `PDF_A4`, `PDF_LETTER` y `PDF_CARD`, con las medidas de las Restricciones.
- `enum class CardDesign { CLASSIC, MINIMAL, BIG_PHOTO, THEME }`.
- `data class CardOptions(format, design, photo: Boolean, steps: Boolean, notes: Boolean, flavor: Boolean)`, que se guarda como texto en `ajustes`.
- `data class CardContent(title, servingsLine, ingredients: List<String>, steps: List<String>, notes: String?, flavorLine: String?, emoji: String, hasPhoto: Boolean, footer = "Compartido desde Sazón")`, construido con `cardContent(recipe, servings, categoryEmoji, options)` y el escalado de siempre.
- `interface TextMeasurer { fun lines(text: String, style: CardTextStyle, widthPx: Float): Int }`: en `logic` se inyecta; la app usa `StaticLayout` o Compose.
- `fun layoutImage(content, format, design, measurer): ImageLayout`:
  - baja el tamaño de letra hasta un mínimo legible (escala 0,75);
  - si no cabe, recorta los pasos con «…y N pasos más» y las notas con «…»;
  - nunca corta a mitad de línea.
- `fun paginatePdf(content, format, design, measurer): List<PdfPage>`: todo el texto, partido en páginas sin perder nada; cada página lleva el pie.
- `fun cardFileName(title: String, format: CardFormat): String`: `Sazon-<titulo-sin-tildes-con-guiones>.png|pdf`, sin caracteres que no valen en un nombre de archivo y con un máximo de 60 caracteres.

- [ ] **Paso 1: pruebas en rojo** (con un medidor falso de caracteres por línea):
  - `content_scaledIngredients_andOptions`;
  - `png_fits_noTruncation`;
  - `png_overflow_shrinksThenTruncatesWithMore`;
  - `png_neverCutsMidLine`;
  - `pdf_overflow_paginatesAllText` (toda línea aparece una vez);
  - `pdf_footerOnEveryPage`;
  - `fileName_noAccents_noSlashes_max60`;
  - `options_roundTripString`.
- [ ] **Paso 2:** FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** PASS.
- [ ] **Paso 5:** commit «Fichas: maquetación».

### Task 5: Pantalla Ficha (vista previa, imagen, PDF, compartir y guardar)

**Archivos:**
- Crear: `app/src/main/java/app/recetario/share/{CardScreen.kt,CardViewModel.kt,CardRenderer.kt,PdfRenderer.kt,CardDesigns.kt}`.
- Modificar:
  - `recipes/detail/*`: «Compartir» abre `Card(recipeId, servings)`;
  - `navigation/*`;
  - `SettingsRepository` (`cardOptions`);
  - `RecetarioApp` (vaciar `cacheDir/fichas/` al iniciar);
  - el `FileProvider` (`res/xml`) para `cacheDir/fichas/`.
- Pruebas: `CardScreenTest`, `CardRendererTest` y `PdfRendererTest`.

**Comportamiento** (diseño §7.7):
- **Vista previa** arriba, escalada para caber, con el contenido real. Debajo, en grupos de chips:
  - formato: Texto, Cuadrada, Historia, Horizontal, PDF A4, PDF Carta, Tarjeta 10×15;
  - diseño;
  - interruptores: Foto, Pasos, Notas, Sabor.
- **Texto** usa `formatRecipeAsText`, el de la etapa 1, con vista previa de texto.
- **Imagen:**
  - ~~el mismo Composable de la vista previa, a tamaño real fuera de la pantalla, capturado con `GraphicsLayer.toImageBitmap()`~~ (cambiado: el mismo `CardPainter` que la vista previa, en un `Bitmap` del tamaño del formato);
  - PNG en `cacheDir/fichas/`.
- **PDF:**
  - `PdfDocument` con `StaticLayout` y las fuentes de la app (`ResourcesCompat.getFont`);
  - las páginas de `paginatePdf`;
  - la foto escalada, si la opción está activa.
- **«Compartir»:** `ACTION_SEND` con el `FileProvider` (`image/png`, `application/pdf` o `text/plain`) y `FLAG_GRANT_READ_URI_PERMISSION`.
- **«Guardar»:** `CreateDocument` con `cardFileName`. Si falla, se avisa con «No se pudo guardar la ficha.» y el documento vacío se borra.
- **Sin foto:** el interruptor Foto está desactivado y «Foto grande» usa el emoji de la categoría sobre el acento.
- **Opciones:** se recuerdan en `ajustes`.
- **Giro:** al cambiar de diseño o de formato, la vista previa gira sobre su eje vertical (`rotationY`, unos 300 ms). Con `LocalReducedMotion` cambia al instante. Se puede tocar durante el giro.
- **Trabajo pesado:** generar el archivo va en `Dispatchers.IO`/`Default` con un indicador «Preparando…»; el toque nunca se bloquea más de lo que dura generar.

- [ ] **Paso 1: pruebas en rojo.**
  - `share_text_usesFormatter`;
  - `share_png_writesFileOfExactSize` (1080 × 1080…);
  - `share_pdf_pageCountAndSize`;
  - `save_createDocument_writesFile`, `save_failure_deletesEmptyDocument`;
  - `photoDesign_withoutPhoto_usesEmoji`;
  - `options_remembered`;
  - `flip_onDesignChange_reducedMotionInstant`;
  - `cacheFichas_clearedOnStart`;
  - `intent_grantsReadPermission`.
- [ ] **Paso 2:** CI: FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** CI: PASS.
- [ ] **Paso 5:** commit «Fichas para compartir».

### Task 6: Cierre de la 2C (0.4.0)

- **Modificar:**
  - `versionName=0.4.0`;
  - `CapturasTest`, en los 3 temas: la pestaña Semana (vacía y llena), el panel de un hueco, la Ficha en cada formato, la ficha con cada diseño (cuadrada) y una página del PDF dibujada a PNG;
  - `CLAUDE.md`;
  - `docs/instalar.md`, si hace falta.
- **Crear** `RecorridoEtapa2cTest`, que recorre:
  1. tres recetas;
  2. la semana: rellenar, deshacer, rellenar y asignar a mano;
  3. generar la lista, que debe salir en Compra;
  4. desde el detalle, la ficha cuadrada «Clásico», que se comparte con un archivo de 1080 × 1080;
  5. el PDF A4, que se guarda;
  6. el cambio de diseño, que hace girar la vista previa.
- **Paso 3:** mirar las capturas, incluida la imagen y el PDF generados, y compararlos con el estilo de los bocetos.
- **Paso 5** (controlador, con el usuario): revisión final, fusión a `main` **con su confirmación** y publicación de `v0.4.0`.
