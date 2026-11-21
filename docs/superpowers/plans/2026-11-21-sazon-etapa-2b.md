# Sazón · Entrega 2B (Hoy y maridaje) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** publicar la 0.3.0. Trae la tarjeta Hoy en Inicio, con clima de Open-Meteo, horarios de comidas y la estación (secas y lluvias en el trópico); el tema según el sol; y el maridaje por reglas, con catálogo, «Le iría bien…», la pestaña Maridaje, la rueda comparativa, el menú completo y 👍/👎 con historial.

**Arquitectura:**
- La lógica pura va en `logic/` con TDD local:
  - estaciones, momentos y clases térmicas en `today/`;
  - lectura de las respuestas de Open-Meteo en `weather/`;
  - el motor de maridaje con su catálogo y guía en `pairing/`;
  - el motor de Hoy en `today/`;
  - la noche según el sol en `core/ui/theme/`.
- En `:app` van el cliente HTTP (`HttpURLConnection`), la ubicación, el repositorio del clima con caché y las pantallas, con el patrón `XxxRoute`/`XxxContent`.
- **No cambia el esquema de Room.** `pairing_ratings` ya existe desde la 2A.

**Tecnología:** Kotlin, Compose, Room, DataStore y kotlinx.serialization. Sin bibliotecas nuevas.

**Diseño:** `docs/superpowers/specs/2026-11-19-sazon-etapa-2-design.md`, §3, §6.3, §7.3, §7.4, §7.5, §7.11 y la rueda de §8. Se apoya en el diseño general `2026-11-13-recetario-design.md` (§8.6 y §8.7) y en `CLAUDE.md`.

## Restricciones globales

- **Interfaz:**
  - textos de la interfaz en español; identificadores en inglés; commits en español con las líneas de la sesión;
  - contraste ≥ 4,5:1 sin transparencias en el texto; áreas táctiles ≥ 48 dp; `LocalReducedMotion`;
  - las estrellas, los emojis y los dibujos llevan un texto equivalente para TalkBack.
- **Red:**
  - **solo** `api.open-meteo.com` y `geocoding-api.open-meteo.com`, con `HttpURLConnection`, 10 s de espera y `User-Agent: Sazon`;
  - sin red, nada falla: Hoy usa la hora y la estación;
  - caché de 1 h; sin conexión se usa el último dato si tiene menos de 6 h.
- **Permisos nuevos:**
  - `INTERNET`;
  - `ACCESS_COARSE_LOCATION`, que se pide solo al tocar «Usar mi ubicación aproximada»;
  - nunca la ubicación precisa.
- **Repositorio público:** ninguna ubicación real del usuario en el código, las pruebas ni las capturas. Las pruebas usan ciudades conocidas o inventadas.
- **Ajustes nuevos en `ajustes`:**
  - `mealTimes`;
  - `thermalThresholds` (15 y 24 °C por defecto);
  - `themeBySun` (no por defecto);
  - `locationMode` (`NONE`, `CITY` o `APPROXIMATE`);
  - la ciudad elegida (nombre, latitud y longitud).
- **En `estado-local`:** la última ubicación aproximada, el último clima y la última previsión, con su hora.
- **Copia de seguridad:** lleva `mealTimes`, `thermalThresholds` y `themeBySun` como campos **opcionales** de `BackupSettings`. La ubicación **no** va en la copia (decisión de este plan: es un dato personal y el archivo puede compartirse). No se sube `BACKUP_SCHEMA_VERSION`: los campos opcionales los lee también la 0.2.0, que ignora las claves desconocidas.
- **Valoraciones:**
  - `pairKey = "<kindA>:<idA>|<kindB>:<idB>"`, con los dos lados ordenados por texto;
  - `kind` solo puede ser `RECIPE` o `CATALOG` (`PAIRING_SIDE_KINDS`);
  - catálogo con catálogo no se puede valorar.
- **Contenido original:** el catálogo de unos 70 elementos y la guía de unos 80 ingredientes, con lo que pide el diseño §7.5 (bebidas mexicanas y genéricas, ingredientes mexicanos).
- **Versión:** `versionName=0.3.0`.

## Puntos de atención para la revisión

1. **Sin conexión, o con Open-Meteo caído o lento.** Hoy sale enseguida, sin clima y con un porqué de hora y estación. Ningún error a la vista, salvo en Ajustes › Ubicación y clima («No se pudo obtener el tiempo»). → Prueba en la Task 4: `weather_networkFails_usesCacheOrNone`; y en la Task 5: `today_withoutWeather_showsSeasonReason`.
2. **Permiso de ubicación denegado o «solo esta vez».** Se ofrece «Elegir ciudad» y no se vuelve a pedir en bucle. → Prueba en la Task 4: `location_denied_offersCity_andDoesNotAskAgain`.
3. **Pocas recetas:** ninguna, solo bebidas o ningún postre. Los estados vacíos del diseño §7.3, sin tarjetas rotas. → Pruebas en la Task 3: `today_noDessert_leavesSlotEmpty`; y en la Task 5: `todayCard_emptyStates`.
4. **«Otra idea» muchas veces seguidas.** No repite mientras queden opciones; al agotarse, empieza otra vuelta; nunca se queda en blanco. → Prueba en la Task 3: `anotherIdea_cyclesWithoutRepeats`.
5. **Una pareja con 👎.** Desaparece del detalle y de la pestaña Maridaje en los dos sentidos, y vuelve si se cambia la valoración en el historial. → Prueba en la Task 6: `dislike_hidesBothWays_untilChanged`.

---

### Task 1: Estación, momentos, clima y noche según el sol (lógica)

**Archivos:**
- Crear:
  - `logic/src/main/kotlin/app/recetario/today/{Season.kt,MealTimes.kt,Thermal.kt}`
  - `logic/src/main/kotlin/app/recetario/weather/{Weather.kt,OpenMeteoJson.kt,WeatherTexts.kt}`
  - `logic/src/main/kotlin/app/recetario/core/ui/theme/SunNight.kt`
- Pruebas: los `*Test.kt` correspondientes, con JSON de ejemplo inventados en `logic/src/test/resources/openmeteo/`.

**Interfaces que produce:**
- `enum class Season { DRY, RAINY, SPRING, SUMMER, AUTUMN, WINTER }` con la etiqueta en español «secas», «lluvias», «primavera»…
- `fun seasonFor(date: LocalDate, latitude: Double?): Season`:
  - si `latitude` es null o |lat| < 23,5, en el norte DRY de noviembre a mayo y RAINY de junio a octubre; en el sur, al revés;
  - si no, estaciones meteorológicas del hemisferio: norte, DIC–FEB invierno…; sur, al revés.
- `data class MealTimes(breakfast: TimeRange, lunch: TimeRange, snack: TimeRange, dinner: TimeRange)`, con lo que queda fuera como noche.
  - Por defecto: desayuno 7–11, comida 13–16, merienda 16–19 y cena 19–23; el resto, noche.
  - `fun MealTimes.momentAt(time: LocalTime): Moment`. Entre 11 y 13 cuenta como `LUNCH` (decisión de este plan).
- `enum class Thermal { COLD, MILD, HOT }` y `fun thermalClass(apparentC: Double, coldBelow: Double = 15.0, hotAbove: Double = 24.0): Thermal`.
- `data class CurrentWeather(apparentC: Double, precipitationMm: Double, weatherCode: Int, isDay: Boolean)`.
- `data class DailyForecast(date: LocalDate, weatherCode: Int, maxC: Double, minC: Double, rainProbability: Int?, sunrise: LocalTime?, sunset: LocalTime?)`.
- `data class WeatherSnapshot(current: CurrentWeather?, daily: List<DailyForecast>, fetchedAt: Instant)`.
- `fun parseForecast(json: String, fetchedAt: Instant): WeatherSnapshot`: lanza `WeatherParseException` si falta lo esencial.
- `data class Place(name: String, admin: String?, country: String?, latitude: Double, longitude: Double)` y `fun parseGeocoding(json: String): List<Place>`.
- `fun weatherKind(code: Int): WeatherKind` (`CLEAR`, `CLOUDY`, `FOG`, `RAIN`, `STORM`, `SNOW`), con los códigos WMO de Open-Meteo, y `fun weatherLabel(code: Int): String` («Soleado», «Lluvia»…).
- `fun isNightBySun(now: LocalTime, sunrise: LocalTime, sunset: LocalTime): Boolean`.
- `resolveTheme` gana un parámetro opcional `sun: Pair<LocalTime, LocalTime>? = null`: con Automático y `sun` no null, la noche va de la puesta a la salida.

- [ ] **Paso 1: pruebas en rojo.**
  - `season_tropicNorth_dryAndRainy` (10 de enero en lat 20 → DRY; 15 de julio → RAINY);
  - `season_tropicSouth_inverted`;
  - `season_noLocation_isTropicNorth`;
  - `season_temperateNorth` (lat 40: 15 de enero → WINTER) y `season_temperateSouth`;
  - `moment_defaults` (7:00 → BREAKFAST, 12:00 → LUNCH, 17:30 → SNACK, 22:59 → DINNER, 23:00 → NIGHT, 3:00 → NIGHT);
  - `thermal_bounds` (14,9 → COLD; 15 → MILD; 24 → MILD; 24,1 → HOT);
  - `parseForecast_fullSample`, `parseForecast_missingCurrent_returnsNullCurrent` y `parseForecast_garbage_throws`;
  - `parseGeocoding_fiveResults` y `parseGeocoding_noResults_empty`;
  - `weatherKind_codes` (0 → CLEAR, 3 → CLOUDY, 45 → FOG, 61 → RAIN, 95 → STORM, 71 → SNOW);
  - `resolveTheme_bySun_nightAfterSunset`;
  - `resolveTheme_bySun_nullFallsBackToHours`.
- [ ] **Paso 2:** `./gradlew -p logic test`: FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** `./gradlew -p logic test`: PASS, todo en verde.
- [ ] **Paso 5:** commit «Hoy y clima: estaciones, momentos y lectura de Open-Meteo».

### Task 2: Motor de maridaje, catálogo y guía de sabores (lógica)

**Archivos:**
- Crear:
  - `logic/src/main/kotlin/app/recetario/pairing/{PairingModels.kt,PairingRules.kt,PairingEngine.kt,FullMenu.kt,PairKey.kt,Catalog.kt,FlavorGuide.kt}`
  - `logic/src/main/resources/pairing/catalog.json`
  - `logic/src/main/resources/pairing/flavors.json`
- Pruebas: `PairingRulesTest`, `PairingEngineTest`, `FullMenuTest`, `PairKeyTest`, `CatalogTest` y `FlavorGuideTest`.

**Interfaces que produce:**
- `enum class PairRole { DRINK, FOOD, DESSERT, SIDE }`.
- `data class PairingItem(kind: String /* RECIPE|CATALOG */, id: String, name: String, role: PairRole, course: Course?, alcoholic: Boolean?, flavor: FlavorProfile, serveTemp: ServeTemp?)`.
- `enum class Principle { INTENSITY, ACID_CUTS_RICH, DESSERT_DRINK_SWEETER, SPICY_NEEDS_FRESH, BITTER_BALANCE, SALTY_LIKES_SOUR_SWEET, TEMPERATURE }`: los 7 principios del diseño general §8.7.
- `data class Contribution(principle: Principle, points: Double, reason: String?)`.
- `fun scorePair(a: PairingItem, b: PairingItem, weights: Map<Principle, Double> = DEFAULT_WEIGHTS): PairScore`, donde `PairScore(score: Int /* 0..100 */, reasons: List<String> /* 1 o 2 */, top: Principle)`.
- `fun learnedWeights(ratings: List<RatedPair>): Map<Principle, Double>`:
  - cada principio se multiplica por un factor de 0,8 a 1,2;
  - empieza en 1;
  - cada 👍 donde fue el `top` suma 0,02 y cada 👎 resta 0,02, con el factor acotado.
- `fun suggestPairings(subject: PairingItem, candidates: List<PairingItem>, ratings: Map<String /*pairKey*/, Boolean>, limit: Int): List<Suggestion>`:
  - `Suggestion(item: PairingItem, score: Int, reasons: List<String>, liked: Boolean?)`;
  - las parejas con 👎 se excluyen y las 👍 suman 15 puntos (hasta 100);
  - se ordenan primero las recetas propias y después el catálogo, y dentro de cada grupo por puntuación;
  - solo cruces con sentido: bebida con comida o postre, comida o postre con bebida, postre con bebida.
- `fun fullMenu(recipes: List<PairingItem>, catalog: List<PairingItem>, ratings: Map<String, Boolean>): FullMenu?`:
  - `FullMenu(starter: PairingItem?, main: PairingItem, dessert: PairingItem?, drink: PairingItem?, reason: String)`;
  - con solo recetas propias si es posible, completando con el catálogo; null si no hay ningún plato principal.
- `fun pairKey(a: Pair<String, String>, b: Pair<String, String>): String`: lados ordenados `"kind:id"`. Lanza una excepción si los dos son `CATALOG`.
- `object Catalog { fun load(): List<PairingItem> }`: lee el recurso; los ids son estables (`cat-…`).
- `object FlavorGuide { fun goesWellWith(ingredientNames: List<String>, limit: Int = 6): List<String> }`:
  - empareja los nombres como `pantryCovers` (palabras completas);
  - no devuelve lo que la receta ya lleva.

**Contenido** (original, en español, sin copiar fuentes con derechos):
- **Catálogo** de 70 elementos o más:
  - bebidas con y sin alcohol: genéricas (tinto ligero, blanco seco, rosado, espumoso, cerveza clara, oscura y de trigo, té verde, té negro, café, agua mineral con limón, refresco de cola, limonada…);
  - las mexicanas del diseño §7.5: agua de jamaica, de horchata, de tamarindo y de limón con chía; tejuino, café de olla, atole, champurrado, chocolate caliente, tequila blanco y reposado, mezcal, raicilla, michelada, pulque y tepache;
  - acompañamientos y postres genéricos.
- **Guía** de unos 80 ingredientes, entre ellos chile poblano, chipotle, epazote, cilantro, nopal, maíz, frijol, aguacate, tomate verde, piloncillo, canela y vainilla.

- [ ] **Paso 1: pruebas en rojo.**
  - Un caso por principio:
    - `intensity_similarScoresHigher`;
    - `acid_cutsRich`;
    - `dessertDrink_mustBeAsSweet`;
    - `spicy_avoidsStrongAlcohol`;
    - `bitter_withBitter_penalised_withSweet_balanced`;
    - `salty_likesSourOrSweet`;
    - `temperature_matchesContext`.
  - `score_inRange0to100`, `reasons_oneOrTwo_spanish`.
  - `suggest_ownRecipesFirst`, `suggest_dislikedExcluded`, `suggest_likedBoosted15`, `suggest_onlySensibleRoles`.
  - `learnedWeights_boundedPoint8to1Point2`.
  - `fullMenu_prefersOwn_completesWithCatalog`, `fullMenu_noMain_null`.
  - `pairKey_orderIndependent`, `pairKey_catalogCatalog_throws`.
  - `catalog_atLeast70_uniqueIds_includesMexican` (con jamaica, horchata, tejuino, café de olla, atole, tequila, mezcal y raicilla).
  - `flavors_atLeast80_entries`, `goesWellWith_excludesWhatRecipeHas`.
- [ ] **Paso 2:** FAIL.
- [ ] **Paso 3:** implementar y escribir el contenido.
- [ ] **Paso 4:** PASS.
- [ ] **Paso 5:** commit «Maridaje por reglas: motor, catálogo y guía de sabores».

### Task 3: Motor de Hoy (lógica)

**Archivos:**
- Crear:
  - `logic/src/main/kotlin/app/recetario/today/{TodayModels.kt,TodayEngine.kt,TodayTexts.kt}`
- Pruebas: `TodayEngineTest` y `TodayTextsTest`.

**Interfaces que produce:**
- `data class TodayRecipe(id, title, role: PairRole, course: Course?, flavor: FlavorProfile, serveTemp: ServeTemp?, moments: Set<Moment>, totalMinutes: Int?, favorite: Boolean, alcoholic: Boolean?, ingredientNames: List<String>, coverPhoto: String?)`.
  - `fun TodayRecipe.asPairingItem(): PairingItem`.
- `data class TodayContext(now: LocalDateTime, moment: Moment, season: Season, thermal: Thermal?, weatherKind: WeatherKind?, apparentC: Double?, rainingNow: Boolean, weekend: Boolean)`.
- `data class TodayInputs(recipes: List<TodayRecipe>, stats: Map<String, CookStats>, pantry: List<String>, basics: List<String>, inSeasonBonus: (TodayRecipe) -> Double = { 0.0 })`.
  - `inSeasonBonus` se queda vacío hasta la 2D.
- `data class TodayMenu(moment: Moment, main: TodayRecipe?, drink: TodayRecipe?, dessert: TodayRecipe?, snack: TodayRecipe?, reason: String)`.
- `fun planToday(ctx: TodayContext, inputs: TodayInputs, exclude: Set<String>, random: Random): TodayMenu`.

**Reglas** (diseño general §8.6 y diseño de la etapa 2 §7.3):
- **Elección del principal:**
  - debe valer para el momento (o no tener momentos: entonces se deduce de su tipo);
  - entre semana, comida y cena prefieren 40 min o menos;
  - con frío, caliente y reconfortante suben; con calor, frío y fresco;
  - la despensa suma por porcentaje;
  - el diario sube lo que lleva tiempo sin hacerse y penaliza lo hecho en los 2 últimos días;
  - favoritas y valoraciones suman;
  - un poco de azar.
- **Comida y cena:** principal + bebida + postre que combinen (con `scorePair`).
- **Desayuno y merienda:** plato + bebida.
- **Noche:** bebida + `snack` (algo para picar).
- **El porqué** (`reason`), por ejemplo:
  - «Hace 9 °C y llueve: algo caliente y reconfortante»;
  - «Hace 31 °C: algo fresco»;
  - sin clima: «Es temporada de lluvias: algo calientito» o «Cena de entre semana: algo rápido».
- **`exclude`:** los ids ya mostrados por «Otra idea». Si no queda nada fuera de `exclude`, se ignora y empieza otra vuelta.

- [ ] **Paso 1: pruebas en rojo.**
  - `cold_prefersHot`, `hot_prefersCold`;
  - `weekday_prefersQuick_weekendAllowsLong`;
  - `recentlyCooked_penalised`, `longAgo_boosted`, `pantryCoverage_adds`;
  - `lunch_hasMainDrinkDessert_thatPair`, `breakfast_plateAndDrink`, `night_drinkAndSnack`;
  - `today_noDessert_leavesSlotEmpty`, `today_noRecipes_allNull`;
  - `anotherIdea_cyclesWithoutRepeats` (con 3 principales, 6 llamadas seguidas sin repetir dentro de cada vuelta de 3);
  - `reason_texts` (tabla de casos);
  - `deterministic_withSeed`.
- [ ] **Paso 2:** FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** PASS.
- [ ] **Paso 5:** commit «Motor de Hoy».

### Task 4: Clima, ubicación y ajustes en la app

**Archivos:**
- Crear:
  - `app/src/main/java/app/recetario/weather/{OpenMeteoClient.kt,WeatherRepository.kt,LocationProvider.kt}`
  - `settings/{LocationWeatherScreen.kt,MealTimesScreen.kt}`
- Modificar:
  - `AndroidManifest.xml` (los 2 permisos);
  - `SettingsRepository` (claves nuevas);
  - `SettingsScreen` (filas «Ubicación y clima» y «Horarios de comidas»);
  - `AboutScreen` («Datos del tiempo: Open-Meteo.com, CC BY 4.0», con enlace);
  - `AppearanceScreen` («Noche según la salida y puesta del sol», visible solo con Automático y ubicación);
  - `MainActivity`/`ThemeState` (pasa `sun` a `resolveTheme` si `themeBySun` y hay una previsión de menos de 3 días);
  - `logic/.../backup/BackupModel.kt` (`BackupSettings` con `mealTimes`, `thermalThresholds` y `themeBySun` opcionales), el exportador y el importador (se aplican solo al reemplazar);
  - `AppContainer`.
- Pruebas:
  - `OpenMeteoClientTest`, con un `HttpURLConnection` falso por fábrica inyectable (nunca red real);
  - `WeatherRepositoryTest`;
  - `LocationWeatherScreenTest`;
  - `MealTimesScreenTest`;
  - `ThemeBySunTest`;
  - `BackupSettingsV2bTest`.

**Interfaces que produce:**
- `interface WeatherRepository`:
  - `val snapshot: Flow<WeatherSnapshot?>`, la caché;
  - `suspend fun refresh(force: Boolean = false): WeatherResult`;
  - `fun location(): Flow<Place?>`.
  - `WeatherResult` es `Ok`, `NoLocation`, `Offline` o `Error(message)`.
  - Nunca lanza excepciones. Pide a Open-Meteo solo si la caché tiene más de 1 h. Sin red, devuelve la caché si tiene menos de 6 h, o null.
- `OpenMeteoClient(open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection })`:
  - `fun forecast(lat: Double, lon: Double): String`, con `current=apparent_temperature,precipitation,weather_code,is_day`, `daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset`, `timezone=auto` y `forecast_days=7`;
  - `fun geocode(name: String): String`, con `count=5`, `language=es` y `format=json`.
- `LocationProvider`: la última ubicación aproximada de `LocationManager` (`NETWORK_PROVIDER`, o pasiva si no hay), sin Play Services; null si no hay permiso o no hay dato.
- `SettingsRepository`:
  - `mealTimes`, `thermalThresholds`, `themeBySun`, `locationMode` y `city` (`Flow`s);
  - sus `set…`, con `reportFailure` en la pantalla.

**Pantallas** (diseño §7.11):
- **«Ubicación y clima»:**
  - «Usar mi ubicación aproximada», que pide el permiso; si se deniega, el aviso «Sin permiso de ubicación. Puedes elegir una ciudad.» y no se vuelve a pedir;
  - «Elegir ciudad»: un campo con 5 resultados «Ciudad, Estado, País» en un `BottomPanel`;
  - la ciudad actual;
  - los umbrales con «−/+» de 1 °C;
  - «Quitar ubicación»;
  - el estado del último dato («Actualizado a las 14:05» o «No se pudo obtener el tiempo»).
- **«Horarios de comidas»:** «−/+» de media hora por momento. Los rangos no pueden solaparse ni quedar vacíos; si no, se avisa y no se guarda.

- [ ] **Paso 1: pruebas en rojo.**
  - `client_buildsExactUrls`;
  - `weather_cacheFreshUnder1h_noRequest`;
  - `weather_networkFails_usesCacheOrNone` (caché de 5 h → la usa; de 7 h → null);
  - `weather_noLocation_returnsNoLocation`;
  - `location_denied_offersCity_andDoesNotAskAgain`;
  - `city_search_selectsAndSaves`;
  - `mealTimes_overlap_rejected`;
  - `about_showsOpenMeteoAttribution`;
  - `themeBySun_nightBetweenSunsetAndSunrise`;
  - `backup_v2b_settingsRoundTrip_andOldBackupKeepsDefaults`.
- [ ] **Paso 2:** CI: FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** CI: PASS.
- [ ] **Paso 5:** commit «Clima, ubicación y horarios de comidas».

### Task 5: Tarjeta Hoy en Inicio

**Archivos:**
- Crear: `app/src/main/java/app/recetario/home/{TodayCard.kt,TodayViewModel.kt}`.
- Modificar:
  - `HomeScreen.kt` (la tarjeta va arriba, bajo el saludo, con el clima pequeño);
  - `RecipeSummary` (`logic/.../recipes/model/Recipe.kt`), que gana `course: Course?` y `alcoholic` (si falta);
  - la consulta de `observeSummaries`.
- Pruebas: `TodayCardTest` y la ampliación de `HomeScreenTest`.

**Comportamiento** (diseño §7.3):
- La tarjeta lleva el estilo de cada tema del diseño general §6.1: degradado terracota en Cálido, verde claro en Fresco y oscuro con borde ámbar en Nocturno.
- Muestra el momento, el porqué, el plato principal grande y la bebida y el postre (o lo que toque) en mini tarjetas. Tocar abre el detalle.
- «Otra idea» vuelve a calcular con `exclude`, que se guarda en el `SavedStateHandle`.
- Arriba va el clima: icono de `weatherKind`, «24 °C» y la ciudad. Solo sale si hay datos.
- Al entrar en Inicio se llama a `refresh()` en segundo plano, sin bloquear nada.
- Estados vacíos:
  - sin recetas, «Cuando tengas recetas, aquí te propondré qué cocinar» y «+ Nueva receta»;
  - si falta un hueco, «Aún no tienes bebidas» (o postres) con un enlace para crearla.
- El cálculo va en `Dispatchers.Default` y la tarjeta nunca bloquea la entrada escalonada de Inicio.

- [ ] **Paso 1: pruebas en rojo.**
  - `todayCard_showsMainDrinkDessert_andReason`;
  - `todayCard_anotherIdea_changesMain`;
  - `todayCard_emptyStates` (sin recetas; sin bebida);
  - `today_withoutWeather_showsSeasonReason`;
  - `todayCard_tapOpensDetail`;
  - `todayCard_weatherChipOnlyWithData`.
- [ ] **Paso 2:** CI: FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** CI: PASS.
- [ ] **Paso 5:** commit «Tarjeta Hoy».

### Task 6: Maridaje en la app (detalle, pestaña, rueda comparativa, menú completo e historial)

**Archivos:**
- Crear:
  - `app/src/main/java/app/recetario/pairing/{PairingRepository.kt,PairingViewModel.kt,PairingScreen.kt,CompareWheel.kt,FullMenuScreen.kt,PairingHistoryScreen.kt}`
  - `recipes/detail/DetailPairing.kt`
- Modificar:
  - `navigation/*` (la pestaña Maridaje deja de ser un marcador; destinos `PairingCompare`, `FullMenu` y `PairingHistory`);
  - `recipes/detail/*` (la sección «Maridaje» y «Le iría bien…»);
  - `AppContainer`.
- Borrar: `PairingPlaceholderScreen.kt`.
- Pruebas:
  - `PairingRepositoryTest`
  - `DetailPairingTest`
  - `PairingScreenTest`
  - `CompareWheelTest`
  - `FullMenuScreenTest`
  - `PairingHistoryTest`

**Interfaces que produce:**
- `interface PairingRepository`:
  - `fun observeRatings(): Flow<Map<String, Boolean>>`;
  - `suspend fun rate(a: PairingItem, b: PairingItem, liked: Boolean?)`, donde `null` borra la valoración;
  - `fun observeHistory(): Flow<List<RatedPairView>>`.
  - Escribe `pairing_ratings` con `pairKey`, `aKind`/`aId` y `bKind`/`bId` ordenados y `ratedAt` del reloj. Solo `RECIPE` y `CATALOG`.

**Comportamiento** (diseño §7.5):
- **En el detalle,** la sección «Maridaje»:
  - las 3 mejores, cada una con su porqué y 👍/👎 (botones de 48 dp con «Me gusta esta pareja» y «No me gusta esta pareja»);
  - «Ver más» abre la pestaña con esa receta.
- **En el detalle,** «Le iría bien…»: hasta 6 chips. Si no hay ninguno, no sale.
- **Pestaña Maridaje:**
  - «¿Con qué combina…?» busca entre las recetas propias y las bebidas del catálogo;
  - los resultados van en «Tus recetas» e «Ideas»;
  - tocar uno abre la rueda comparativa;
  - el botón «Menú completo» y el acceso a «Historial».
- **Rueda comparativa:**
  - las dos ruedas de sabor superpuestas, con relleno semitransparente y líneas y texto opacos, la leyenda y las frases;
  - cada rueda se dibuja desde el centro en 400 ms, primero una y luego la otra, con `LocalReducedMotion` (dibujo final al instante);
  - para TalkBack, una descripción con los cinco sabores de cada lado.
- **Historial:** las parejas valoradas con su 👍/👎, que se puede cambiar o quitar.
- Una 👎 oculta la pareja en los dos sentidos.

- [ ] **Paso 1: pruebas en rojo.**
  - `rate_storesOrderedPairKey`, `rate_nullRemoves`, `rate_catalogCatalog_rejected`;
  - `detail_showsTop3WithReasons`, `detail_goesWellWith_chips`;
  - `dislike_hidesBothWays_untilChanged`;
  - `pairingTab_searchDrink_showsRecipesAndIdeas`;
  - `compareWheel_drawsBoth_reducedMotionInstant`, `compareWheel_talkbackDescription`;
  - `fullMenu_showsFourCourses_orEmptyState`;
  - `history_changeRating`.
- [ ] **Paso 2:** CI: FAIL.
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** CI: PASS.
- [ ] **Paso 5:** commit «Maridaje».

### Task 7: Cierre de la 2B (0.3.0)

**Archivos:**
- Modificar:
  - `app/version.properties` (`versionName=0.3.0`);
  - `CapturasTest`: la tarjeta Hoy (con y sin clima, y vacía), Ubicación y clima, Horarios, Apariencia con el sol, la sección Maridaje del detalle, la pestaña Maridaje, la rueda comparativa (también a mitad del dibujo), el menú completo y el historial, en los 3 temas;
  - `CLAUDE.md`;
  - `docs/instalar.md` (permisos nuevos: ubicación aproximada, opcional).
- Crear: `RecorridoEtapa2bTest`, que recorre:
  1. tres recetas: un principal caliente, una bebida y un postre;
  2. con clima falso de 9 °C y lluvia, Hoy propone el principal caliente con «Hace 9 °C y llueve…»;
  3. «Otra idea»;
  4. el detalle con su sección Maridaje;
  5. 👎 a una pareja, que desaparece;
  6. la pestaña Maridaje partiendo de una bebida;
  7. la rueda comparativa;
  8. el menú completo;
  9. el historial, donde se cambia la 👎.
  - Capturas en `app/build/capturas/recorrido-2b/`.

- [ ] **Paso 1:** el recorrido y las capturas.
- [ ] **Paso 2:** CI en verde, con el recuento anotado.
- [ ] **Paso 3:** mirar las capturas con Read y arreglar los fallos visuales claros y baratos.
- [ ] **Paso 4:** commit «Cerrar la entrega 2B: versión 0.3.0» y push.
- [ ] **Paso 5** (controlador, con el usuario): revisión final; fusión a `main` **solo con su confirmación**; comprobar la publicación de `v0.3.0`; el usuario la instala encima.
