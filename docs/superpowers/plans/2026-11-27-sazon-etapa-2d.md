# Sazón · Entrega 2D (widgets, manos libres, temporada y animaciones) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Objetivo:** publicar la 0.5.0, que cierra la etapa 2. Trae:
- el calendario de temporada de México, con su tarjeta en Inicio y el empujón en Hoy;
- el modo cocina manos libres, por gesto (sensor de proximidad) y por voz en el propio teléfono;
- los widgets «Hoy» y «Compra», hechos con Glance;
- las animaciones de la etapa 2 que faltaban: el fondo de Hoy según el clima, «Otra idea» barajando y el ingrediente que cae en la despensa.

**Arquitectura:**
- La lógica pura va en `logic/` con TDD en local. Son el calendario y su emparejamiento con los ingredientes, y la detección del gesto y de las órdenes de voz.
- En `:app` van la pantalla «De temporada», el controlador de manos libres (sensor, `SpeechRecognizer` del teléfono, `TextToSpeech`), los widgets Glance con su trabajo de WorkManager, y las animaciones.
- **No cambia el esquema de Room ni el de las copias de seguridad.**

**Tecnología:** Kotlin, Compose y **Jetpack Glance** (`androidx.glance:glance-appwidget`, la única biblioteca nueva, aprobada en el diseño §3), además de WorkManager, que ya está en el proyecto.

**Diseño:** `docs/superpowers/specs/2026-11-19-sazon-etapa-2-design.md`, en concreto §7.8 (widgets), §7.9 (manos libres), §7.10 (temporada) y §8 (animaciones). Ahí se dice cómo debe comportarse cada cosa; este plan no lo repite. Consulta también `CLAUDE.md`.

## Restricciones globales

- **Interfaz:**
  - los textos en español y los identificadores en inglés; los commits en español, con las líneas de la sesión;
  - contraste de 4,5:1 o más, sin transparencias en el texto; áreas táctiles de 48 dp o más;
  - las animaciones respetan `LocalReducedMotion` (estado final al instante), no bloquean el toque y no se anuncian en TalkBack.
- **Privacidad del micrófono:**
  - el permiso `RECORD_AUDIO` se pide solo al activar la voz;
  - solo se usa el reconocimiento en el teléfono (`SpeechRecognizer.createOnDeviceSpeechRecognizer`, Android 12+), **nunca** el de red, ni siquiera como respaldo;
  - nunca se escucha en segundo plano: se para en `ON_STOP` y al salir de la pantalla.
- **Widgets:**
  - no abren la base de datos antes de que pase `DatabaseUpgradeGuard`; si la guarda falló, muestran «Abre Sazón»;
  - no piden red: usan el clima en caché y el trabajo solo refresca si la caché tiene más de 1 h y hay red;
  - el trabajo periódico de Hoy (1 h) solo existe mientras haya algún widget «Hoy» (`onEnabled` y `onDisabled`).
- **Temporada:**
  - unos 70 productos de los mercados de México, con contenido original;
  - el emparejamiento con los ingredientes usa palabras completas, sinónimos y normalización, como `pantryCovers`.
- **Contenido de prueba:** nada de ubicaciones reales ni de datos personales.
- **Versión:** `versionName=0.5.0`.

## Puntos de atención para la revisión

1. **El micrófono no escucha nunca fuera del modo cocina.** Pasa al salir de la pantalla, en segundo plano, con la pantalla apagada y al girarla. → Task 3: `voice_stopsOnStop_andOnLeave`.
2. **Teléfono boca abajo o en el bolsillo.** No hay «Hecho ✓» en bucle. → Task 1: `gesture_heldNear_doesNotCount`; Task 3: `gesture_cooldown_preventsDoubleStep`.
3. **Un widget con la base de datos sin migrar o la guarda fallida.** No abre Room y muestra «Abre Sazón». → Task 4: `widget_guardFailed_showsOpenApp`.
4. **Marcar un artículo desde el widget de Compra con la app cerrada.** Se marca de verdad y la lista de la app lo refleja. → Task 4: `shoppingWidget_check_persists`.
5. **El fondo animado de Hoy.** Nunca baja el contraste del texto de la tarjeta y se para a los 3 s. → Task 5: `todayBackground_contrast_threeThemes`, `todayBackground_stopsAfter3s`.

---

### Task 1: Temporada y manos libres (lógica)

**Archivos:**
- Crear:
  - `logic/src/main/kotlin/app/recetario/season/{SeasonCalendar.kt,SeasonMatch.kt,SeasonTexts.kt}`
  - `logic/src/main/resources/season/mexico.json`
  - `logic/src/main/kotlin/app/recetario/cooking/handsfree/{WaveDetector.kt,VoiceCommands.kt}`
- Pruebas: los `*Test.kt` correspondientes.

**Interfaces que produce:**
- `enum class Availability { BEST, AVAILABLE, OUT }`.
- `data class Produce(id: String, name: String, emoji: String, kind: ProduceKind /* FRUIT, VEGETABLE, CHILE, HERB */, synonyms: List<String>, months: List<Availability> /* 12 */)`.
- `object SeasonCalendar`:
  - `fun load(): List<Produce>` (lee el recurso en UTF-8, como `Catalog`);
  - `fun forMonth(month: Int): Pair<List<Produce>, List<Produce>>`: primero lo que está en su mejor momento, después lo que también hay.
- `fun producesIn(ingredientNames: List<String>, calendar: List<Produce>): Set<String>`: usa palabras completas, sinónimos y plurales básicos (reutiliza `pantryCovers` o lo que haya en `PantryMatch`).
- `fun recipesUsing(produce: Produce, recipes: List<RecipeSummary>): Int`.
- `fun inSeasonBonus(recipe: TodayRecipe, month: Int, calendar: List<Produce>): Double`:
  - +4 por cada producto en su mejor momento y +1 por cada uno que también hay;
  - como mucho +8.
- `fun seasonTeaser(month: Int, calendar: List<Produce>): String`: «De temporada en noviembre: guayaba, mandarina, calabaza…».
- `class WaveDetector(cooldownMs: Long = 1500, maxNearMs: Long = 1500, heldMs: Long = 2000)` con `fun onProximity(near: Boolean, atMs: Long): Boolean`:
  - devuelve true cuando la lectura pasa de lejos a cerca y otra vez a lejos en menos de 1,5 s, y no está dentro de los 1,5 s de pausa tras la última;
  - si se queda cerca más de 2 s, no cuenta.
- `enum class VoiceCommand { NEXT, PREVIOUS, TIMER, REPEAT }` y `fun parseVoiceCommand(alternatives: List<String>): VoiceCommand?`:
  - «siguiente», «listo», «hecho»; «anterior», «atrás»; «temporizador»; «repite», «repetir»;
  - sin distinguir tildes ni mayúsculas, con la orden como palabra completa;
  - ignora cualquier otra frase;
  - mira todas las alternativas del reconocedor.

- [ ] **Paso 1: pruebas en rojo.**
  - Calendario:
    - `calendar_atLeast70_uniqueIds_12months`;
    - `calendar_includesMexicanStaples`: mango, guayaba, pitahaya, elote, nopal, chile poblano, chile jalapeño, jitomate, tomate verde, aguacate, calabaza, epazote y cilantro;
    - `forMonth_bestThenAvailable`.
  - Emparejamiento:
    - `match_synonyms` (elote/maíz, jitomate/tomate);
    - `match_wholeWords` («caña» no cubre «cañada»).
  - Empujón:
    - `bonus_capped8`;
    - `teaser_text`.
  - Gesto:
    - `gesture_wave_counts`;
    - `gesture_heldNear_doesNotCount`;
    - `gesture_cooldown`;
    - `gesture_slowWave_doesNotCount`.
  - Voz:
    - `voice_commands_variants`;
    - `voice_otherPhrase_null`;
    - `voice_usesAlternatives`.
- [ ] **Paso 2:** `./gradlew -p logic test`: FAIL.
- [ ] **Paso 3:** implementar y escribir el contenido del calendario. Es original, en español de México y con meses plausibles para el centro y el occidente del país.
- [ ] **Paso 4:** PASS.
- [ ] **Paso 5:** commit con el mensaje «Temporada y manos libres: lógica».

### Task 2: Pantalla «De temporada» y empujón en Hoy

**Archivos:**
- Crear: `app/src/main/java/app/recetario/season/{SeasonScreen.kt,SeasonViewModel.kt}`.
- Modificar:
  - `home/HomeScreen.kt`: la tarjeta pequeña con `seasonTeaser`;
  - `navigation/*`: el destino `Season`;
  - `logic/.../today/TodayContexts.kt` (`todayInputs`): pasar `inSeasonBonus` con el mes del contexto;
  - `RecipesRoute`: abrir con una búsqueda inicial.
- Pruebas: `SeasonScreenTest`, más la ampliación de `HomeScreenTest` y `TodayViewModelTest`.

**Comportamiento:** el del diseño §7.10.
- Arriba van los meses, con el actual elegido; la fila no es perezosa, para que se compongan los 12 (como en el conversor).
- Debajo, «En su mejor momento» y «También hay», agrupados por tipo de producto.
- Cada producto muestra «N recetas». Al tocarlo se abre Recetas con su nombre en la búsqueda.

- [ ] **Paso 1: pruebas en rojo.**
  - `season_currentMonthSelected`;
  - `season_changeMonth`;
  - `season_tapProduce_opensRecipesSearch`;
  - `season_recipeCounts`;
  - `homeTeaser_opensSeason`;
  - `today_inSeasonRecipe_preferred`, que usa el motor con los datos de prueba.
- [ ] **Paso 2:** comprobar en CI que fallan (FAIL).
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** comprobar en CI que pasan (PASS).
- [ ] **Paso 5:** commit con el mensaje «De temporada».

### Task 3: Modo cocina manos libres

**Archivos:**
- Crear: `app/src/main/java/app/recetario/cooking/handsfree/{HandsFreeController.kt,ProximitySource.kt,VoiceSource.kt,Speaker.kt,HandsFreePanel.kt}`.
- Modificar:
  - `cooking/CookingScreen.kt` y `CookingParts.kt`: el botón «✋ Manos libres» en la cabecera, el punto que late mientras escucha y el destello «✓ Siguiente»;
  - `AndroidManifest.xml`: `RECORD_AUDIO`;
  - `SettingsRepository`: guarda las preferencias de gesto y voz en `ajustes`.
- Pruebas: `HandsFreeControllerTest` y `CookingHandsFreeTest`. El sensor, el reconocedor y la voz se sustituyen por versiones falsas.

**Comportamiento:** el del diseño §7.9.
- **Interfaces inyectables, para poder probar sin hardware:**
  - `ProximitySource`: un `Flow<Boolean>` con «cerca o lejos» y `available`;
  - `VoiceSource`: un `Flow<List<String>>` con las alternativas, más `available`, `start` y `stop`;
  - `Speaker`: `speak(text)`.
- **Gesto:** equivale a «Hecho ✓».
- **Voz:**
  - siguiente, anterior, temporizador (arranca el del paso actual, si lo tiene) y repite (lee el paso con `TextToSpeech` en `es-MX`, o en `es` si no hay `es-MX`);
  - el reconocedor se reinicia tras cada resultado y tras cada error, con un intervalo creciente: 0,5 s, 1 s, 2 s… hasta 10 s.
- **Panel:**
  - interruptores de gesto y voz;
  - la voz desactivada en Android 11 o anterior, con el texto «La voz necesita Android 12 o más reciente; puedes usar el gesto»;
  - el gesto desactivado si el teléfono no tiene sensor.
- **TalkBack** anuncia la orden.
- **Al apagarse** (`ON_STOP`, salir de la pantalla o cerrar el panel), se dejan de escuchar el sensor y el micrófono. Al volver, sigue como estaba.

- [ ] **Paso 1: pruebas en rojo.**
  - `wave_advancesStep`;
  - `gesture_cooldown_preventsDoubleStep`;
  - `voice_next_previous_timer_repeat`;
  - `voice_stopsOnStop_andOnLeave`, que comprueba que el falso deja de escuchar;
  - `voice_unavailableBelow31_panelSays`;
  - `voice_permissionAskedOnlyOnEnable`;
  - `noSensor_gestureHidden`;
  - `flash_announcesCommand`;
  - `reducedMotion_dotStatic`.
- [ ] **Paso 2:** comprobar en CI que fallan (FAIL).
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** comprobar en CI que pasan (PASS).
- [ ] **Paso 5:** commit con el mensaje «Modo cocina manos libres».

### Task 4: Widgets «Hoy» y «Compra» (Glance)

**Archivos:**
- Crear: `app/src/main/java/app/recetario/widget/{TodayWidget.kt,ShoppingWidget.kt,WidgetReceivers.kt,TodayWidgetWorker.kt,WidgetData.kt}` y `res/xml/{today_widget_info.xml,shopping_widget_info.xml}`.
- Modificar:
  - el catálogo de versiones (Glance) y `app/build.gradle.kts`;
  - `AndroidManifest.xml`: los dos receptores;
  - `ShoppingRepository`: al cambiar la lista, avisa a los widgets con `updateAll`;
  - `RecetarioApp` o `MainActivity`: al abrir la app, actualiza «Hoy».
- Pruebas: `TodayWidgetTest`, `ShoppingWidgetTest` y `TodayWidgetWorkerTest`. Usan `glance-appwidget-testing` si se puede usar con Robolectric; si no, se prueban `WidgetData`, que es pura, y el trabajo.

**Comportamiento:** el del diseño §7.8.
- **«Hoy»** muestra el plato propuesto con el mismo motor y los mismos datos que la tarjeta (`todayInputs`/`todayContext`).
  - «Otra idea» la cambia; la vuelta se guarda en el estado de Glance.
  - Tocar el plato abre su detalle con un `Intent` explícito a `MainActivity` con el id.
- **«Compra»** muestra los artículos sin marcar. Tocar el círculo marca el artículo mediante una acción que escribe en Room; tocar el título abre Compra.
- **Colores:** claro (Cálido) u oscuro (Nocturno) según el sistema, con contraste de 4,5:1 o más.
- **Sin recetas:** «Abre Sazón para añadir recetas».
- **Si la guarda falló:** «Abre Sazón».

- [ ] **Paso 1: pruebas en rojo.**
  - `todayWidget_data_matchesCard`;
  - `todayWidget_anotherIdea`;
  - `todayWidget_noRecipes`;
  - `widget_guardFailed_showsOpenApp`;
  - `shoppingWidget_listsUnchecked`;
  - `shoppingWidget_check_persists`;
  - `worker_scheduledOnlyWithWidgets`;
  - `worker_usesCachedWeather_refreshOnlyIfOld`.
- [ ] **Paso 2:** comprobar en CI que fallan (FAIL).
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** comprobar en CI que pasan (PASS).
- [ ] **Paso 5:** commit con el mensaje «Widgets Hoy y Compra».

### Task 5: Animaciones de la etapa 2

**Archivos:**
- Crear: `app/src/main/java/app/recetario/core/ui/motion/{WeatherBackdrop.kt,ShuffleCards.kt,DropIn.kt}`.
- Modificar:
  - `home/TodayCard.kt`: el fondo y el barajado;
  - `pantry/PantryScreen.kt`: la caída del ingrediente añadido.
- Pruebas: `WeatherBackdropTest`, `ShuffleTest` y `DropInTest`, con la ampliación de `TodayContrastTest`.

**Comportamiento:** el del diseño §8.
- **Fondo:**
  - gotas si llueve, rayos si hay sol, nubes si está nublado y estrellas de noche;
  - con poca intensidad, detrás de la tarjeta;
  - se mueve durante 3 s al entrar en Inicio y luego se queda quieto;
  - con `LocalReducedMotion` se dibuja directamente el cuadro fijo;
  - TalkBack no lo lee.
- **«Otra idea»:**
  - las tarjetas salen inclinadas y desplazadas, como cartas, y entran las nuevas, en unos 350 ms;
  - se puede tocar durante la animación;
  - sustituye al fundido actual.
- **Despensa:** el ingrediente añadido cae hasta su sitio en la lista en unos 350 ms, con muelle.
- **Propiedades de prueba:** `AnimationProgress` y, donde haga falta, `boundsInRoot`, como en la etapa 1.

- [ ] **Paso 1: pruebas en rojo.**
  - `todayBackground_kindByWeather`;
  - `todayBackground_stopsAfter3s`;
  - `todayBackground_reducedMotion_static`;
  - `todayBackground_contrast_threeThemes`, con píxeles en el peor cuadro;
  - `shuffle_350ms_tappableDuring`;
  - `shuffle_reducedMotion_instant`;
  - `pantryDrop_springs_reducedMotionInstant`.
- [ ] **Paso 2:** comprobar en CI que fallan (FAIL).
- [ ] **Paso 3:** implementar.
- [ ] **Paso 4:** comprobar en CI que pasan (PASS).
- [ ] **Paso 5:** commit con el mensaje «Animaciones de la etapa 2».

### Task 6: Cierre de la 2D y de la etapa 2 (0.5.0)

- **Modificar:**
  - `versionName=0.5.0`;
  - `CapturasTest`, en los 3 temas: «De temporada», la tarjeta de temporada en Inicio, el panel de manos libres y la cabecera escuchando, los widgets (con capturas de la vista previa en Compose si Glance no se puede capturar), el fondo de Hoy a mitad y quieto, y el barajado a mitad;
  - `CLAUDE.md`: «Etapa 2 terminada (0.5.0)»;
  - `docs/instalar.md`: los permisos nuevos (micrófono, opcional) y cómo añadir los widgets;
  - `docs/superpowers/pendientes-del-proyecto.md`.
- **Crear** `RecorridoEtapa2dTest`. Recorre, en este orden:
  1. la tarjeta de temporada lleva a «De temporada»;
  2. al tocar un producto se abren las recetas;
  3. en el modo cocina se activan las manos libres, con el sensor y la voz falsos;
  4. un gesto avanza un paso;
  5. «siguiente», «anterior», «repite» y «temporizador»;
  6. salir de la pantalla deja de escuchar;
  7. «Otra idea» baraja.
- **CI en verde dos veces.**
- **Paso 5** (lo hace el controlador con el usuario):
  - revisión final;
  - fusión a `main` con su confirmación y publicación de `v0.5.0`;
  - recopilar las decisiones de la etapa 2 para el usuario.
