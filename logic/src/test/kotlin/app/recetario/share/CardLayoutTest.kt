package app.recetario.share

import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.time.Instant
import kotlin.math.ceil

/** Medidor falso: cada carácter ocupa media letra de ancho; los saltos de línea cuentan. */
private object FakeMeasurer : TextMeasurer {
    override fun lines(text: String, style: CardTextStyle, widthPx: Float): Int {
        val perLine = maxOf(1, (widthPx / (style.sizePx * 0.5f)).toInt())
        return text.split('\n').sumOf { maxOf(1, ceil(it.length / perLine.toDouble()).toInt()) }
    }
}

class CardLayoutTest {
    private fun ing(n: Int, name: String, q: Double?, unit: MeasureUnit) = Ingredient("i$n", null, name, q, unit, null, "")

    private fun recipe(
        ingredients: List<Ingredient> = listOf(
            ing(1, "Calabaza", 800.0, MeasureUnit.GRAM),
            ing(2, "Sal y pimienta", null, MeasureUnit.TO_TASTE),
        ),
        steps: List<String> = listOf("Pela la calabaza.", "Cuécela 20 minutos."),
        notes: String = "",
        photo: String? = "foto.jpg",
        flavor: FlavorProfile = FlavorProfile(sweet = 3, sour = 1),
    ) = Recipe(
        id = "r", title = "Crema de calabaza", categoryId = "cat-comidas", coverPhoto = photo, description = "",
        totalMinutes = 35, servings = 4, difficulty = Difficulty.EASY, flavor = flavor,
        serveTemp = null, moments = emptySet(), alcoholic = null, course = null,
        ingredients = ingredients,
        steps = steps.mapIndexed { i, s -> Step("s$i", s, emptyList(), null, emptySet()) },
        tags = emptyList(), notes = notes, favorite = false, sourceUrl = null, aiGenerated = false,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private val allOn = CardOptions()

    private fun longRecipe() = recipe(
        ingredients = (1..40).map { ing(it, "Ingrediente número $it bien descrito", it * 10.0, MeasureUnit.GRAM) },
        steps = (1..25).map { "Paso $it: remueve con calma la mezcla durante unos minutos hasta que espese y brille." },
        notes = (1..30).joinToString(" ") { "Nota larga $it sobre cómo servirla." } + "\n" +
            (1..20).joinToString(" ") { "Segundo párrafo $it." },
    )

    private fun mediumRecipe() = recipe(
        ingredients = (1..6).map { ing(it, "Ingrediente $it", 100.0, MeasureUnit.GRAM) },
        steps = (1..4).map { "Paso $it: mezcla todo con cuidado y deja reposar un rato." },
    )

    @Test fun content_scaledIngredients_andOptions() {
        val c = cardContent(recipe(notes = "Mejor al día siguiente."), 6, "🍲", allOn)
        assertThat(c.title).isEqualTo("Crema de calabaza")
        assertThat(c.servingsLine).isEqualTo("35 min · 6 porciones · Fácil")
        assertThat(c.ingredients).containsExactly("Calabaza — 1,2 kg", "Sal y pimienta — al gusto").inOrder()
        assertThat(c.steps).containsExactly("Pela la calabaza.", "Cuécela 20 minutos.").inOrder()
        assertThat(c.notes).isEqualTo("Mejor al día siguiente.")
        assertThat(c.flavorLine).isEqualTo("Sabor: muy dulce y un poco ácido")
        assertThat(c.emoji).isEqualTo("🍲")
        assertThat(c.hasPhoto).isTrue()
        assertThat(c.footer).isEqualTo("Compartido desde Sazón")

        val off = cardContent(
            recipe(notes = "Mejor al día siguiente."), 4, "🍲",
            CardOptions(photo = false, steps = false, notes = false, flavor = false),
        )
        assertThat(off.ingredients).containsExactly("Calabaza — 800 g", "Sal y pimienta — al gusto").inOrder()
        assertThat(off.steps).isEmpty()
        assertThat(off.notes).isNull()
        assertThat(off.flavorLine).isNull()
        assertThat(off.hasPhoto).isFalse()

        // Sin foto en la receta, el interruptor no cuenta; notas en blanco y sabor sin apuntar no salen.
        val bare = cardContent(recipe(photo = null, flavor = FlavorProfile()), 1, "🍲", allOn)
        assertThat(bare.hasPhoto).isFalse()
        assertThat(bare.notes).isNull()
        assertThat(bare.flavorLine).isNull()
        assertThat(bare.servingsLine).isEqualTo("35 min · 1 porción · Fácil")
    }

    @Test fun flavorLine_wording() {
        assertThat(flavorLine(FlavorProfile(salty = 2))).isEqualTo("Sabor: salado")
        assertThat(flavorLine(FlavorProfile(sweet = 1, sour = 2, spicy = 3)))
            .isEqualTo("Sabor: un poco dulce, ácido y muy picante")
        assertThat(flavorLine(FlavorProfile())).isNull()
    }

    @Test fun png_fits_noTruncation() {
        val c = cardContent(recipe(notes = "Mejor al día siguiente."), 4, "🍲", allOn)
        for (design in CardDesign.entries) {
            val l = layoutImage(c, CardFormat.SQUARE, design, FakeMeasurer)
            assertThat(l.scale).isEqualTo(1f)
            assertThat(l.hiddenSteps).isEqualTo(0)
            assertThat(l.hiddenIngredients).isEqualTo(0)
            assertThat(l.notesTruncated).isFalse()
            assertThat(l.blocks.none { it.kind == CardBlockKind.MORE }).isTrue()
            assertThat(l.blocks.filter { it.kind == CardBlockKind.STEP }.map { it.text })
                .containsExactly("1. Pela la calabaza.", "2. Cuécela 20 minutos.").inOrder()
            assertThat(l.blocks.single { it.kind == CardBlockKind.NOTES }.text).isEqualTo("Mejor al día siguiente.")
            assertThat(l.blocks.single { it.kind == CardBlockKind.FOOTER }.text).isEqualTo("Compartido desde Sazón")
            assertThat(l.blocks.single { it.kind == CardBlockKind.TITLE }.text).isEqualTo("Crema de calabaza")
            val photos = l.blocks.filter { it.kind == CardBlockKind.PHOTO }
            if (design == CardDesign.MINIMAL) assertThat(photos).isEmpty() else assertThat(photos).hasSize(1)
            l.blocks.forEach { assertThat(it.y + it.height).isAtMost(CardFormat.SQUARE.height) }
        }
    }

    @Test fun bigPhoto_withoutPhoto_usesEmoji() {
        val c = cardContent(recipe(photo = null), 4, "🍲", allOn)
        val l = layoutImage(c, CardFormat.SQUARE, CardDesign.BIG_PHOTO, FakeMeasurer)
        assertThat(l.blocks.none { it.kind == CardBlockKind.PHOTO }).isTrue()
        assertThat(l.blocks.single { it.kind == CardBlockKind.EMOJI }.text).isEqualTo("🍲")
        val classic = layoutImage(c, CardFormat.SQUARE, CardDesign.CLASSIC, FakeMeasurer)
        assertThat(classic.blocks.none { it.kind == CardBlockKind.PHOTO || it.kind == CardBlockKind.EMOJI }).isTrue()
    }

    @Test fun png_overflow_shrinksThenTruncatesWithMore() {
        // Un poco largo: basta con bajar la letra.
        val medium = layoutImage(cardContent(mediumRecipe(), 4, "🍲", allOn), CardFormat.SQUARE, CardDesign.THEME, FakeMeasurer)
        assertThat(medium.scale).isLessThan(1f)
        assertThat(medium.scale).isAtLeast(MIN_CARD_SCALE)
        assertThat(medium.hiddenSteps).isEqualTo(0)
        assertThat(medium.blocks.none { it.kind == CardBlockKind.MORE }).isTrue()

        // Muy larga: letra mínima, «…y N pasos más» y notas con «…».
        val content = cardContent(
            longRecipe().copy(ingredients = longRecipe().ingredients.take(12)), 4, "🍲", allOn,
        )
        val l = layoutImage(content, CardFormat.STORY, CardDesign.CLASSIC, FakeMeasurer)
        assertThat(l.scale).isEqualTo(MIN_CARD_SCALE)
        val shown = l.blocks.count { it.kind == CardBlockKind.STEP }
        assertThat(shown).isGreaterThan(0)
        assertThat(l.hiddenSteps).isEqualTo(25 - shown)
        assertThat(l.blocks.single { it.kind == CardBlockKind.MORE }.text).isEqualTo("…y ${25 - shown} pasos más")
        assertThat(l.blocks.count { it.kind == CardBlockKind.INGREDIENT }).isEqualTo(12)
        assertThat(l.notesTruncated).isTrue()
        l.blocks.singleOrNull { it.kind == CardBlockKind.NOTES }?.let { assertThat(it.text).endsWith("…") }

        // 40 ingredientes no caben ni en la letra mínima: se recortan con «…y N ingredientes más».
        val huge = layoutImage(cardContent(longRecipe(), 4, "🍲", allOn), CardFormat.SQUARE, CardDesign.CLASSIC, FakeMeasurer)
        val shownIngredients = huge.blocks.count { it.kind == CardBlockKind.INGREDIENT }
        assertThat(huge.hiddenIngredients).isEqualTo(40 - shownIngredients)
        assertThat(huge.blocks.map { it.text }).contains("…y ${40 - shownIngredients} ingredientes más")
        assertThat(huge.blocks.none { it.kind == CardBlockKind.PHOTO }).isTrue()
    }

    @Test fun morePhrase_singular() {
        assertThat(moreStepsText(1)).isEqualTo("…y 1 paso más")
        assertThat(moreIngredientsText(1)).isEqualTo("…y 1 ingrediente más")
    }

    /** Dentro del lienzo, sin media línea, nada pasa del pie y ningún bloque pisa a otro. */
    private fun assertSane(blocks: List<CardBlock>, format: CardFormat) {
        val eps = 0.01f
        val footer = blocks.single { it.kind == CardBlockKind.FOOTER }
        for (b in blocks) {
            assertThat(b.x).isAtLeast(-eps)
            assertThat(b.y).isAtLeast(-eps)
            assertThat(b.x + b.width).isAtMost(format.width + eps)
            assertThat(b.y + b.height).isAtMost(format.height + eps)
            if (b.kind == CardBlockKind.PHOTO || b.kind == CardBlockKind.EMOJI) continue
            assertThat(b.text).isNotEmpty()
            val style = b.style!!
            assertThat(b.lines).isEqualTo(FakeMeasurer.lines(b.text, style, b.width))
            assertThat(b.height).isEqualTo(b.lines * style.lineHeightPx)
            if (b.kind != CardBlockKind.FOOTER) assertThat(b.y + b.height).isAtMost(footer.y)
        }
        for ((i, a) in blocks.withIndex()) for (b in blocks.drop(i + 1)) {
            val photo = setOf(CardBlockKind.PHOTO, CardBlockKind.EMOJI)
            if ((a.overPhoto && b.kind in photo) || (b.overPhoto && a.kind in photo)) continue
            val overlap = a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height
            assertWithMessage("${a.kind} «${a.text.take(20)}» pisa ${b.kind} «${b.text.take(20)}»").that(overlap).isFalse()
        }
    }

    @Test fun png_neverCutsMidLine() {
        val contents = listOf(
            cardContent(recipe(), 4, "🍲", allOn),
            cardContent(mediumRecipe(), 4, "🍲", allOn),
            cardContent(longRecipe(), 4, "🍲", allOn),
            cardContent(longRecipe().copy(ingredients = longRecipe().ingredients.take(8)), 4, "🍲", allOn),
            cardContent(recipe(ingredients = emptyList()), 4, "🍲", allOn),
        )
        for (c in contents) for (format in CardFormat.entries.filter { !it.isPdf }) for (design in CardDesign.entries) {
            assertSane(layoutImage(c, format, design, FakeMeasurer).blocks, format)
        }
    }

    private val wide = listOf(CardFormat.SQUARE, CardFormat.LANDSCAPE, CardFormat.PDF_A4, CardFormat.PDF_LETTER)

    private fun firstPageOrImage(c: CardContent, format: CardFormat, design: CardDesign): List<CardBlock> =
        if (format.isPdf) paginatePdf(c, format, design, FakeMeasurer).first().blocks
        else layoutImage(c, format, design, FakeMeasurer).blocks

    @Test fun classic_twoColumns_inWideFormats() {
        for (c in listOf(cardContent(recipe(notes = "Mejor al día siguiente."), 4, "🍲", allOn), cardContent(mediumRecipe(), 4, "🍲", allOn))) {
            for (format in CardFormat.entries) for (design in CardDesign.entries) {
                val blocks = firstPageOrImage(c, format, design)
                val left = blocks.filter { it.kind == CardBlockKind.INGREDIENT }
                val right = blocks.filter { it.kind == CardBlockKind.STEP || it.kind == CardBlockKind.NOTES }
                assertThat(left).isNotEmpty()
                assertThat(right).isNotEmpty()
                val title = blocks.single { it.kind == CardBlockKind.TITLE }
                if (design == CardDesign.CLASSIC && format in wide) {
                    assertThat(left.map { it.x }.toSet()).hasSize(1)
                    assertThat(right.map { it.x }.toSet()).hasSize(1)
                    val gutter = right.first().x - (left.first().x + left.first().width)
                    assertThat(gutter).isGreaterThan(0f)
                    // Las dos columnas empiezan a la misma altura, debajo de la cabecera de ancho completo.
                    val leftHead = blocks.first { it.kind == CardBlockKind.HEADING && it.text == "Ingredientes" }
                    val rightHead = blocks.first { it.kind == CardBlockKind.HEADING && it.text == "Pasos" }
                    assertThat(rightHead.y).isEqualTo(leftHead.y)
                    assertThat(rightHead.x).isEqualTo(right.first().x)
                    assertThat(title.width).isGreaterThan(left.first().width * 1.9f)
                } else {
                    assertThat((left + right).map { it.x }.toSet()).hasSize(1)
                }
                assertSane(blocks, format)
            }
        }
    }

    @Test fun classic_twoColumns_truncatesPerColumn() {
        // Muchos pasos y pocos ingredientes: solo se recorta la columna derecha.
        val steps = layoutImage(
            cardContent(longRecipe().copy(ingredients = longRecipe().ingredients.take(5)), 4, "🍲", allOn),
            CardFormat.SQUARE, CardDesign.CLASSIC, FakeMeasurer,
        )
        assertThat(steps.hiddenIngredients).isEqualTo(0)
        assertThat(steps.blocks.count { it.kind == CardBlockKind.INGREDIENT }).isEqualTo(5)
        assertThat(steps.hiddenSteps).isGreaterThan(0)
        assertThat(steps.blocks.single { it.kind == CardBlockKind.MORE }.text).isEqualTo("…y ${steps.hiddenSteps} pasos más")
        assertThat(steps.photoDropped).isFalse()

        // Muchos ingredientes y pocos pasos: solo se recorta la izquierda (y se quita la foto); los pasos siguen.
        val ingr = layoutImage(
            cardContent(longRecipe().copy(steps = longRecipe().steps.take(3), notes = ""), 4, "🍲", allOn),
            CardFormat.SQUARE, CardDesign.CLASSIC, FakeMeasurer,
        )
        assertThat(ingr.hiddenIngredients).isGreaterThan(0)
        assertThat(ingr.photoDropped).isTrue()
        assertThat(ingr.hiddenSteps).isEqualTo(0)
        assertThat(ingr.blocks.count { it.kind == CardBlockKind.STEP }).isEqualTo(3)
        assertThat(ingr.blocks.single { it.kind == CardBlockKind.MORE }.text)
            .isEqualTo("…y ${ingr.hiddenIngredients} ingredientes más")
    }

    @Test fun photoDropped_onlyWhenNeeded() {
        val short = layoutImage(cardContent(recipe(), 4, "🍲", allOn), CardFormat.SQUARE, CardDesign.CLASSIC, FakeMeasurer)
        assertThat(short.photoDropped).isFalse()
        val huge = layoutImage(cardContent(longRecipe(), 4, "🍲", allOn), CardFormat.STORY, CardDesign.CLASSIC, FakeMeasurer)
        assertThat(huge.photoDropped).isTrue()
        assertThat(huge.scale).isEqualTo(MIN_CARD_SCALE)
    }

    @Test fun longTitle_neverPastFooter() {
        val wordy = (1..60).joinToString(" ") { "palabra$it" }.take(425)
        val oneWord = "x".repeat(425)
        for (title in listOf(wordy, oneWord)) {
            val c = cardContent(recipe().copy(title = title), 4, "🍲", allOn)
            for (format in CardFormat.entries) for (design in CardDesign.entries) {
                val all = if (format.isPdf) paginatePdf(c, format, design, FakeMeasurer).map { it.blocks }
                else listOf(layoutImage(c, format, design, FakeMeasurer).blocks)
                all.forEach { assertSane(it, format) }
                val t = all.flatten().single { it.kind == CardBlockKind.TITLE }
                assertThat(t.lines).isAtMost(MAX_TITLE_LINES)
                assertThat(t.text).endsWith("…")
            }
        }
    }

    @Test fun noIngredients_noHeading() {
        val c = cardContent(recipe(ingredients = emptyList()), 4, "🍲", allOn)
        for (format in CardFormat.entries) for (design in CardDesign.entries) {
            val blocks = if (format.isPdf) paginatePdf(c, format, design, FakeMeasurer).flatMap { it.blocks }
            else layoutImage(c, format, design, FakeMeasurer).blocks
            assertThat(blocks.none { it.text == "Ingredientes" }).isTrue()
            assertThat(blocks.count { it.kind == CardBlockKind.STEP }).isEqualTo(2)
        }
    }

    @Test fun classic_noIngredients_stepsUseFullWidth() {
        val c = cardContent(recipe(ingredients = emptyList(), notes = "Mejor al día siguiente."), 4, "🍲", allOn)
        for (format in CardFormat.entries) {
            val blocks = firstPageOrImage(c, format, CardDesign.CLASSIC)
            val title = blocks.single { it.kind == CardBlockKind.TITLE }
            val body = blocks.filter { it.kind == CardBlockKind.STEP || it.kind == CardBlockKind.NOTES || it.kind == CardBlockKind.HEADING }
            assertWithMessage("$format").that(body).isNotEmpty()
            for (b in body) {
                assertWithMessage("$format ${b.text}").that(b.x).isEqualTo(title.x)
                assertWithMessage("$format ${b.text}").that(b.width).isEqualTo(title.width)
            }
            assertSane(blocks, format)
        }
    }

    /** Textos de ingredientes, pasos y notas, cada uno con sus continuaciones unidas por su separador. */
    private fun flowTexts(pages: List<PdfPage>): Map<CardBlockKind, List<String>> {
        val out = mutableMapOf<CardBlockKind, MutableList<String>>()
        for (p in pages) for (b in p.blocks) {
            if (b.kind !in setOf(CardBlockKind.INGREDIENT, CardBlockKind.STEP, CardBlockKind.NOTES)) continue
            val list = out.getOrPut(b.kind) { mutableListOf() }
            if (b.continuation) list[list.lastIndex] = list.last() + b.separator + b.text else list += b.text
        }
        return out
    }

    private fun assertPagesSane(pages: List<PdfPage>, format: CardFormat) {
        pages.forEachIndexed { i, p ->
            assertThat(p.number).isEqualTo(i + 1)
            assertThat(p.total).isEqualTo(pages.size)
            assertSane(p.blocks, format)
            // Un título de sección nunca queda solo al final de una columna.
            p.blocks.filter { it.kind != CardBlockKind.FOOTER && it.kind != CardBlockKind.PHOTO && it.kind != CardBlockKind.EMOJI }
                .groupBy { it.x }.values.forEach { col ->
                    assertThat(col.maxBy { it.y }.kind).isNotEqualTo(CardBlockKind.HEADING)
                }
        }
    }

    @Test fun pdf_overflow_paginatesAllText() {
        val c = cardContent(longRecipe(), 4, "🍲", allOn)
        val expected = mapOf(
            CardBlockKind.INGREDIENT to c.ingredients.map { "• $it" },
            CardBlockKind.STEP to c.steps.mapIndexed { i, s -> "${i + 1}. $s" },
            CardBlockKind.NOTES to c.notes!!.split('\n').filter { it.isNotBlank() },
        )
        for (format in CardFormat.entries.filter { it.isPdf }) for (design in CardDesign.entries) {
            val pages = paginatePdf(c, format, design, FakeMeasurer)
            assertThat(pages.size).isGreaterThan(1)
            assertThat(flowTexts(pages)).isEqualTo(expected)
            assertThat(pages.flatMap { it.blocks }.none { it.kind == CardBlockKind.MORE }).isTrue()
            assertThat(pages.flatMap { it.blocks }.count { it.kind == CardBlockKind.TITLE }).isEqualTo(1)
            assertPagesSane(pages, format)
        }
    }

    @Test fun pdf_giantWord_splitByCharacters() {
        val notes = "Antes " + "x".repeat(3000) + " después"
        val c = cardContent(recipe(notes = notes), 4, "🍲", allOn)
        for (format in CardFormat.entries.filter { it.isPdf }) for (design in CardDesign.entries) {
            val pages = paginatePdf(c, format, design, FakeMeasurer)
            // En la tarjeta de 10 × 15 la palabra no cabe en una página entera: se parte por caracteres.
            if (format == CardFormat.PDF_CARD) assertThat(pages.size).isGreaterThan(2)
            assertThat(flowTexts(pages)[CardBlockKind.NOTES]).containsExactly(notes)
            assertPagesSane(pages, format)
        }
    }

    @Test fun pdf_short_onePage() {
        val pages = paginatePdf(cardContent(recipe(), 4, "🍲", allOn), CardFormat.PDF_A4, CardDesign.CLASSIC, FakeMeasurer)
        assertThat(pages).hasSize(1)
        assertThat(pages[0].blocks.single { it.kind == CardBlockKind.FOOTER }.text).isEqualTo("Compartido desde Sazón")
    }

    @Test fun pdf_footerOnEveryPage() {
        val pages = paginatePdf(cardContent(longRecipe(), 4, "🍲", allOn), CardFormat.PDF_CARD, CardDesign.MINIMAL, FakeMeasurer)
        assertThat(pages.size).isGreaterThan(2)
        pages.forEachIndexed { i, p ->
            val footer = p.blocks.single { it.kind == CardBlockKind.FOOTER }
            assertThat(footer.text).isEqualTo("Compartido desde Sazón · ${i + 1}/${pages.size}")
            assertThat(footer.y + footer.height).isAtMost(CardFormat.PDF_CARD.height)
            assertThat(footer.y + footer.height).isGreaterThan(CardFormat.PDF_CARD.height * 0.85f)
        }
    }

    @Test fun formats_sizes() {
        assertThat(listOf(CardFormat.SQUARE.width, CardFormat.SQUARE.height)).containsExactly(1080f, 1080f)
        assertThat(listOf(CardFormat.STORY.width, CardFormat.STORY.height)).containsExactly(1080f, 1920f)
        assertThat(listOf(CardFormat.LANDSCAPE.width, CardFormat.LANDSCAPE.height)).containsExactly(1500f, 1000f)
        assertThat(listOf(CardFormat.PDF_A4.width, CardFormat.PDF_A4.height)).containsExactly(595f, 842f)
        assertThat(listOf(CardFormat.PDF_LETTER.width, CardFormat.PDF_LETTER.height)).containsExactly(612f, 792f)
        assertThat(listOf(CardFormat.PDF_CARD.width, CardFormat.PDF_CARD.height)).containsExactly(283f, 425f)
        assertThat(CardFormat.entries.filter { it.isPdf })
            .containsExactly(CardFormat.PDF_A4, CardFormat.PDF_LETTER, CardFormat.PDF_CARD)
    }

    @Test fun fileName_noAccents_noSlashes_max60() {
        assertThat(cardFileName("Crema de calabaza", CardFormat.SQUARE)).isEqualTo("Sazon-crema-de-calabaza.png")
        assertThat(cardFileName("Piña colada / Año nuevo: ¡ñam!", CardFormat.PDF_A4))
            .isEqualTo("Sazon-pina-colada-ano-nuevo-nam.pdf")
        assertThat(cardFileName("¿?*", CardFormat.STORY)).isEqualTo("Sazon-receta.png")
        val long = cardFileName("Tarta ".repeat(30), CardFormat.PDF_CARD)
        assertThat(long.length).isAtMost(60)
        assertThat(long).startsWith("Sazon-tarta-tarta")
        assertThat(long).endsWith(".pdf")
        assertThat(long).doesNotContain("-.")
        assertThat(long).matches("[A-Za-z0-9.-]+")
    }

    @Test fun options_roundTripString() {
        val o = CardOptions(CardFormat.PDF_LETTER, CardDesign.BIG_PHOTO, photo = false, steps = true, notes = false, flavor = true)
        assertThat(CardOptions.decode(o.encode())).isEqualTo(o)
        for (f in CardFormat.entries) for (d in CardDesign.entries) {
            val x = CardOptions(f, d, photo = true, steps = false, notes = true, flavor = false)
            assertThat(CardOptions.decode(x.encode())).isEqualTo(x)
        }
        assertThat(CardOptions.decode(null)).isEqualTo(CardOptions())
        assertThat(CardOptions.decode("basura")).isEqualTo(CardOptions())
        assertThat(CardOptions.decode("NOPE|CLASSIC|1|1|1|1")).isEqualTo(CardOptions())
    }
}
