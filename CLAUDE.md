# CLAUDE.md — Recetario (Recipe_lower)

## Qué es
Una app **personal de Android** para un solo usuario. Sirve para apuntar recetas propias de comidas, bebidas y postres, cocinarlas con un modo paso a paso, hacer la lista de compra, recibir sugerencias del día (según la hora, la estación y el clima) y ver maridajes (con reglas propias y, opcionalmente, con IA de Claude).

El diseño aprobado está en `docs/superpowers/specs/2026-11-13-recetario-design.md`, con los bocetos HTML en `docs/superpowers/specs/2026-11-13-recetario-bocetos/`. **Léelo antes de tocar nada.** La app se entrega en 3 etapas (§5 del diseño).

## Estado actual
- Fase de diseño: todavía no hay código de la app.
- Las tareas de implementación vivirán en `docs/superpowers/plans/`.

## Estructura
```
docs/superpowers/specs/   Diseños aprobados y sus bocetos
docs/superpowers/plans/   Planes de implementación (cuando existan)
.superpowers/             Bocetos temporales de las sesiones (ignorado por git)
```
Cuando exista el proyecto Android, documenta aquí sus módulos y paquetes.

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
Pendiente hasta que exista el proyecto Gradle. Documenta aquí los comandos (por ejemplo `./gradlew test`, `./gradlew assembleRelease`) en cuanto existan.

## Claves y secretos (sin valores reales, nunca)
- **El repositorio es PÚBLICO.** No subas claves, contraseñas, llaves de firma en claro ni datos personales.
- **Firma del APK:** el secreto de GitHub `SIGNING_PASSPHRASE` descifra la llave de firma, que en el repositorio solo se guarda cifrada (§9 del diseño). Todas las versiones deben firmarse con la misma llave; si cambia, las actualizaciones no se instalan encima.
- **Clave de Anthropic (IA):** la pega el usuario dentro de la app y se guarda cifrada en el teléfono. Nunca va en el código, en las copias de seguridad ni en el repositorio.
- **Open-Meteo:** no necesita clave. Es gratis para uso no comercial y exige atribución CC BY 4.0 en «Acerca de».

## Problemas conocidos / notas del entorno
- En el contenedor de Claude Code en la nube no hay SDK de Android instalado (sí Java 21 y Gradle 8.14). Se descarga desde `dl.google.com`, que es accesible.
- `api.open-meteo.com` está bloqueado por el proxy del contenedor. No afecta a la app (la llamada la hace el teléfono), pero no se puede probar desde aquí.
