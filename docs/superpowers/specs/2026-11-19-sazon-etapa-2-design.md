# Sazón — Etapa 2 «La app te ayuda» — Diseño

Fecha: 2026-11-19 · Estado: aprobado por el usuario (2026-11-19)

Este documento **amplía** el diseño general `2026-11-13-recetario-design.md` (lo llamaremos «el diseño general»). Lo que allí ya está decidido para la etapa 2 (§5, §6, §7, §8.1, §8.3, §8.6, §8.7 y §8.8) sigue valiendo y no se repite aquí: este texto solo añade lo nuevo, concreta lo que estaba abierto y anota los cambios. Si algo choca, manda este documento.

«(decisión de Claude)» marca lo que propuse yo y el usuario no decidió de forma explícita. El usuario delegó estas decisiones («te dejo tomar todas las decisiones… las más óptimas y mejores») y aprobó el reparto en cuatro entregas («todo cuadra y va bien»).

---

## 1. Intención

En la etapa 1 la app guarda, cocina y hace la compra. En la etapa 2 **ayuda a decidir**: qué cocinar hoy, qué combina con qué, qué se puede hacer con lo que hay en casa y qué comer esta semana. También recuerda lo que se ha cocinado (diario) y permite **presumir las recetas** con fichas bonitas.

El usuario vive en **México**. Por eso las estaciones, el calendario de temporada y el catálogo de bebidas se piensan para México (decisión del usuario sobre su ubicación; el contenido concreto es decisión de Claude).

## 2. Objetivos, no-objetivos y anti-objetivos

**Objetivos**
- Abrir la app y ver en segundos una propuesta razonada para el momento del día, con su porqué.
- Saber qué bebida, postre o plato propio acompaña a una receta, y por qué.
- Apuntar la despensa en segundos y saber qué se puede cocinar con ella.
- Llevar un diario de lo cocinado, con valoración y fotos.
- Planificar la semana y sacar de ella la lista de compra.
- Compartir una receta como ficha (imagen o PDF) en el formato que se elija.
- Usar el modo cocina sin tocar la pantalla, ver la sugerencia o la compra desde un widget y saber qué está de temporada.

**No-objetivos**
- La IA, «Algo nuevo», importar desde un enlace y escanear recetas en papel: llegan en la etapa 3.
- Nutrición y calorías; recetas de otras personas; sincronización.
- Un calendario de temporada para otros países (solo México).

**Anti-objetivos**
- **Perder datos con la primera migración** de la base de datos. Es el riesgo principal de esta etapa.
- Que Hoy o el maridaje se sientan aleatorios o sin sentido: cada propuesta lleva su porqué.
- Que lo básico (recetas, cocina, compra) dependa de internet: sin conexión, Hoy funciona solo con la hora y la estación.
- Que el micrófono envíe audio fuera del teléfono. Solo se usa el reconocimiento de voz del propio teléfono, **nunca uno en la red**.
- Animaciones de fondo continuas que gasten batería o distraigan.

## 3. Restricciones nuevas

- **Internet** (permiso `INTERNET`, el primero de la app): solo para Open-Meteo, es decir, el clima, la previsión, la salida y puesta del sol y buscar una ciudad. Nada más en esta etapa.
- **Ubicación aproximada** (`ACCESS_COARSE_LOCATION`), pedida solo cuando el usuario elige «Usar mi ubicación». La alternativa es escribir una ciudad. Nunca se pide la ubicación precisa.
- **Micrófono** (`RECORD_AUDIO`), pedido solo al encender la voz en el modo cocina.
- **Open-Meteo:**
  - atribución CC BY 4.0 en «Acerca de»;
  - llamadas solo cuando hacen falta: caché de una hora y como mucho una petición por pantalla abierta.
- **Sin bibliotecas de red nuevas:** `HttpURLConnection` y kotlinx.serialization, que ya está (decisión de Claude).
- **Bibliotecas nuevas permitidas:** Jetpack Glance (el widget) y nada más, salvo que el plan lo justifique.
- **Contenido original:** el catálogo de maridaje, la guía de sabores y el calendario de temporada se escriben a partir de conocimiento culinario general (diseño general §8.7).
- **El repositorio es público:** ni la ciudad ni las coordenadas del usuario aparecen en el código, las pruebas, las capturas ni la documentación. Las pruebas usan ubicaciones inventadas o capitales conocidas.

## 4. Enfoques considerados

| Decisión | Opciones | Elegida | Por qué |
|---|---|---|---|
| Cómo se entrega | A) Toda la etapa 2 de una vez · B) Cuatro entregas con APK | **B** (aprobada por el usuario) | Es unas tres veces la etapa 1. Con B el usuario prueba antes y los errores salen pronto. |
| Estaciones en el trópico | A) Cuatro estaciones astronómicas · B) Secas y lluvias | **B** (decisión de Claude) | En México (\|lat\| < 23,5° en gran parte, y clima tropical o subtropical en casi todo el país) lo que cambia de verdad la comida es la lluvia y el calor, no la primavera o el otoño. Fuera del trópico se siguen usando las 4 estaciones meteorológicas del diseño general. |
| Red | A) Ktor/Retrofit · B) `HttpURLConnection` | **B** (decisión de Claude) | Son dos llamadas GET a una API sin clave; una biblioteca solo añadiría peso. |
| Imagen de la ficha | A) Dibujar a mano en un `Canvas` · B) Componer la ficha en Compose y capturarla | **B** (decisión de Claude) | La vista previa y la imagen son la misma composición: lo que se ve es lo que se comparte. |
| PDF de la ficha | A) Capturar una imagen y meterla en el PDF · B) `android.graphics.pdf.PdfDocument` dibujando texto | **B** (decisión de Claude) | Con B el texto es nítido a cualquier tamaño, se puede seleccionar y el archivo pesa poco. Las páginas largas se parten. |
| Widget | A) `RemoteViews` clásico · B) Jetpack Glance | **B** (decisión de Claude) | Glance se escribe como Compose y es la vía oficial actual. |
| Manos libres | A) Solo voz · B) Solo gesto · C) Gesto (sensor de proximidad) y voz en el teléfono | **C** (decisión de Claude) | El gesto funciona en cualquier móvil y con ruido. La voz sin internet solo es fiable desde Android 12 (`createOnDeviceSpeechRecognizer`). Así nadie se queda sin manos libres. |
| Copia antes de migrar | A) Exportar un zip con el código de la versión vieja · B) Copiar tal cual los archivos de la base de datos antes de abrirla | **B** (decisión de Claude) | B no depende de que el esquema nuevo entienda el viejo y funciona aunque la migración falle a medias. La copia se puede restaurar desde la app (§6.2). |
| Pesos del maridaje que aprenden | A) Guardar los pesos ajustados · B) Calcularlos cada vez a partir de las valoraciones | **B** (decisión de Claude) | Las valoraciones ya van en la copia de seguridad. Así no hay un estado más que copiar ni que se pueda desincronizar. |

## 5. Entregas

Cada entrega acaba con un APK instalable encima del anterior, sin perder datos, y con su versión:

| Entrega | Versión | Contenido |
|---|---|---|
| **2A · Diario y despensa** | 0.2.0 | Migración 1→2 con la copia previa; diario (desde «¡Listo!» y a mano, en el detalle y como cronología); despensa con «¿Qué puedo hacer?»; filtro «con mi despensa»; orden «más cocinadas» y «mejor valoradas»; «ya lo tienes» y «a la despensa» en Compra; «hace tiempo que no la haces» en Inicio; copias de seguridad con todo lo nuevo (`schemaVersion` 2). |
| **2B · Hoy y maridaje** | 0.3.0 | Ajustes de ubicación y clima, y horarios de comidas; Open-Meteo; motor de Hoy y su tarjeta en Inicio; tema Automático según el sol (opcional); maridaje por reglas con catálogo, «Le iría bien…», pestaña Maridaje (bebida → plato, menú completo, rueda comparativa, 👍/👎 e historial). |
| **2C · Semana y fichas** | 0.4.0 | Menú de la semana (rellenar con sugerencias y la previsión, generar la lista de compra); fichas para compartir (texto, imagen y PDF, con formatos, diseños, opciones y vista previa). |
| **2D · Extras** | 0.5.0 | Widgets (Hoy y Compra); modo cocina manos libres; calendario de temporada; las animaciones de la etapa 2; cierre de la etapa (recorrido de extremo a extremo y documentación). |

**Cómo llegan las entregas (decisión del usuario: versión oficial firmada):**
- **Antes de la 2A, la 0.1.0 oficial.** La etapa 1 pasa a `main`, siempre con el permiso del usuario. El usuario crea el secreto `SIGNING_PASSPHRASE` y lanza una vez «Crear llave de firma», que publica `Sazon-0.1.0.apk` firmado con la llave definitiva (guía en `docs/instalar.md`). El usuario pasa sus datos una sola vez: «Exportar copia» en el APK de prueba, desinstalarlo, instalar la 0.1.0 e «Importar copia» con «Reemplazar todo».
- **Cada entrega** (0.2.0 a 0.5.0) se cierra fusionándola a `main`, con permiso del usuario cada vez. «Publicar APK» la publica, y se instala **encima** de la anterior sin perder datos.
- La 0.2.0, instalada encima de la 0.1.0 con datos reales, es la prueba de verdad de la migración 1→2 y de su copia previa.
- Los APK de prueba de CI (`sazon-apk`) siguen sirviendo para mirar avances a mitad de una entrega, en el teléfono o en otro dispositivo. Para instalarlos hay que desinstalar la versión oficial, así que el usuario no los instala sobre sus datos reales.

Las tablas nuevas de las cuatro entregas se crean **todas en la migración 1→2** de la entrega 2A, aunque algunas se usen más tarde. Así la etapa 2 tiene **una sola migración** (decisión de Claude).

## 6. Datos

### 6.1 Esquema de la base de datos, versión 2 (solo tablas nuevas; las de la versión 1 no cambian)

- **`cook_log`** (diario):
  - `id` (UUID) y `recipeId` (que pasa a `NULL` si se borra la receta: el diario no se pierde);
  - `recipeTitle` (copia del título, para seguir leyéndose sin la receta);
  - `date` (fecha local), `rating` (1–5, opcional), `note`, `createdAt` y `updatedAt`.
- **`cook_log_photos`**: `logId`, `position` (0–3) y `fileName`. Se borran en cascada con su entrada, y los archivos se borran como las fotos de paso.
- **`pantry_items`**: `id`, `name`, `normalized` (único) y `addedAt`.
- **`meal_plan`**:
  - `id`, `date`, `slot` (`DESAYUNO`, `COMIDA` o `CENA`), `recipeId` y `servings`;
  - único por `(date, slot)`;
  - si se borra la receta, se borran sus huecos del plan (son planes, no recetas).
- **`pairing_ratings`** (decisión de Claude):
  - `pairKey` (clave primaria), `aKind`, `aId`, `bKind`, `bId`, `liked` (sí o no) y `ratedAt`;
  - cada lado es una receta propia (`RECIPE`, con su id) o un elemento del catálogo (`CATALOG`, con su id estable del JSON). Así se pueden valorar también las parejas que parten de una bebida del catálogo y las del menú completo;
  - **la pareja no tiene orden:** los dos lados se guardan ordenados (`kind:id` menor primero) y `pairKey` es `"<a>|<b>"`, así que (A, B) y (B, A) son la misma valoración;
  - valorar otra vez sustituye la valoración, y un 👎 oculta la pareja desde los dos lados;
  - se borra si se borra cualquiera de las recetas que la forman. Una pareja de dos elementos del catálogo no se puede valorar.
- Índices: `cook_log(recipeId, date)`, `meal_plan(date)`, `pairing_ratings(aId)` y `pairing_ratings(bId)`.

`MIGRATION_1_2` solo hace `CREATE TABLE` y `CREATE INDEX`: no toca ninguna tabla existente. Lleva su prueba con `MigrationTestHelper`: una base de datos v1 con recetas, compra y categorías migra a v2 y conserva todo.

### 6.2 Copia previa a la migración

- **Cuándo:** antes de que Room abra la base de datos, si el archivo existe y su `user_version` es menor que la versión actual.
- **Cómo:**
  1. abrir la base de datos con `SQLiteDatabase` en modo lectura y escritura;
  2. ejecutar `PRAGMA wal_checkpoint(TRUNCATE)` y cerrarla;
  3. copiar `recetario.db` a `filesDir/backups/antes-de-actualizar-v<vieja>-<AAAA-MM-DD-HHmm>.db`, primero en `.tmp` con fsync y luego renombrando.
- **Si la copia falla** (disco lleno): la app **no migra**. Muestra una pantalla de error («No hay espacio para hacer la copia de seguridad antes de actualizar. Libera espacio y vuelve a abrir Sazón.») y no abre la base de datos. Así nunca se migra sin red de seguridad.
- **Cuántas se guardan:** las 2 más recientes de este tipo. Se incluyen en la rotación de `BackupRotation` como un tipo más, con las mismas garantías.
- **En «Copias en el teléfono»:** aparece como «Antes de actualizar · fecha», con «Guardar fuera» y «Restaurar».
  - Restaurar sustituye la base de datos y reinicia la app, que vuelve a migrar. Las fotos no se tocan: la base de datos vieja apunta a fotos que siguen ahí.
  - Antes de restaurar se hace la copia previa de importación habitual.
- **Si la migración lanza una excepción:** la app muestra una pantalla de error con «Guardar la copia fuera del teléfono» (la `.db` previa) y no se cierra en bucle.

### 6.3 Ajustes nuevos

- **En `ajustes`** (va en la copia de Android y en la copia de seguridad):
  - horarios de comidas (diseño general §8.6);
  - umbrales térmicos;
  - básicos de la despensa (por defecto: sal, agua, aceite, pimienta, azúcar);
  - tema según el sol (sí o no);
  - la ciudad elegida a mano (nombre, latitud y longitud);
  - modo de ubicación (ninguna, ciudad o aproximada);
  - las últimas opciones de la ficha;
  - las preferencias de manos libres (gesto y voz).
- **En `estado-local`** (solo este teléfono, fuera de las copias):
  - la última ubicación aproximada;
  - el último clima y la última previsión, con su hora.

### 6.4 Copias de seguridad, `schemaVersion` 2

- `backup.json` añade `cookLog` (con sus fotos en el manifiesto `images`), `pantry`, `mealPlan`, `pairingRatings` y los ajustes nuevos de `ajustes`.
- **Las copias v1 se siguen importando:** lo que falta se toma como vacío. Una copia v3 se rechaza con el aviso de «versión más nueva».
- **«Combinar»:**
  - diario: se añaden las entradas que faltan por `id`; si el `id` existe, gana el `updatedAt` más reciente;
  - valoraciones: por `pairKey`; si existe, gana el `ratedAt` más reciente;
  - despensa: por `normalized`;
  - menú: se añade el hueco si está libre en la copia local;
  - nunca se borra nada.
- **«Reemplazar todo»:** sustituye también las tablas nuevas.
- La exportación, la importación y la ida y vuelta llevan pruebas con todos los datos nuevos.

## 7. Funciones

### 7.1 Diario (2A)

- **Desde «¡Listo!»** en el modo cocina, la tarjeta «Guardar en el diario» se abre ya rellena: hoy, sin valoración, sin nota, con «Añadir foto». Guardar o «Ahora no» no bloquean la salida del modo cocina.
- **A mano**, desde el detalle: «Lo hice» abre el mismo panel, con la fecha editable.
- **En el detalle,** una línea:
  - «La hiciste 3 veces · última hace 2 semanas · ★★★★☆», con la media redondeada a media estrella;
  - si no hay entradas, «Aún no la has hecho».
  - Al tocarla se ve su historial.
- **Pantalla «Diario»** (desde la cabecera de Inicio, como dice el diseño general §6.3): cronología por meses, con foto, título, estrellas y nota. Tocar una entrada la edita o la borra, con «Deshacer».
- **Orden en Recetas:**
  - «Más cocinadas»: número de entradas;
  - «Mejor valoradas»: media; las que no tienen valoración van al final.
- **«Hace tiempo que no la haces»** en Inicio:
  - recetas cocinadas al menos una vez cuya última entrada tiene más de 30 días;
  - como mucho 6, las más antiguas primero;
  - si no hay ninguna, la sección no sale.

### 7.2 Despensa (2A)

- Es la pestaña interna **Despensa** de Compra (diseño general §6.3):
  - un campo «Añadir a la despensa» con autocompletado a partir de los ingredientes de las recetas, normalizados sin tildes ni plurales;
  - debajo, la lista alfabética, con borrar y «Deshacer».
- **«¿Qué puedo hacer?»:** las recetas ordenadas por el porcentaje de ingredientes disponibles, sin contar los básicos. Cada una muestra «Tienes 5 de 6 · falta: cilantro».
  - Una receta sin ingredientes no cuenta.
  - Coincidencia: el nombre normalizado de la despensa está contenido en el del ingrediente, o al revés, por palabras completas («tomate» cubre «tomate saladet»).
- **Filtro de Recetas «Con mi despensa»:** el 100 % de los ingredientes que no son básicos está en la despensa.
- **En Compra:**
  - los artículos que están en la despensa muestran «ya lo tienes» en `muted`;
  - al marcar un artículo como comprado, el aviso inferior ofrece «A la despensa». La acción añade el nombre normalizado a la despensa sin duplicarlo.

### 7.3 Hoy (2B)

Todo lo del diseño general §8.6 vale, con estos cambios y concreciones:

- **Estación:**
  - Si |lat| < 23,5°, o si no hay ubicación, hay dos estaciones. En el hemisferio norte, **secas** de noviembre a mayo y **lluvias** de junio a octubre; en el sur, al revés.
  - Fuera del trópico, las 4 estaciones meteorológicas.
  - Sin ubicación se asume el trópico norte (decisión de Claude: es el caso del usuario y no exige saber dónde está).
- **Clima:**
  - petición `forecast` de Open-Meteo con `current` (temperatura aparente, precipitación, `weather_code` e `is_day`) y `daily` de 7 días (código del tiempo, máximas y mínimas, probabilidad de lluvia, salida y puesta del sol);
  - `timezone=auto`;
  - la misma respuesta alimenta Hoy, el menú de la semana y el tema según el sol.
- **Ciudad:** el geocodificador de Open-Meteo (`geocoding-api.open-meteo.com`), en español, con 5 resultados («Ciudad, Estado, País»).
- **De temporada (2D):** una receta que usa un producto en su mejor momento del mes suma un poco.
- **Tarjeta Hoy en Inicio** (con el estilo de cada tema del diseño general §6.1):
  - el momento del día;
  - la línea del porqué («Hace 31 °C y hay sol: algo fresco»);
  - el plato principal grande;
  - la bebida y el postre (o lo que toque) como mini tarjetas;
  - «Otra idea»;
  - tocar una receta abre su detalle;
  - arriba, el clima pequeño (icono, temperatura y ciudad). Sin datos de clima no sale, y el porqué usa la hora y la estación.
- **Pocas recetas:**
  - con 0 recetas, «Cuando tengas recetas, aquí te propondré qué cocinar» y «+ Nueva receta»;
  - si falta la bebida o el postre, el hueco dice «Aún no tienes bebidas» y enlaza a crear una.
- **Lo ya mostrado:** «Otra idea» no repite nada mientras queden opciones. Los ids mostrados van en el `SavedStateHandle`; al acabarse las opciones, empieza otra vuelta.
- **Implementación:** el motor es lógica pura en `logic/.../today/`, con reloj, aleatoriedad (semilla) y datos inyectados, y TDD con los casos del diseño general §10.

### 7.4 Tema según el sol (2B)

- En Apariencia, con Automático, aparece «Noche según la salida y puesta del sol» si hay ubicación.
- La noche va de la puesta a la salida del sol de hoy, tomadas de la última previsión.
- Sin datos de menos de 3 días, se usan las horas fijas.

### 7.5 Maridaje por reglas (2B)

Todo lo del diseño general §8.7 («Reglas»), con estas concreciones:

- **Motor:** lógica pura en `logic/.../pairing/`.
  - Puntúa una pareja de perfiles con los 7 principios. Cada uno da una contribución y una frase.
  - Devuelve una puntuación de 0 a 100 y 1 o 2 frases, las de los principios que más pesan.
  - TDD: un caso por principio y casos combinados.
- **Catálogo** (`logic/src/main/resources/pairing/catalog.json`): unos 70 elementos con su perfil y tipo (bebida con o sin alcohol, acompañamiento o postre).
  - Incluye genéricos (tinto ligero, blanco seco, cerveza clara, cerveza oscura, té verde, café, agua mineral con limón, refresco de cola…) y **bebidas mexicanas**: agua de jamaica, de horchata, de tamarindo y de limón con chía; tejuino, café de olla, atole, champurrado, chocolate caliente, tequila blanco y reposado, mezcal, raicilla, michelada, pulque y tepache.
- **Guía de sabores** (`pairing/flavors.json`): unos 80 ingredientes comunes, con los ingredientes mexicanos habituales (chile poblano, chipotle, epazote, cilantro, nopal, maíz, frijol, aguacate, tomate verde, piloncillo, canela, vainilla…).
  - **«Le iría bien…»** en el detalle reúne, de los ingredientes de la receta, hasta 6 sabores que todavía no lleva.
- **En el detalle,** la sección «Maridaje» muestra:
  - las 3 mejores parejas (primero las recetas propias, luego el catálogo), cada una con su porqué y 👍/👎;
  - «Ver más» abre la pestaña Maridaje con esa receta elegida.
- **Pestaña Maridaje:**
  - arriba, «¿Con qué combina…?» con un buscador de recetas propias y de bebidas del catálogo (es el «al revés»: se parte de una bebida);
  - debajo, los resultados agrupados («Tus recetas» y «Ideas»);
  - tocar un resultado abre la **rueda comparativa**: las dos ruedas de sabor superpuestas, con la leyenda y las frases;
  - un botón «Menú completo» propone entrada, principal, postre y bebida que combinan entre sí, usando solo recetas propias y completando con el catálogo si falta algo;
  - «Historial»: las parejas valoradas, con su 👍/👎, que se pueden cambiar.
- **Aprende de ti:**
  - una pareja 👎 no vuelve a salir;
  - una pareja 👍 suma 15 puntos;
  - el peso de cada principio se multiplica por un factor entre 0,8 y 1,2, calculado a partir de las valoraciones donde ese principio fue el que más pesó (fórmula exacta para quien construye, con pruebas).

### 7.6 Menú de la semana (2C)

- Es la pestaña interna **Semana** de Compra.
  - La semana va de lunes a domingo, con flechas para cambiar de semana (empieza en la actual).
  - Cada día tiene Desayuno, Comida y Cena.
  - Un hueco vacío muestra «+»; uno lleno, la receta y sus porciones.
- Tocar un hueco: elegir receta (buscador), cambiar porciones o quitar.
- **«Rellenar con sugerencias»:**
  - solo rellena los huecos vacíos desde hoy hasta el domingo;
  - usa el motor de Hoy con la previsión de cada día; sin previsión, con la estación;
  - no repite la misma receta en la semana mientras haya otras;
  - ofrece «Deshacer».
- **«Generar lista de compra»:**
  - suma los ingredientes de los huecos desde hoy hasta el domingo, escalados a sus porciones;
  - quita lo que está en la despensa y los básicos;
  - lo funde con la lista (como «Lo que necesito») y avisa con «✓ N ingredientes añadidos».

### 7.7 Fichas para compartir (2C)

Sustituyen a «compartir como imagen» del diseño general.

- **Entrada:** el botón «Compartir» del detalle abre la pantalla **Ficha**: la vista previa arriba y las opciones debajo.
- **Formatos:**
  - **Texto:** el de la etapa 1.
  - **Imagen PNG:**
    - cuadrada 1080 × 1080;
    - vertical, de historias, 1080 × 1920;
    - tarjeta horizontal 1500 × 1000.
  - **PDF:**
    - A4 o Carta, en vertical; varias páginas si hace falta;
    - tarjeta de 10 × 15 cm en vertical; varias tarjetas si hace falta.
- **Diseños:**
  - **Clásico:** título con serifa, foto arriba, dos columnas.
  - **Minimalista:** solo texto, mucho aire, una línea de acento.
  - **Foto grande:** la foto a sangre con el título encima y los datos debajo.
  - **Tema:** los colores y las fuentes del tema activo.
  - Todos usan las fuentes incluidas en la app.
- **Opciones** (interruptores): foto, pasos, notas y perfil de sabor.
  - Ingredientes, título y porciones van siempre.
  - Las porciones son las elegidas en el detalle, con las cantidades escaladas.
  - El pie dice «Compartido desde Sazón».
- **Si no cabe:**
  - en las imágenes se reduce la letra hasta un mínimo legible; después se recortan los pasos con «…y N pasos más» y las notas con «…»;
  - en PDF se pasa a otra página.
- **Sin foto:** si la receta no tiene foto, el interruptor «Foto» se desactiva y el diseño «Foto grande» usa el emoji de la categoría sobre el color de acento.
- **Acciones:**
  - «Compartir» crea el archivo en `cacheDir/fichas/` (se vacía al iniciar la app) y lo comparte por `FileProvider` con `ACTION_SEND`;
  - «Guardar» usa `CreateDocument` (`Sazon-<titulo-sin-tildes>.png` o `.pdf`).
- **Memoria:** las últimas opciones elegidas se recuerdan (§6.3).
- **Implementación:**
  - la maquetación es lógica pura donde se pueda (qué cabe, qué se recorta, cómo se parte en páginas), en `logic/.../share/`, con pruebas;
  - la imagen se compone en Compose fuera de la pantalla y se captura con `GraphicsLayer`;
  - el PDF se dibuja con `PdfDocument` y `StaticLayout`.

### 7.8 Widgets (2D)

- **Dos widgets** con Glance, que siguen el tema claro u oscuro del sistema con los colores de Cálido y Nocturno.
  - **«Hoy»** (4 × 2): el momento del día, el plato propuesto y su porqué corto. Tocar abre el detalle; «Otra idea» cambia la propuesta en el widget.
  - **«Compra»** (4 × 2 o mayor): los artículos sin marcar; tocar el círculo los marca. El título abre Compra.
- **Cuándo se actualizan:**
  - «Compra», cuando cambia la lista (desde el repositorio);
  - «Hoy», con un trabajo de WorkManager cada hora mientras exista algún widget «Hoy», más al cambiar de momento del día y al abrir la app.
  - El trabajo usa el clima en caché y solo lo pide a Open-Meteo si tiene más de una hora y hay red.
- **Sin recetas,** «Hoy» invita a abrir la app.

### 7.9 Modo cocina manos libres (2D)

- **Botón «✋ Manos libres»** en la cabecera del modo cocina. Al activarlo, un panel explica los gestos y deja elegir gesto, voz o ambos.
  - La voz solo se ofrece si `SpeechRecognizer.isOnDeviceRecognitionAvailable` (Android 12+). Si no, el panel dice «La voz necesita Android 12 o más reciente; puedes usar el gesto».
- **Gesto:**
  - el sensor de proximidad pasa de «lejos» a «cerca» y otra vez a «lejos» en menos de 1,5 s: equivale a «Hecho ✓»;
  - después hay 1,5 s de pausa;
  - si se queda «cerca» más de 2 s (el teléfono boca abajo o en un bolsillo), no cuenta;
  - sin sensor, no se ofrece.
- **Voz:**
  - reconocimiento en el propio teléfono, en español (`es-MX`, o `es` si no hay `es-MX`), que se reinicia tras cada resultado;
  - órdenes: «siguiente» (o «listo» o «hecho»), «anterior» (o «atrás»), «temporizador» (arranca el del paso actual) y «repite» (lee el paso en voz alta con `TextToSpeech`);
  - se ignora cualquier otra frase;
  - el audio nunca sale del teléfono: no se usa el reconocedor de red ni como respaldo.
- **Respuesta visible:**
  - cada orden reconocida destella en la cabecera («✓ Siguiente»);
  - mientras escucha, un punto late junto a «✋»;
  - TalkBack anuncia la orden.
- **Cuándo se apaga:** al salir de la pantalla y en `ON_STOP`, para que nunca escuche en segundo plano. Al volver, sigue como estaba.
- **Implementación:** la lógica de gesto y órdenes es pura en `logic/.../cooking/handsfree/`: secuencias del sensor con tiempos y frases con su orden, con TDD.

### 7.10 Calendario de temporada (2D)

- **Datos** (`logic/src/main/resources/season/mexico.json`): unos 70 productos de los mercados de México (frutas, verduras, chiles y hierbas), con su emoji, sinónimos para buscar (elote/maíz, jitomate/tomate…) y, para cada mes, «en su mejor momento», «hay» o «no es temporada».
- **Pantalla «De temporada»:**
  - el acceso es una tarjeta pequeña en Inicio («De temporada en noviembre: guayaba, mandarina, calabaza…»);
  - arriba, los meses en una fila desplazable, con el actual elegido;
  - debajo, «En su mejor momento» y «También hay», agrupados en frutas y verduras;
  - cada producto dice cuántas recetas propias lo usan («3 recetas») y al tocarlo abre Recetas con esa búsqueda.
- **Hoy:** el mes actual da un pequeño empujón a las recetas de temporada (§7.3).
- **Implementación:** el emparejamiento de producto con ingrediente es lógica pura (nombre normalizado, sinónimos y palabras completas), con TDD.

### 7.11 Ajustes nuevos (2B y 2D)

- **«Ubicación y clima»:**
  - «Usar mi ubicación aproximada» o «Elegir ciudad»;
  - la ciudad actual;
  - los umbrales térmicos con «−/+»;
  - «Quitar ubicación».
- **«Horarios de comidas»:** cada momento con «−/+» de media hora, como las horas de noche.
- **«Despensa › Básicos»:** una lista editable.
- **«Acerca de»:** la atribución de Open-Meteo («Datos del tiempo: Open-Meteo.com, CC BY 4.0», con enlace).

## 8. Animaciones de la etapa 2 (2D, salvo que se diga otra cosa)

Valen las reglas de siempre: breves, sin bloquear el toque, con `LocalReducedMotion` (estado final al instante) y sin anunciarse en TalkBack.

- **Fondo de Hoy según el clima y la hora:**
  - gotas si llueve, rayos suaves si hay sol, nubes si está nublado y estrellas de noche;
  - va detrás de la tarjeta, con baja intensidad y sin bajar el contraste del texto (se comprueba con píxeles);
  - se mueve **3 s al entrar** y después se queda en un cuadro fijo (no hay animación continua).
- **«Otra idea»:** las tarjetas salen como cartas barajadas (inclinación y desplazamiento) y entran las nuevas, en unos 350 ms. Se puede tocar durante la animación.
- **Ruedas de maridaje:**
  - en la rueda comparativa, las dos ruedas se dibujan desde el centro (400 ms), una después de otra, y quedan superpuestas con transparencia de relleno;
  - las líneas y el texto van opacos;
  - entra en la entrega 2B, con la pantalla.
- **Despensa:** el ingrediente añadido cae hasta su sitio en la lista (unos 350 ms, con muelle).
- **Ficha:** la vista previa gira sobre su eje vertical (`rotationY`, unos 300 ms) al cambiar de diseño o de formato. Entra en la entrega 2C, con la pantalla.
- **Pantallas nuevas:** usan la entrada escalonada y los componentes animados de la etapa 1.

## 9. Calidad y pruebas

- **TDD en `logic/`:**
  - estación (dos y cuatro) y hemisferio;
  - motor de Hoy (cada entrada y la salida);
  - los 7 principios de maridaje, los pesos que aprenden y el menú completo;
  - coincidencia con la despensa y porcentaje;
  - estadísticas del diario;
  - el plan semanal y su lista de compra;
  - maquetación de la ficha;
  - gesto y órdenes de manos libres;
  - calendario de temporada;
  - lectura de las respuestas de Open-Meteo (JSON de ejemplo inventado);
  - la rotación con el tipo «antes de actualizar».
- **En `:app`:**
  - migración 1→2 (`MigrationTestHelper`) y copia previa (con éxito, con disco lleno simulado y con la migración fallida);
  - copias v2 de ida y vuelta e importación de copias v1;
  - pruebas Compose de cada pantalla nueva;
  - widgets con las pruebas de Glance (`glance-appwidget-testing`);
  - el cliente de Open-Meteo con un servidor falso (sin red real).
- **Por entrega:**
  - capturas en los tres temas (`CapturasTest`) y comparación con el estilo de los bocetos;
  - recorrido de extremo a extremo con `RecorridoEtapa2Test`, que amplía el de la etapa 1 con lo de cada entrega;
  - CI en verde;
  - fusión a `main` con permiso del usuario, y versión publicada en Releases.
- **Rendimiento:** con unas 500 recetas, Hoy y «¿Qué puedo hacer?» responden en menos de 200 ms en el hilo de fondo; la prueba usa datos sintéticos.

## 10. Solo se puede comprobar en un teléfono

- Ubicación aproximada y Open-Meteo reales.
- Widgets en el lanzador real.
- El sensor de proximidad y la voz en el teléfono, con el ruido de una cocina.
- `TextToSpeech`.
- La copia previa a la migración al instalar 0.2.0 encima de 0.1.0, con datos de verdad.
- El PDF abierto en otras apps y la imagen en WhatsApp.

## 11. Lo que se deja a quien construye

- Los pesos y umbrales exactos de Hoy y del maridaje, y la fórmula del ajuste de pesos, siempre que se cumplan los principios y las pruebas.
- El contenido del catálogo, la guía y el calendario, con los mínimos indicados y escritos de forma original.
- El aspecto de las pantallas sin boceto (Diario, Despensa, Semana, Maridaje, Ficha y De temporada), siguiendo el estilo de los bocetos aprobados.
- El orden de las tareas dentro de cada entrega.
