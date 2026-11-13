# Instalar Recetario en tu móvil

Recetario no está en Google Play. GitHub la compila, la firma con tu llave y la deja en la página **Releases** del repositorio. Desde ahí la descargas al móvil.

Los pasos 1 y 2 se hacen **una sola vez**, mejor desde un ordenador. Después, para instalar y actualizar, solo necesitas el móvil (pasos 3 a 5).

> **Antes de empezar:** el código tiene que estar ya en la rama `main`. Los flujos de GitHub Actions solo aparecen en la pestaña **Actions** cuando están en `main`. Hasta que hagas el paso 2, el flujo **Publicar APK** fallará con el aviso «Falta la llave: ejecuta primero el flujo «Crear llave de firma»». Es normal.

## 1. Crea tu frase secreta (una sola vez)

1. Inventa una frase larga, de **20 caracteres o más**. Por ejemplo, cuatro o cinco palabras que no tengan nada que ver entre sí. No uses datos personales ni frases conocidas.
2. **Guárdala ahora en tu gestor de contraseñas** (el de Google, el de Apple, Bitwarden…).
   - **Si pierdes la frase, las versiones nuevas no se podrán instalar encima de la que tengas en el móvil.** Tendrías que desinstalar la app y empezar de cero.
   - No la escribas en ningún chat, correo ni archivo del repositorio.
3. En GitHub, abre el repositorio y entra en **Settings** (Ajustes). Es la última pestaña de arriba, la del engranaje.
4. En el menú de la izquierda, abre **Secrets and variables** (Secretos y variables) y elige **Actions**.
5. Pulsa el botón verde **New repository secret** (Nuevo secreto del repositorio).
6. En **Name** (Nombre), escribe exactamente `SIGNING_PASSPHRASE`.
7. En **Secret** (Secreto), pega tu frase.
8. Pulsa **Add secret** (Añadir secreto). GitHub no te la volverá a enseñar nunca. Es normal.

## 2. Crea la llave de firma (una sola vez)

1. Entra en la pestaña **Actions** del repositorio.
2. En la lista de la izquierda, elige **Crear llave de firma**.
3. A la derecha, pulsa **Run workflow** (Ejecutar flujo).
4. Comprueba que en **Use workflow from** (Usar el flujo de) pone **Branch: main**. Pulsa el botón verde **Run workflow**.
5. Espera a que la ejecución termine con una marca verde (alrededor de un minuto).
   - Este flujo guarda la llave **cifrada** en el repositorio.
   - Después lanza el flujo **Publicar APK**, que tarda unos 10 minutos en dejar la primera versión en **Releases**.

Si termina con una cruz roja, ábrela y lee el mensaje:
- «Falta el secreto SIGNING_PASSPHRASE» o «debe tener 20 caracteres o más»: repite el paso 1. Si el secreto ya existe, cámbialo con el lápiz que hay a su lado.
- «La llave de firma ya existe»: ya la creaste antes. No hace falta repetir este paso.

## 3. Descarga la app en el móvil

1. En el móvil, abre esta dirección con el navegador: <https://github.com/obitouchihatobir-ai/Recipe_lower/releases/latest>
2. En **Assets** (Archivos), toca **Recetario-0.1.0.apk**. El número cambia con cada versión.
3. Si el navegador avisa de que el archivo puede ser dañino, elige descargarlo de todos modos. Es tu propia app.

## 4. Permite instalar apps desconocidas e instálala

1. Abre el archivo descargado, desde el aviso de descarga o desde la app **Archivos**, en la carpeta **Descargas**.
2. La primera vez, Android te avisa de que no puede instalar apps desconocidas de esa fuente. Toca **Ajustes** y activa **Permitir de esta fuente**. En algunos móviles se llama «Instalar apps desconocidas».
3. Vuelve atrás y toca **Instalar**.
4. Si Google Play Protect avisa de que no conoce la app, toca **Más detalles** y después **Instalar de todas formas**. Avisa porque Recetario no viene de Google Play.

Cuando termines, puedes volver a desactivar el permiso del punto 2. Solo lo necesitarás otra vez al actualizar.

## 5. Actualizaciones

1. Cada vez que el código nuevo llega a `main`, el flujo **Publicar APK** deja el APK nuevo en **Releases**.
2. Para actualizar, repite los pasos 3 y 4. La versión nueva se instala **encima** de la anterior y conservas tus recetas.
3. Esto funciona porque todas las versiones van firmadas con la misma llave. Por eso:
   - no pierdas la frase del paso 1;
   - no borres el archivo `signing/release.p12.enc` del repositorio.
4. Si un día Android dice que la app no se ha instalado porque «entra en conflicto con un paquete existente», no desinstales nada todavía. Antes, haz una copia de seguridad desde la app.
