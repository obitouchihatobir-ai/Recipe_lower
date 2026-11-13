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
  src/main/java/app/recetario/   RecetarioApp (con AppContainer) y MainActivity; paquetes por función
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
- Los flujos `release.yml` y `create-signing-key.yml` solo se pueden lanzar cuando están en `main`; no se pueden probar desde la rama de trabajo (CI ensaya sus pasos con una llave de prueba). Lo que sube un flujo con `GITHUB_TOKEN` no lanza otros flujos: por eso «Crear llave de firma» lanza «Publicar APK» con `gh workflow run`.
- `api.open-meteo.com` está bloqueado por el proxy del contenedor. No afecta a la app (la llamada la hace el teléfono), pero no se puede probar desde aquí.
