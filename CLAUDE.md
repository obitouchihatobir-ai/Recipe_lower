# CLAUDE.md — Recetario (Recipe_lower)

## Qué es
Una app **personal de Android** para un solo usuario. Sirve para apuntar recetas propias de comidas, bebidas y postres, cocinarlas con un modo paso a paso, hacer la lista de compra, recibir sugerencias del día (según la hora, la estación y el clima) y ver maridajes (con reglas propias y, opcionalmente, con IA de Claude).

El diseño aprobado está en `docs/superpowers/specs/2026-11-13-recetario-design.md`, con los bocetos HTML en `docs/superpowers/specs/2026-11-13-recetario-bocetos/`. **Léelo antes de tocar nada.** La app se entrega en 3 etapas (§5 del diseño).

## Estado actual
- Etapa 1 en curso según `docs/superpowers/plans/2026-11-13-recetario-etapa-1.md` (léete también su «Enmienda 1»).
- Ya existe el proyecto Gradle: `:app` (Android) y el build incluido `logic/` (Kotlin puro), con integración continua.

## Estructura
```
app/                      Módulo Android :app (Compose, Room, DataStore…), paquete app.recetario
  src/main/java/app/recetario/   RecetarioApp (con AppContainer) y MainActivity; paquetes por función
  src/test/                      Pruebas en la JVM con Robolectric (sdk 35, robolectric.properties)
logic/                    Build incluido, Kotlin/JVM sin Android: lógica pura y sus pruebas
                          (artefacto app.recetario:logic, que :app consume)
gradle/libs.versions.toml Catálogo de versiones, compartido por la raíz y por logic/
.github/workflows/ci.yml  Integración continua
scripts/                  setup-android-sdk.sh (instala el SDK en $HOME/android-sdk)
docs/superpowers/specs/   Diseños aprobados y sus bocetos
docs/superpowers/plans/   Planes de implementación
.superpowers/             Bocetos temporales de las sesiones (ignorado por git)
```

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
- **CI** (`.github/workflows/ci.yml`): en cada push y pull request ejecuta los dos comandos anteriores y, si algo falla, sube los informes de prueba como artefacto `informes-de-prueba`.
- Versiones: AGP 9.5.1 con Kotlin integrado (no se aplica `org.jetbrains.kotlin.android`), Kotlin 2.4.21, KSP 2.3.13, `compileSdk`/`targetSdk` 37, `minSdk` 26. Todas en `gradle/libs.versions.toml`.

## Claves y secretos (sin valores reales, nunca)
- **El repositorio es PÚBLICO.** No subas claves, contraseñas, llaves de firma en claro ni datos personales.
- **Firma del APK:** el secreto de GitHub `SIGNING_PASSPHRASE` descifra la llave de firma, que en el repositorio solo se guarda cifrada (§9 del diseño). Todas las versiones deben firmarse con la misma llave; si cambia, las actualizaciones no se instalan encima.
- **Clave de Anthropic (IA):** la pega el usuario dentro de la app y se guarda cifrada en el teléfono. Nunca va en el código, en las copias de seguridad ni en el repositorio.
- **Open-Meteo:** no necesita clave. Es gratis para uso no comercial y exige atribución CC BY 4.0 en «Acerca de».

## Problemas conocidos / notas del entorno
- En el contenedor de Claude Code en la nube no hay SDK de Android (sí Java 21) y no se puede aceptar su licencia. Allí solo se prueba `logic/` en local; `:app` se verifica en GitHub Actions haciendo push a la rama de trabajo.
- Maven Central responde a veces `429 Too Many Requests` a través del proxy del contenedor. Basta con repetir el comando de Gradle.
- `api.open-meteo.com` está bloqueado por el proxy del contenedor. No afecta a la app (la llamada la hace el teléfono), pero no se puede probar desde aquí.
