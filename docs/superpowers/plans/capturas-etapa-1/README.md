# Capturas del cierre de la etapa 1

Capturas de `RecorridoEtapa1Test` (`app/src/test/java/app/recetario/RecorridoEtapa1Test.kt`), sacadas del artefacto
`capturas` de la ejecución de CI 43755375475 (commit 1b72996). La prueba recorre la app de verdad (`MainActivity`) con
Robolectric, gráficos nativos, una pantalla de 360 × 780 dp (xhdpi, 720 × 1560 px) y un reloj fijo (13 de noviembre
de 2026, 21:40 en Madrid; en el paso 7 pasa a las 13:10 del día siguiente). Hay una captura por paso y por tema; el
sufijo es el tema elegido al abrir la app (Cálido, Fresco o Nocturno).

En las capturas la foto de la receta sale como el emoji de su categoría: en Robolectric la imagen no se dibuja (la
prueba sí comprueba que la foto se guarda en el almacén y que viaja en la copia). Las sombras grises que se ven en
algún botón («Exportar copia», el interruptor de Automático) son la onda del toque congelada por el reloj de la
prueba; en el teléfono desaparece.

| Archivo | Qué se ve |
| --- | --- |
| `paso1-editor-pasos-{calido,fresco,nocturno}.png` | 1. Editor de «Bizcocho de limón» antes de guardar: el último ingrediente (200 g de azúcar) y los dos pasos; el segundo, «Hornea 20 min a 180 °C.», con el temporizador de 20 min aceptado. |
| `paso2-busqueda-{calido,fresco,nocturno}.png` | 2. Recetas buscando «limon» (sin tilde): solo queda «Bizcocho de limón» (la «Tortilla de patatas» desaparece). |
| `paso3-seis-porciones-{calido,fresco,nocturno}.png` | 3. Detalle con 6 porciones: harina 2¼ tazas (era 1½), huevos 3 y azúcar 300 g; debajo, «Lo que necesito» y los pasos. |
| `paso4-compra-{calido,fresco,nocturno}.png` | 4. Compra después de «Lo que necesito»: Huevos 3 en Lácteos; Harina 2¼ tazas y Azúcar 300 g en Despensa. |
| `paso5-cocina-listo-{calido,fresco,nocturno}.png` | 5. Modo cocina terminado: los dos pasos hechos y «¡Listo!» con «Volver a la receta». |
| `paso6-combinar-{calido,fresco,nocturno}.png` | 6. Copias de seguridad después de exportar e importar la misma copia con «Combinar»: «Copia importada: 0 recetas, 0 fotos.» y la fecha de la última copia guardada fuera del teléfono. |
| `paso7-automatico-noche-calido.png` | 7. Apariencia con Automático a las 21:40: «Ahora: Nocturno» y toda la app en Nocturno; el tema de día sigue marcado (Cálido). |
| `paso7-automatico-dia-fresco.png` | 7. Automático a las 13:10 del día siguiente, con Fresco como tema de día: «Ahora: Fresco». |
| `paso7-automatico-dia-nocturno.png` | 7. La app se abrió en Nocturno; con Automático, a las 13:10 vuelve al tema claro de día (Cálido): «Ahora: Cálido». |

Para volver a sacarlas: `./gradlew testDebugUnitTest --tests '*RecorridoEtapa1Test*'` deja todas las del recorrido
en `app/build/capturas/recorrido/` (en CI van en el artefacto `capturas`).
