package app.recetario.share

/*
 * Maquetación de la ficha (diseño de la etapa 2, §7.7): qué cabe, qué se recorta y cómo se parte en
 * páginas. Todo se apila en vertical; cada bloque lleva su texto, su estilo, su posición y su alto
 * medido (siempre un número entero de líneas). La app dibuja la imagen en Compose y el PDF con
 * `StaticLayout` leyendo los mismos bloques; solo aporta el medidor de líneas ([TextMeasurer]).
 */

/** Escala mínima legible de la letra en las imágenes. */
const val MIN_CARD_SCALE = 0.75f

/** Alto de línea respecto al tamaño de la letra. */
const val CARD_LINE_SPACING = 1.3f

private val IMAGE_SCALES = listOf(1f, 0.95f, 0.9f, 0.85f, 0.8f, MIN_CARD_SCALE)

/** Papel de cada texto; su tamaño es el del cuerpo por [factor]. La fuente la elige la app según el diseño. */
enum class CardTextRole(val factor: Float, val bold: Boolean) {
    TITLE(1.9f, true),
    META(0.95f, false),
    HEADING(1.2f, true),
    BODY(1f, false),
    FOOTER(0.8f, false),
}

data class CardTextStyle(val role: CardTextRole, val sizePx: Float) {
    val lineHeightPx: Float get() = sizePx * CARD_LINE_SPACING
}

/** Cuántas líneas ocupa [text] con [style] en [widthPx] (en la app, `StaticLayout` o Compose). */
interface TextMeasurer {
    fun lines(text: String, style: CardTextStyle, widthPx: Float): Int
}

enum class CardBlockKind {
    /** La foto de la receta (sin texto ni estilo). */
    PHOTO,

    /** «Foto grande» sin foto: el emoji de la categoría ([CardBlock.text]) sobre el color de acento. */
    EMOJI,
    TITLE, META, FLAVOR, HEADING, INGREDIENT, STEP, NOTES,

    /** «…y N pasos más» o «…y N ingredientes más». */
    MORE,
    FOOTER,
}

/**
 * Un bloque ya colocado. [height] es exactamente [lines] × alto de línea (las fotos, su caja).
 * [overPhoto]: el título de «Foto grande», encima de la foto. [continuation]: en el PDF, sigue el
 * texto del bloque anterior de la página previa (unido con un espacio).
 */
data class CardBlock(
    val kind: CardBlockKind,
    val text: String,
    val style: CardTextStyle?,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val lines: Int = 0,
    val overPhoto: Boolean = false,
    val continuation: Boolean = false,
)

data class ImageLayout(
    val format: CardFormat,
    val design: CardDesign,
    val scale: Float,
    val blocks: List<CardBlock>,
    val hiddenIngredients: Int,
    val hiddenSteps: Int,
    val notesTruncated: Boolean,
)

data class PdfPage(
    val format: CardFormat,
    val design: CardDesign,
    val number: Int,
    val total: Int,
    val blocks: List<CardBlock>,
)

fun moreStepsText(n: Int): String = if (n == 1) "…y 1 paso más" else "…y $n pasos más"

fun moreIngredientsText(n: Int): String = if (n == 1) "…y 1 ingrediente más" else "…y $n ingredientes más"

private data class Item(
    val kind: CardBlockKind,
    val text: String,
    val role: CardTextRole,
    val newSection: Boolean = false,
    val continuation: Boolean = false,
)

private class Geo(val format: CardFormat, scale: Float) {
    val w = format.width
    val h = format.height
    val margin = w * (if (format.isPdf) 0.08f else 0.07f)
    val contentW = w - 2 * margin
    val body = format.baseBody * scale
    val itemGap = body * 0.25f
    val sectionGap = body * 0.8f

    /** Alto de la foto: la de «Foto grande» va a sangre y es más alta. */
    fun photoHeight(big: Boolean): Float = h * when {
        format.isPdf -> if (big) 0.35f else 0.22f
        format == CardFormat.STORY -> if (big) 0.42f else 0.3f
        else -> if (big) 0.34f else 0.26f
    }
    fun style(role: CardTextRole) = CardTextStyle(role, body * role.factor)
}

private class Stacker(val geo: Geo, val m: TextMeasurer, var y: Float, val bottom: Float) {
    val blocks = mutableListOf<CardBlock>()
    var empty = true

    private fun gap(item: Item, first: Boolean) = when {
        first -> 0f
        item.newSection -> geo.sectionGap
        else -> geo.itemGap
    }

    fun measure(item: Item, at: Float): CardBlock {
        val style = geo.style(item.role)
        val lines = m.lines(item.text, style, geo.contentW)
        return CardBlock(
            item.kind, item.text, style, geo.margin, at, geo.contentW, lines * style.lineHeightPx, lines,
            continuation = item.continuation,
        )
    }

    /** Si [items] caben debajo de lo apilado dejando libre [reserve] al final. */
    fun fits(items: List<Item>, reserve: Float = 0f): Boolean {
        var yy = y
        var first = empty
        for (item in items) {
            yy += gap(item, first)
            yy += measure(item, yy).height
            first = false
        }
        return yy <= bottom - reserve
    }

    fun place(item: Item) {
        var at = y
        at += gap(item, empty)
        val b = measure(item, at)
        blocks += b
        y = at + b.height
        empty = false
    }
}

private fun footerBlock(geo: Geo, m: TextMeasurer, text: String): CardBlock {
    val style = geo.style(CardTextRole.FOOTER)
    val lines = m.lines(text, style, geo.contentW)
    val height = lines * style.lineHeightPx
    return CardBlock(CardBlockKind.FOOTER, text, style, geo.margin, geo.h - geo.margin - height, geo.contentW, height, lines)
}

private fun photoBlock(geo: Geo, content: CardContent, design: CardDesign): CardBlock? = when (design) {
    CardDesign.MINIMAL -> null
    CardDesign.BIG_PHOTO -> {
        val kind = if (content.hasPhoto) CardBlockKind.PHOTO else CardBlockKind.EMOJI
        val text = if (content.hasPhoto) "" else content.emoji
        CardBlock(kind, text, null, 0f, 0f, geo.w, geo.photoHeight(big = true))
    }
    CardDesign.CLASSIC, CardDesign.THEME ->
        if (content.hasPhoto) {
            CardBlock(
                CardBlockKind.PHOTO, "", null, geo.margin, geo.margin, geo.contentW,
                geo.photoHeight(big = false),
            )
        } else {
            null
        }
}

/** Foto, título, porciones y sabor: lo que abre la ficha (o la primera página). */
private class Start(val stacker: Stacker, val top: List<CardBlock>)

private fun start(
    content: CardContent,
    design: CardDesign,
    geo: Geo,
    m: TextMeasurer,
    bottom: Float,
    withPhoto: Boolean,
): Start {
    val photo = if (withPhoto) photoBlock(geo, content, design) else null
    val s = Stacker(geo, m, if (photo == null) geo.margin else photo.y + photo.height + geo.sectionGap, bottom)
    val top = listOfNotNull(photo).toMutableList()
    val title = Item(CardBlockKind.TITLE, content.title, CardTextRole.TITLE)
    if (photo != null && design == CardDesign.BIG_PHOTO) {
        val b = s.measure(title, 0f)
        val y = photo.height - geo.margin * 0.5f - b.height
        if (y >= geo.margin) top += b.copy(y = y, overPhoto = true) else s.place(title)
    } else {
        s.place(title)
    }
    s.place(Item(CardBlockKind.META, content.servingsLine, CardTextRole.META))
    content.flavorLine?.let { s.place(Item(CardBlockKind.FLAVOR, it, CardTextRole.META)) }
    return Start(s, top)
}

private fun heading(text: String) = Item(CardBlockKind.HEADING, text, CardTextRole.HEADING, newSection = true)

private fun ingredientItems(c: CardContent) =
    listOf(heading("Ingredientes")) + c.ingredients.map { Item(CardBlockKind.INGREDIENT, "• $it", CardTextRole.BODY) }

private fun stepItems(c: CardContent) =
    if (c.steps.isEmpty()) {
        emptyList()
    } else {
        listOf(heading("Pasos")) + c.steps.mapIndexed { i, s -> Item(CardBlockKind.STEP, "${i + 1}. $s", CardTextRole.BODY) }
    }

private fun more(text: String) = Item(CardBlockKind.MORE, text, CardTextRole.BODY)

/**
 * La ficha en imagen. Si no cabe, baja la letra hasta [MIN_CARD_SCALE]; si aún no cabe, recorta los
 * pasos («…y N pasos más») y las notas («…»), y en último caso quita la foto y recorta los
 * ingredientes («…y N ingredientes más»). Nunca corta un bloque a mitad de línea.
 */
fun layoutImage(content: CardContent, format: CardFormat, design: CardDesign, measurer: TextMeasurer): ImageLayout {
    val notes = content.notes?.let { listOf(heading("Notas"), Item(CardBlockKind.NOTES, it, CardTextRole.BODY)) }.orEmpty()
    val rest = ingredientItems(content) + stepItems(content) + notes
    for (scale in IMAGE_SCALES) {
        val geo = Geo(format, scale)
        val footer = footerBlock(geo, measurer, content.footer)
        val st = start(content, design, geo, measurer, footer.y - geo.sectionGap, withPhoto = true)
        if (st.stacker.fits(rest)) {
            rest.forEach(st.stacker::place)
            return ImageLayout(format, design, scale, st.top + st.stacker.blocks + footer, 0, 0, false)
        }
    }
    return truncatedImage(content, format, design, measurer)
}

private fun truncatedImage(content: CardContent, format: CardFormat, design: CardDesign, m: TextMeasurer): ImageLayout {
    val geo = Geo(format, MIN_CARD_SCALE)
    val footer = footerBlock(geo, m, content.footer)
    val bottom = footer.y - geo.sectionGap
    val ingredients = ingredientItems(content)
    var st = start(content, design, geo, m, bottom, withPhoto = true)
    if (!st.stacker.fits(ingredients) && st.top.isNotEmpty()) st = start(content, design, geo, m, bottom, withPhoto = false)
    val s = st.stacker
    fun done(hiddenIngredients: Int, hiddenSteps: Int, notesTruncated: Boolean) =
        ImageLayout(format, design, MIN_CARD_SCALE, st.top + s.blocks + footer, hiddenIngredients, hiddenSteps, notesTruncated)

    if (!s.fits(ingredients)) {
        val n = content.ingredients.size
        val k = (n - 1 downTo 0).firstOrNull { k ->
            s.fits(ingredients.take(1 + k) + more(moreIngredientsText(n - k)))
        }
        if (k != null) (ingredients.take(1 + k) + more(moreIngredientsText(n - k))).forEach(s::place)
        return done(n - (k ?: 0), content.steps.size, content.notes != null)
    }
    ingredients.forEach(s::place)

    // Pasos: dejan sitio para el título de las notas y sus 3 primeras líneas.
    var hiddenSteps = 0
    val steps = stepItems(content)
    if (steps.isNotEmpty()) {
        val reserve = content.notes?.let { text ->
            val body = geo.style(CardTextRole.BODY)
            val noteLines = minOf(3, m.lines(text, body, geo.contentW))
            geo.sectionGap + s.measure(heading("Notas"), 0f).height + geo.itemGap + noteLines * body.lineHeightPx
        } ?: 0f
        val n = content.steps.size
        val k = (n downTo 0).firstOrNull { k ->
            s.fits(steps.take(1 + k) + listOfNotNull(if (k < n) more(moreStepsText(n - k)) else null), reserve)
        }
        if (k != null) {
            (steps.take(1 + k) + listOfNotNull(if (k < n) more(moreStepsText(n - k)) else null)).forEach(s::place)
        }
        hiddenSteps = n - (k ?: 0)
    }

    // Notas: enteras si caben; si no, las palabras que quepan y «…».
    var notesTruncated = false
    content.notes?.let { text ->
        val whole = listOf(heading("Notas"), Item(CardBlockKind.NOTES, text, CardTextRole.BODY))
        if (s.fits(whole)) {
            whole.forEach(s::place)
        } else {
            notesTruncated = true
            val words = text.split(' ')
            fun cut(k: Int) = listOf(heading("Notas"), Item(CardBlockKind.NOTES, words.take(k).joinToString(" ").trimEnd() + "…", CardTextRole.BODY))
            var lo = 0
            var hi = words.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (s.fits(cut(mid))) lo = mid else hi = mid - 1
            }
            if (lo >= 1) cut(lo).forEach(s::place)
        }
    }
    return done(0, hiddenSteps, notesTruncated)
}

/**
 * La ficha en PDF: todo el texto, partido en páginas sin perder nada. Un título de sección nunca
 * queda solo al final de una página; un bloque que no cabe ni en una página entera se parte por
 * palabras (el resto lleva [CardBlock.continuation]). Cada página lleva el pie con «n/total» si hay
 * más de una.
 */
fun paginatePdf(content: CardContent, format: CardFormat, design: CardDesign, measurer: TextMeasurer): List<PdfPage> {
    val geo = Geo(format, 1f)
    // El hueco del pie se mide con el texto más largo posible.
    val bottom = footerBlock(geo, measurer, "${content.footer} · 99/99").y - geo.sectionGap
    val notes = content.notes?.let { text ->
        val paragraphs = text.split('\n').filter { it.isNotBlank() }
        listOf(heading("Notas")) + paragraphs.map { Item(CardBlockKind.NOTES, it, CardTextRole.BODY) }
    }.orEmpty()
    val queue = ArrayDeque(ingredientItems(content) + stepItems(content) + notes)

    val pages = mutableListOf<List<CardBlock>>()
    val first = start(content, design, geo, measurer, bottom, withPhoto = true)
    var top = first.top
    var s = first.stacker
    fun newPage() {
        pages += top + s.blocks
        top = emptyList()
        s = Stacker(geo, measurer, geo.margin, bottom)
    }
    fun fitsFresh(item: Item) = Stacker(geo, measurer, geo.margin, bottom).fits(listOf(item))

    while (queue.isNotEmpty()) {
        val item = queue.removeFirst()
        if (item.kind == CardBlockKind.HEADING) {
            val next = queue.firstOrNull()
            val together = listOfNotNull(item, next)
            if (!s.fits(together) && !s.empty) newPage()
            s.place(item)
            continue
        }
        if (s.fits(listOf(item))) {
            s.place(item)
            continue
        }
        if (!s.empty && fitsFresh(item)) {
            newPage()
            s.place(item)
            continue
        }
        // Más alto que lo que queda: se parte por palabras.
        val words = item.text.split(' ')
        fun part(k: Int) = item.copy(text = words.take(k).joinToString(" "))
        var lo = 0
        var hi = words.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (s.fits(listOf(part(mid)))) lo = mid else hi = mid - 1
        }
        if (lo == 0 && s.empty) lo = 1 // Ni una palabra cabe en una página vacía: se pone igual.
        if (lo >= 1) {
            s.place(part(lo))
            queue.addFirst(item.copy(text = words.drop(lo).joinToString(" "), continuation = true, newSection = false))
        } else {
            queue.addFirst(item)
        }
        newPage()
    }
    pages += top + s.blocks
    val total = pages.size
    return pages.mapIndexed { i, blocks ->
        val text = if (total > 1) "${content.footer} · ${i + 1}/$total" else content.footer
        PdfPage(format, design, i + 1, total, blocks + footerBlock(geo, measurer, text))
    }
}
