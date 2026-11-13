# Recetario personal para Android — Diseño

Fecha: 2026-11-13 · Estado: borrador para aprobación

Bocetos aprobados (abrir en el navegador): `docs/superpowers/specs/2026-11-13-recetario-bocetos/`
- `estilo-visual.html`: los tres temas sobre la pantalla de Inicio.
- `apariencia.html`: cambio de tema y modo Automático.
- `receta-y-cocina.html`: detalle de receta y modo cocina (se eligió el **B · Lista**).

En este documento, «(decisión de Claude)» marca lo que propuse yo y el usuario no decidió de forma explícita. Puede corregirlo.

---

## 1. Intención

Un recetario **personal**, para un solo usuario, donde apuntar recetas propias de **comidas, bebidas y postres** y tenerlas siempre a mano en el teléfono. Debe ser **práctico, bonito y minimalista, con animaciones**, y **muy completo**. Además de guardar recetas, la app ayuda a decidir **qué cocinar hoy** (según la hora, la estación y el clima) y **qué combina con qué** (maridaje).

Por qué: el usuario quiere un lugar propio, agradable de usar, donde sus recetas no se pierdan y que además le dé ideas.

## 2. Objetivos, no-objetivos y anti-objetivos

**Objetivos**
- Apuntar una receta completa rápido: fotos (portada y por paso), ingredientes con cantidades, pasos, tiempos y perfil de sabor.
- Encontrar cualquier receta en segundos: búsqueda por nombre o ingrediente y filtros.
- Cocinar cómodamente con el teléfono en la encimera: modo cocina con letra grande, pantalla siempre encendida y temporizadores.
- Saber qué hay que comprar para una receta o para la semana.
- Recibir sugerencias del día y maridajes razonados.
- Que las recetas nunca se pierdan: copias de seguridad fáciles y actualizaciones que no borran datos.

**No-objetivos** (fuera de alcance)
- iPhone, versión web o tablet como objetivo de diseño.
- Cuentas de usuario, sincronización entre dispositivos y servidor propio.
- Compartir el recetario con otras personas, funciones sociales o publicar en Google Play.
- Información nutricional y calorías.
- Otros idiomas aparte del español.

**Anti-objetivos** (cosas que la harían un fracaso aunque funcione)
- **Perder recetas.** Una actualización, migración o importación que borre datos es el peor fallo posible.
- Una app recargada o lenta. Lo minimalista manda: cada pantalla tiene un propósito claro.
- Animaciones que estorban al cocinar: largas, que bloquean el toque o que marean.
- Depender de internet para lo básico (crear, ver y cocinar recetas, lista de compra).
- Que la IA envíe datos sin que el usuario lo pida.
- Que una clave, una contraseña o un dato personal acaben en el repositorio (es **público**).

## 3. Restricciones

- **Plataforma:** Android, app nativa. Versión mínima Android 8.0, API 26 (decisión de Claude).
- **Idioma de la interfaz:** español. Sistema métrico por defecto, con conversor.
- **Datos:** solo en el teléfono (base de datos local + fotos en el almacenamiento interno de la app). Funciona sin conexión.
- **Internet solo para:** el clima (Open-Meteo), la IA (Claude, opcional) e importar recetas desde un enlace.
- **Repositorio público** (`obitouchihatobir-ai/Recipe_lower`): ningún secreto en el código ni en el historial de git.
- **Coste:** gratis, salvo la IA, que paga el usuario por uso con su propia clave de Anthropic.
- **Open-Meteo:** gratuito para uso no comercial y sin clave. Exige atribución CC BY 4.0, que va en «Acerca de».
- **Distribución:** APK instalado a mano. Todas las versiones deben firmarse con la **misma llave** para que se instalen encima sin perder datos.

## 4. Enfoques considerados

| Decisión | Opciones | Elegida | Por qué |
|---|---|---|---|
| Dónde viven los datos | A) En el teléfono con copia de seguridad · B) En la nube con cuenta (Firebase) | **A** (usuario) | Más simple, sin cuentas y sin internet. B obligaba a crear un proyecto Firebase y añadía mucha complejidad para un único usuario. |
| Tecnología | Kotlin + Jetpack Compose · Flutter · React Native · web instalable (PWA) | **Kotlin + Compose** (decisión de Claude, aceptada) | Mejores animaciones e integración con Android (temporizadores con pantalla apagada, cámara, pantalla encendida). Flutter y React Native solo compensan si se quiere iPhone, que no es el caso. Una PWA no tiene temporizadores ni avisos fiables. |
| Maridaje | A) Reglas propias sin conexión · B) IA (Claude) · C) Ambas | **C** (usuario) | Las reglas funcionan siempre, gratis y sin internet; la IA, cuando el usuario quiere algo más fino. Se descartaron fuentes externas: las bases de datos de sabores (Flavor Network, FlavorDB) son químicas y no recomiendan bebidas, y Spoonacular solo sugiere vinos, en inglés y con un plan gratuito muy limitado. |
| Modo cocina | A) Un paso por pantalla · B) Lista con el paso actual resaltado | **B** (usuario; Claude recomendaba A) | El usuario prefiere ver todos los pasos a la vez. |
| Apariencia | Un solo tema · tres temas | **Tres temas + Automático** (usuario) | Cálido y Fresco son claros; Nocturno es el modo oscuro. |
| Cómo llega la app | A) GitHub Actions construye y publica el APK firmado · B) APK enviado por el chat | **A** (usuario) | Con B la llave de firma se perdería al cerrar la sesión y cada actualización obligaría a desinstalar y restaurar. |

## 5. Etapas de entrega

Cada etapa termina con un **APK instalable y usable** publicado en GitHub Releases, que se instala encima del anterior sin perder datos.

1. **Lo esencial:** recetas completas con fotos, ingredientes, perfil de sabor, etiquetas y categorías propias; buscar, filtrar y favoritas; detalle de receta; modo cocina (B) con temporizadores y porciones; lista de compra; conversor de medidas; compartir como texto; copias de seguridad; tres temas con Automático y animaciones; ajustes; la cadena de GitHub (compilación, firma y publicación).
2. **La app te ayuda:** sección Hoy; maridaje por reglas con sus extras (inverso, menú completo, rueda comparativa, valoraciones, sabores sugeridos); despensa; diario de cocina; menú de la semana; compartir una receta como imagen.
3. **Conectada:** IA (maridaje fino y «Algo nuevo» en Hoy) e importar recetas desde un enlace.

## 6. Diseño visual

### 6.1 Temas
Los tokens salen de los bocetos (`estilo-visual.html`, `apariencia.html`).

| | A · Cálido (claro) | B · Fresco (claro) | C · Nocturno (oscuro) |
|---|---|---|---|
| Fondo | `#F6EFE6` | `#FFFFFF` | `#141416` |
| Superficie | `#FFFAF3` | `#F3F7F2` | `#1E1E22` |
| Texto | `#2B2118` | `#1E2A22` | `#F2EDE4` |
| Texto secundario | `#8A7A6A` | `#7A887E` | `#9A948A` |
| Líneas | `#E8DACB` | `#E2EAE2` | `#2C2C31` |
| Acento | `#C2563A` (terracota) | `#4E8A5C` (salvia) | `#E0A458` (ámbar) |
| Títulos | Fraunces 600 | Nunito 800 | Playfair Display 600 |
| Cuerpo | Inter | Nunito | Inter |
| Radio base | 18 dp | 24 dp | 16 dp |
| Tarjeta «Hoy» | Degradado terracota, texto crema | Verde muy claro | Degradado oscuro con borde ámbar |

- Las fuentes (todas con licencia OFL) **van incluidas en la app**, para que funcionen sin conexión.
- **Ajustes › Apariencia:** Cálido, Fresco, Nocturno y el interruptor **Automático**. Con Automático, la app usa el tema claro elegido (Cálido o Fresco) de día y Nocturno de noche o cuando el sistema está en modo oscuro.
  - La «noche» va de 20:00 a 7:00, configurable, en la etapa 1. Desde la etapa 2 puede usar la puesta y la salida del sol que da Open-Meteo (decisión de Claude).
- La primera vez que se abre la app arranca en **Automático con Cálido de día** (decisión de Claude).
- Todos los textos deben cumplir un contraste mínimo de 4,5:1 en los tres temas.

### 6.2 Animaciones
Breves (normalmente de 150 a 450 ms), con física de muelle suave, sin bloquear nunca el toque y respetando la opción del sistema «quitar animaciones».
- Entrada escalonada de los elementos de cada pantalla (se desplazan hacia arriba mientras aparecen).
- Transición compartida: la foto de una tarjeta se expande hasta la cabecera del detalle.
- Cambio de tema con una revelación circular desde el punto tocado.
- Chips, casillas (la marca aparece con un pequeño rebote), botón «+» (escala y gira al pulsarlo), favorito (late), reordenación animada de listas.
- La rueda de sabores se dibuja creciendo desde el centro y las barras de la leyenda se llenan.
- Cambio de porciones: la cantidad modificada destella en el color de acento.
- Modo cocina: el paso actual se expande y la lista se desplaza sola de forma suave.

### 6.3 Navegación
La barra inferior tiene 4 pestañas: **Inicio · Recetas · Compra · Maridaje**. Hay un botón flotante «+» para crear una receta en Inicio y en Recetas.
- **Inicio:** saludo, clima, tarjeta Hoy (etapa 2), chips de categorías, recientes, favoritas y «hace tiempo que no la haces» (etapa 2). Desde la cabecera se llega a Ajustes y al diario.
- **Recetas:** búsqueda, filtros y vista en cuadrícula o en lista.
- **Compra:** pestañas internas **Lista · Despensa · Semana** (Despensa y Semana llegan en la etapa 2).
- **Maridaje:** elegir una receta o una bebida y ver con qué combina. También el menú completo y el historial de maridajes valorados (etapa 2). En la etapa 1 la pestaña muestra un estado vacío que anuncia la función.
- **Ajustes:** apariencia, horarios de comidas, ubicación y clima, unidades, categorías, etiquetas, copias de seguridad, IA y «Acerca de» (con créditos y versión).

Dónde vive el conversor (en el detalle, al tocar una cantidad, y/o en Compra) lo decide quien construye la app.

## 7. Datos

Base de datos local (Room/SQLite) más archivos de imagen en el almacenamiento interno de la app. Las preferencias se guardan con DataStore.

**Categoría:** nombre, emoji o icono, color, orden y **tipo base**: `COMIDA`, `BEBIDA`, `POSTRE` u `OTRO`. Vienen tres de serie: Comidas, Bebidas y Postres. El usuario crea las suyas (Salsas, Cócteles…) eligiendo un tipo base, que es el que usan Hoy y el maridaje (decisión de Claude).

**Receta**
- Título, categoría, foto de portada, descripción breve opcional y tiempo total en minutos.
- Porciones base y dificultad (Fácil, Media o Difícil).
- **Perfil de sabor:** dulce, salado, ácido, amargo y picante, cada uno de 0 a 3. Además, intensidad (ligera, media o intensa) y si es **rica o cremosa** (sí/no; decisión de Claude, la usa el maridaje: «la acidez equilibra la grasa»).
- **Se sirve:** caliente, fría o a temperatura ambiente.
- **Momentos del día** (varios a la vez): desayuno, comida, merienda, cena, aperitivo o noche.
- Solo para bebidas: con o sin alcohol.
- Solo para comidas: tipo de plato (entrada, principal o acompañamiento; principal por defecto; decisión de Claude, lo usa el menú completo).
- Etiquetas, notas libres, favorita, enlace de origen (si se importó), si la generó la IA, y fechas de creación y modificación.
- Si el usuario no rellena el momento del día o la temperatura de servicio, se deducen de la categoría y se muestran como sugerencia editable.

**Filtro «dulce / salada»** (petición explícita del usuario para las bebidas, aunque sirve para todo):
- Es **dulce** si dulce ≥ 2 y dulce ≥ salado.
- Es **salada** si salado ≥ 2 y salado > dulce.
- Al editar una bebida hay atajos «Dulce» y «Salada» que ajustan esos dos valores.

**Ingrediente** (de una receta): orden, nombre, cantidad decimal (puede quedar vacía), unidad y nota («picada»). Sección opcional dentro de la receta («Para la salsa»).
- Unidades: g, kg, ml, l, taza, cucharada, cucharadita, unidad, diente, pizca, al gusto, oz, lb, fl oz, y unidad libre.

**Paso:** orden, texto, **fotos** (0 a 4 por paso; decisión de Claude) y temporizador opcional en segundos.
- Al escribir «20 min» en el texto, la app propone crear el temporizador.
- Cada paso indica qué ingredientes usa. La app los sugiere comparando nombres y el usuario puede corregirlos.

**Diario (etapa 2):** receta, fecha, valoración de 1 a 5, nota y fotos.

**Despensa (etapa 2):** nombre del ingrediente y nombre normalizado para comparar.

**Artículo de la compra:** nombre, cantidad, unidad, sección del súper, marcado o no y receta de origen (opcional).

**Menú de la semana (etapa 2):** fecha, momento del día, receta y porciones.

**Valoración de maridaje (etapa 2):** la pareja (receta + receta o receta + sugerencia genérica), si gustó y la fecha.

**Resultado de la IA (etapa 3):** receta, tipo (maridaje o «algo nuevo»), contenido y fecha. Se guarda para verlo sin conexión.

**Seguridad de los datos**
- Las migraciones de la base de datos **nunca son destructivas**. Cada una tiene su prueba.
- Antes de migrar el esquema se hace una copia local automática (decisión de Claude).

## 8. Funciones

### 8.1 Recetas (etapa 1)
- **Editor:** foto de portada (cámara o galería) y campos agrupados en secciones plegables: lo básico, sabor, ingredientes, pasos y notas.
  - El perfil de sabor se rellena con 5 chips; cada toque sube el nivel de 0 a 3, con una mini rueda que se actualiza en vivo.
  - Ingredientes y pasos se pueden reordenar arrastrando.
  - Se guarda un borrador si se sale sin guardar.
- **Fotos:** se comprimen (lado mayor de 1600 px como máximo) y se guardan en el almacenamiento interno.
- **Detalle:** como en el boceto. Cabecera con foto, datos rápidos y etiquetas, rueda de sabores, ingredientes con selector de porciones y casillas, «Lo que necesito → a la compra», pasos con miniaturas, maridaje (desde la etapa 2) y notas. Abajo, el botón fijo «Modo cocina».
- **Porciones:** las cantidades se escalan con la proporción.
  - g y ml pasan a kg y l al llegar a 1000.
  - Las unidades contables usan fracciones (¼, ½, ¾).
  - «Pizca» y «al gusto» no se escalan.
  - Se respeta el singular y el plural (diente/dientes).
- **Buscar y filtrar:**
  - Búsqueda por título, ingrediente o etiqueta, sin distinguir tildes ni mayúsculas.
  - Filtros: categoría, etiquetas, dulce o salada, con o sin alcohol, caliente o fría, tiempo máximo, momento del día, dificultad y favoritas. Desde la etapa 2, también «puedo hacerla con mi despensa».
  - Orden: recientes, alfabético, más cocinadas o mejor valoradas.
- **Compartir:** como texto con formato, listo para WhatsApp (etapa 1), y como imagen de la receta (etapa 2).

### 8.2 Modo cocina (etapa 1, opción B)
- Mantiene la pantalla encendida.
- Arriba hay una sección plegable de ingredientes para tachar mientras se prepara todo.
- Debajo, la lista de pasos:
  - El **paso actual se expande** con letra grande, sus fotos (en carrusel si hay varias), los ingredientes que usa con las cantidades ya escaladas y su temporizador.
  - Al pulsar **«Hecho ✓»**, el paso se marca, el siguiente se expande y la lista se desplaza sola.
  - Tocar un paso anterior permite volver a él.
- Al terminar aparece «¡Listo!». Desde la etapa 2 ofrece guardarlo en el diario con fecha, valoración, foto y nota.
- **Temporizadores:**
  - Puede haber varios a la vez. Siguen corriendo con la app en segundo plano o la pantalla apagada (servicio en primer plano con notificación).
  - Al acabar suenan y vibran.
  - Se pueden pausar y sumar un minuto.

### 8.3 Lista de compra (etapa 1; integración con la despensa en la etapa 2)
- «Lo que necesito» añade los ingredientes **no marcados**, escalados a las porciones elegidas, y avisa con un mensaje como «✓ N ingredientes añadidos».
- La lista general junta los duplicados: mismo nombre normalizado y unidades compatibles se suman; si no, quedan en líneas separadas.
- Se agrupa por **sección del súper** (frutas y verduras, lácteos, carnes y pescados, despensa, bebidas, otros) con un diccionario básico incluido en la app. Cada artículo se puede cambiar de sección a mano.
- Se pueden añadir artículos a mano, marcarlos, borrarlos deslizando, vaciar los marcados y compartir la lista como texto.
- Desde la etapa 2:
  - Lo que está en la despensa aparece como «ya lo tienes».
  - Al marcar un artículo como comprado se puede pasar a la despensa.

### 8.4 Conversor de medidas (etapa 1)
- Entre unidades de volumen o entre unidades de peso: conversión exacta. La taza es de 240 ml, la cucharada de 15 ml y la cucharadita de 5 ml (decisión de Claude, configurable).
- Entre volumen y peso: con una tabla de densidades de ingredientes comunes (harina, azúcar, arroz, mantequilla, leche, miel…). Si el ingrediente no está en la tabla, la app avisa de que no puede convertir.
- Disponible como herramienta independiente y desde las cantidades de una receta.

### 8.5 Copias de seguridad (etapa 1)
- **Exportar:** un único `.zip` (por ejemplo `Recetario-2026-11-13.zip`) con un JSON de todos los datos, con versión de esquema, y la carpeta de imágenes. El usuario elige dónde guardarlo (Google Drive, Descargas…).
- **Importar:** «Reemplazar todo» (con confirmación, y después de una copia automática de lo actual) o «Combinar» (sin duplicar; identificadores estables, UUID).
- **Copia automática semanal** a una carpeta elegida por el usuario. Si falla, la app avisa (decisión de Claude).
- **Recordatorio** en Inicio si la última copia tiene más de 30 días (decisión de Claude).
- La clave de la IA **nunca** se incluye en las copias.
- La copia automática de Android excluye la clave. Si se activa para la base de datos y las preferencias, lo decide quien construye la app.

### 8.6 Hoy (etapa 2; «Algo nuevo» con IA en la etapa 3)

**Entradas**
- **Hora → momento del día**, según horarios configurables. Por defecto: desayuno de 7 a 11, comida de 13 a 16, merienda de 16 a 19, cena de 19 a 23 y noche de 23 a 7.
- **Estación**, según la fecha y el hemisferio (signo de la latitud), con estaciones meteorológicas. En latitudes tropicales (|lat| < 23,5°) la estación pesa poco y manda el clima.
- **Clima actual** (Open-Meteo): temperatura aparente, precipitación y código del tiempo.
  - Ubicación: aproximada, con el permiso de ubicación aproximada, o una ciudad escrita a mano mediante el geocodificador gratuito de Open-Meteo.
  - Se guarda una hora en caché. Sin conexión se usa el último dato si tiene menos de 6 horas; si no, solo la estación.
  - Clase térmica según la temperatura aparente: frío por debajo de 15 °C, templado de 15 a 24 °C y calor por encima de 24 °C. Los umbrales son ajustables.
- **Día de la semana:** entre semana se priorizan las recetas de 40 minutos o menos para comida y cena; el fin de semana se permiten las largas.
- **Despensa:** porcentaje de los ingredientes de la receta que hay en casa, sin contar básicos como sal, agua y aceite (lista configurable).
- **Diario:** sube lo que lleva tiempo sin hacerse y penaliza lo hecho en los últimos 2 días. También cuentan favoritas y valoraciones.
- Una pequeña variación aleatoria da variedad, y **«Otra idea»** vuelve a sortear sin repetir lo ya mostrado.

**Salida: el menú de hoy**
- Comida y cena: plato principal más bebida y postre que **combinen con él** (usa el motor de maridaje).
- Desayuno y merienda: plato y bebida.
- Noche: bebida y algo para picar.
- Incluye una línea que explica la elección, por ejemplo: «Hace 9 °C y llueve: algo caliente y reconfortante».
- **Si hay pocas recetas:** se muestra lo que hay e invita a añadir más o a pedir «Algo nuevo» si la IA está activada. Este estado vacío tiene que estar bien diseñado.

### 8.7 Maridaje

**Reglas (etapa 2)**
- Puntúan parejas a partir de los perfiles. Principios:
  - Intensidades parecidas.
  - La acidez equilibra lo rico o cremoso.
  - La bebida de un postre debe ser igual de dulce o más.
  - El picante pide frescor o algo de dulzor y evita el alcohol fuerte y lo muy amargo.
  - Amargo con amargo se potencia (se evita) y el amargo con algo dulce se equilibra.
  - Lo salado combina con lo ácido y con un contraste dulce.
  - La temperatura de servicio se ajusta al contexto.
  - Cada principio puntúa la complementariedad y el contraste.
- **Resultado:** primero, parejas de **recetas del propio recetario** (bebida para un plato, plato o postre para una bebida, bebida para un postre). Después, **sugerencias genéricas** de un catálogo incluido en la app (al menos unas 60 bebidas y acompañamientos con su perfil: tintos ligeros, cervezas, aguas frescas, tés, cafés, refrescos…).
- Cada sugerencia muestra 1 o 2 frases con el **porqué**, generadas a partir de los principios aplicados.
- **Al revés:** se parte de una bebida.
- **Menú completo:** entrada, principal, postre y bebida que combinan entre sí.
- **Rueda comparativa:** dos ruedas superpuestas que muestran por qué encajan.
- **Aprende de ti:** cada sugerencia se valora con 👍 o 👎.
  - Las parejas que gustan suben y las que no, no vuelven a salir.
  - Los pesos de los principios se ajustan un poco según las valoraciones.
- **Sabores sugeridos:** una guía incluida con unos 80 ingredientes comunes y los sabores que combinan con cada uno (por ejemplo, calabaza con canela, nuez moscada, salvia o jengibre). En el detalle aparece como «Le iría bien…», a partir de los ingredientes de la receta.
- El contenido del catálogo y de la guía debe ser **original**, escrito a partir de conocimiento culinario general, sin copiar libros ni bases de datos con derechos.

**IA (etapa 3)**
- **«Sugerir maridaje con IA»** en el detalle y en la pestaña Maridaje.
  - Envía la receta (título, categoría, perfil, ingredientes y pasos; **nunca fotos**), los títulos y perfiles de las recetas del usuario que podrían acompañarla, y sus preferencias aprendidas.
  - Recibe un JSON estructurado con sugerencias (primero recetas propias, luego ideas nuevas), el porqué de cada una y sabores o ingredientes que mejorarían la receta.
  - El resultado se guarda en la receta para verlo sin conexión.
- **«Algo nuevo» en Hoy:** envía el contexto del momento (franja horaria, estación, clima, despensa, títulos existentes para no repetir) y recibe una receta completa en JSON. Se ve en una vista previa y se guarda con un toque, marcada como generada por la IA.
- **Solo se envía algo cuando el usuario pulsa el botón.**
- **Configuración:** el usuario pega su clave de Anthropic en Ajustes › IA.
  - La clave se guarda cifrada con el almacén de claves de Android y queda fuera de las copias.
  - Sin clave, los botones de IA no aparecen y nada se rompe.
- **Detalles técnicos:**
  - API de Claude con el SDK oficial de Java de Anthropic, si funciona en Android con API 26. Si no, HTTP directo contra `POST /v1/messages`, con el motivo documentado.
  - Modelo `claude-opus-5-5`, pensamiento adaptativo y salida estructurada (`output_config.format`).
  - Respaldo en el servidor ante negativas: `fallbacks: "default"` con la beta `server-side-fallback-2026-07-01`. Siempre se comprueba `stop_reason` antes de leer el contenido.
  - Coste estimado: de 2 a 5 centavos de dólar por consulta.
  - Errores claros cuando no hay conexión, la clave no es válida, no queda saldo o se supera el límite de peticiones.

### 8.8 Despensa, diario y menú de la semana (etapa 2)
- **Despensa:** añadir rápido con autocompletado a partir de los ingredientes de las recetas. **«¿Qué puedo hacer?»** ordena las recetas por porcentaje de ingredientes disponibles.
- **Diario:** se rellena al terminar en el modo cocina o a mano. Se ve en el detalle de la receta («La hiciste 3 veces · última hace 2 semanas · ★★★★☆») y como cronología con fotos.
- **Menú de la semana:**
  - Vista de 7 días con desayuno, comida y cena; cada hueco se asigna a mano.
  - **«Rellenar con sugerencias»** usa el motor de Hoy con la previsión del tiempo de Open-Meteo para los próximos días.
  - **«Generar lista de compra»** suma los ingredientes de la semana menos lo que hay en la despensa.

### 8.9 Importar desde un enlace (etapa 3)
- El usuario pega una URL o la comparte desde el navegador hacia la app.
- La app descarga la página y lee los datos estructurados de receta (schema.org `Recipe` en JSON-LD), que tienen la mayoría de webs de recetas. Si no los hay y la IA está activada, envía el texto de la página a Claude para extraer la receta.
- El resultado **siempre pasa por el editor** para revisarlo antes de guardar. Se descarga la foto principal si existe y se guarda el enlace de origen.

## 9. Compilación, firma y publicación (GitHub)

- **Integración continua:** en cada push y pull request se compila y se pasan las pruebas unitarias.
- **Publicación:** al crear una etiqueta `vX.Y.Z`, GitHub Actions compila el APK de release firmado y lo publica en **GitHub Releases** con notas en español. El usuario lo descarga desde el móvil.
- **Llave de firma** (decisión de Claude, pensada para que el usuario solo cree **un** secreto y la llave nunca pase por el chat):
  1. El usuario crea en GitHub el secreto `SIGNING_PASSPHRASE`, una frase larga inventada por él, guiado paso a paso.
  2. Un flujo de Actions que se lanza a mano una sola vez («Crear llave de firma») genera la llave con esa frase, la **cifra** (AES-256) y guarda solo el archivo cifrado en el repositorio.
  3. Los flujos de publicación descifran la llave con el secreto para firmar.
  4. Se avisa al usuario de que **guarde la frase** en un lugar seguro: sin ella, las versiones futuras no podrían instalarse encima.
  - Quien construye la app puede usar otro mecanismo equivalente si mantiene estas propiedades: un solo secreto, nada en claro en el repositorio y nada en el chat.
- **Versionado:** `versionCode` crece en cada publicación.
- Se incluye una guía breve en español para instalar el APK, permitiendo «instalar apps de origen desconocido» en el navegador.

## 10. Calidad y pruebas

- Desarrollo guiado por pruebas en la lógica, con pruebas unitarias para:
  - escalado y formato de cantidades;
  - conversión de unidades;
  - fusión de la lista de compra;
  - filtro dulce/salada;
  - puntuación de Hoy;
  - reglas de maridaje;
  - estación y hemisferio;
  - ida y vuelta de las copias (exportar → importar → datos idénticos).
- Pruebas de migración de la base de datos.
- Pruebas de interfaz de los flujos clave: crear una receta, cocinarla, mandarla a la compra, cambiar el tema.
- Accesibilidad: tamaño de letra dinámico, áreas táctiles de 48 dp como mínimo, descripciones para lectores de pantalla y el contraste del §6.1.
- Rendimiento: la lista de recetas debe ir fluida con unas 500 recetas con fotos (miniaturas en caché).

## 11. Lo que se deja a quien construye la app

- La arquitectura interna (capas, inyección de dependencias, módulos), las bibliotecas concretas (carga de imágenes, serialización) y el `targetSdk`.
- El ajuste fino de umbrales y pesos de Hoy y del maridaje, siempre que se cumplan los principios descritos.
- El contenido del catálogo de maridaje, la guía de sabores, el diccionario de secciones del súper y la tabla de densidades, con los mínimos indicados.
- Las pantallas que no tienen boceto (editor, lista de compra, Maridaje, Ajustes, Despensa, Semana, Diario). Deben seguir el estilo y las animaciones de los bocetos aprobados.
- Dónde poner el conversor y otros accesos secundarios.
- Si incluir «Buscar actualizaciones» (consultar la última versión en GitHub Releases).
