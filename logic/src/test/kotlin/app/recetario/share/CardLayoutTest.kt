package app.recetario.share

import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import com.google.common.truth.Truth.assertThat
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
        val medium = layoutImage(cardContent(mediumRecipe(), 4, "🍲", allOn), CardFormat.SQUARE, CardDesign.CLASSIC, FakeMeasurer)
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

    @Test fun png_neverCutsMidLine() {
        val contents = listOf(
            cardContent(recipe(), 4, "🍲", allOn),
            cardContent(mediumRecipe(), 4, "🍲", allOn),
            cardContent(longRecipe(), 4, "🍲", allOn),
            cardContent(longRecipe().copy(ingredients = longRecipe().ingredients.take(8)), 4, "🍲", allOn),
        )
        for (c in contents) for (format in CardFormat.entries.filter { !it.isPdf }) for (design in CardDesign.entries) {
            val l = layoutImage(c, format, design, FakeMeasurer)
            val footer = l.blocks.single { it.kind == CardBlockKind.FOOTER }
            var lastBottom = 0f
            for (b in l.blocks) {
                if (b.kind == CardBlockKind.PHOTO || b.kind == CardBlockKind.EMOJI) continue
                val style = b.style!!
                // Alto exacto de sus líneas: nunca media línea.
                assertThat(b.lines).isEqualTo(FakeMeasurer.lines(b.text, style, b.width))
                assertThat(b.height).isEqualTo(b.lines * style.lineHeightPx)
                if (b.kind != CardBlockKind.FOOTER) {
                    assertThat(b.y + b.height).isAtMost(footer.y)
                    if (!b.overPhoto) {
                        assertThat(b.y).isAtLeast(lastBottom)
                        lastBottom = b.y + b.height
                    }
                }
            }
            assertThat(footer.y + footer.height).isAtMost(format.height)
        }
    }

    private fun flowTexts(pages: List<PdfPage>): List<String> {
        val out = mutableListOf<String>()
        for (p in pages) for (b in p.blocks) {
            if (b.kind !in setOf(CardBlockKind.INGREDIENT, CardBlockKind.STEP, CardBlockKind.NOTES)) continue
            if (b.continuation) out[out.lastIndex] = out.last() + " " + b.text else out += b.text
        }
        return out
    }

    @Test fun pdf_overflow_paginatesAllText() {
        val r = longRecipe()
        val c = cardContent(r, 4, "🍲", allOn)
        val expected = c.ingredients.map { "• $it" } +
            c.steps.mapIndexed { i, s -> "${i + 1}. $s" } +
            c.notes!!.split('\n').filter { it.isNotBlank() }
        for (format in CardFormat.entries.filter { it.isPdf }) for (design in CardDesign.entries) {
            val pages = paginatePdf(c, format, design, FakeMeasurer)
            assertThat(pages.size).isGreaterThan(1)
            assertThat(flowTexts(pages)).containsExactlyElementsIn(expected).inOrder()
            assertThat(pages.flatMap { it.blocks }.none { it.kind == CardBlockKind.MORE }).isTrue()
            assertThat(pages.flatMap { it.blocks }.count { it.kind == CardBlockKind.TITLE }).isEqualTo(1)
            pages.forEachIndexed { i, p ->
                assertThat(p.number).isEqualTo(i + 1)
                assertThat(p.total).isEqualTo(pages.size)
                val footer = p.blocks.single { it.kind == CardBlockKind.FOOTER }
                for (b in p.blocks) {
                    if (b.kind == CardBlockKind.FOOTER || b.kind == CardBlockKind.PHOTO || b.kind == CardBlockKind.EMOJI) continue
                    assertThat(b.lines).isEqualTo(FakeMeasurer.lines(b.text, b.style!!, b.width))
                    assertThat(b.height).isEqualTo(b.lines * b.style!!.lineHeightPx)
                    assertThat(b.y + b.height).isAtMost(footer.y)
                }
                // Un título de sección nunca queda solo al final de la página.
                assertThat(p.blocks.filter { it.kind != CardBlockKind.FOOTER }.last().kind).isNotEqualTo(CardBlockKind.HEADING)
            }
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
