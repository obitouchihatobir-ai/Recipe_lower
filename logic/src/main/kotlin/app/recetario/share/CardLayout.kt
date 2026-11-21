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
/** Líneas máximas del título; uno más largo se corta con «…» (también en el PDF). */
const val MAX_TITLE_LINES = 3

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
 * Un bloque ya colocado, en px o pt del formato. [height] es exactamente [lines] × alto de línea (las
 * fotos, su caja). En «Clásico» de los formatos anchos los ingredientes van en la columna izquierda y
 * los pasos y las notas en la derecha: el [x] y el [width] de cada bloque ya lo dicen.
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
    /** Con [continuation], lo que va entre el texto anterior y este: un espacio, o nada si se partió una palabra. */
    val separator: String = " ",
)

/**
 * La ficha en imagen. [scale] es la escala de la letra (1 a [MIN_CARD_SCALE]). [photoDropped]: la foto
 * se quitó para que cupieran los ingredientes; solo pasa a la escala mínima, como último recurso (la
 * vista previa puede explicarlo).
 */
data class ImageLayout(
    val format: CardFormat,
    val design: CardDesign,
    val scale: Float,
    val blocks: List<CardBlock>,
    val hiddenIngredients: Int,
    val hiddenSteps: Int,
    val notesTruncated: Boolean,
    val photoDropped: Boolean = false,
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
    val separator: String = " ",
)

private class Geo(val format: CardFormat, val design: CardDesign, scale: Float) {
    val w = format.width
    val h = format.height
    val margin = w * (if (format.isPdf) 0.08f else 0.07f)
    val contentW = w - 2 * margin
    val body = format.baseBody * scale
    val itemGap = body * 0.25f
    val sectionGap = body * 0.8f

    /** «Clásico» en dos columnas en los formatos anchos (la vertical y la tarjeta de 10 × 15 son estrechas). */
    val twoColumns = design == CardDesign.CLASSIC &&
        format in setOf(CardFormat.SQUARE, CardFormat.LANDSCAPE, CardFormat.PDF_A4, CardFormat.PDF_LETTER)
    val gutter = body * 1.5f
    val columnW = (contentW - gutter) / 2
    val rightX = margin + columnW + gutter

    fun style(role: CardTextRole) = CardTextStyle(role, body * role.factor)

    /** Alto de la foto: la de «Foto grande» va a sangre y es más alta. */
    fun photoHeight(big: Boolean): Float = h * when {
        format.isPdf -> if (big) 0.35f else 0.22f
        format == CardFormat.STORY -> if (big) 0.42f else 0.3f
        else -> if (big) 0.34f else 0.26f
    }
}

/** Apila bloques en una columna ([x], [width]) desde [y] hasta [bottom]. */
private class Stacker(
    val geo: Geo,
    val m: TextMeasurer,
    var y: Float,
    val bottom: Float,
    val x: Float = geo.margin,
    val width: Float = geo.contentW,
) {
    val blocks = mutableListOf<CardBlock>()
    var empty = true

    private fun gap(item: Item, first: Boolean) = when {
        first -> 0f
        item.newSection -> geo.sectionGap
        else -> geo.itemGap
    }

    fun measure(item: Item, at: Float): CardBlock {
        val style = geo.style(item.role)
        val lines = m.lines(item.text, style, width)
        return CardBlock(
            item.kind, item.text, style, x, at, width, lines * style.lineHeightPx, lines,
            continuation = item.continuation, separator = item.separator,
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

    /** Coloca [items] mientras quepan (un título de sección, solo si cabe con el siguiente); devuelve el resto. */
    fun pour(items: List<Item>): List<Item> {
        var i = 0
        while (i < items.size) {
            val item = items[i]
            val need = if (item.kind == CardBlockKind.HEADING) items.subList(i, minOf(i + 2, items.size)) else listOf(item)
            if (!fits(need)) break
            place(item)
            i++
        }
        return items.drop(i)
    }
}

/** Mayor n en 0..[max] con [ok] (que se supone creciente al revés: si n vale, valen los menores). */
private fun largest(max: Int, ok: (Int) -> Boolean): Int {
    var lo = 0
    var hi = max
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (ok(mid)) lo = mid else hi = mid - 1
    }
    return lo
}

/** [text] cortado con «…» para que ocupe como mucho [maxLines]: por palabras y, si ni una cabe, por caracteres. */
private fun capLines(text: String, style: CardTextStyle, width: Float, maxLines: Int, m: TextMeasurer): String {
    if (m.lines(text, style, width) <= maxLines) return text
    fun fits(t: String) = m.lines(t, style, width) <= maxLines
    val words = text.split(' ')
    val k = largest(words.size - 1) { fits(words.take(it).joinToString(" ").trimEnd() + "…") }
    if (k >= 1) return words.take(k).joinToString(" ").trimEnd() + "…"
    val c = largest(text.length - 1) { fits(text.take(it) + "…") }
    return text.take(maxOf(c, 1)) + "…"
}

private fun footerBlock(geo: Geo, m: TextMeasurer, text: String): CardBlock {
    val style = geo.style(CardTextRole.FOOTER)
    val lines = m.lines(text, style, geo.contentW)
    val height = lines * style.lineHeightPx
    return CardBlock(CardBlockKind.FOOTER, text, style, geo.margin, geo.h - geo.margin - height, geo.contentW, height, lines)
}

private fun photoBlock(geo: Geo, content: CardContent): CardBlock? = when (geo.design) {
    CardDesign.MINIMAL -> null
    CardDesign.BIG_PHOTO -> {
        val kind = if (content.hasPhoto) CardBlockKind.PHOTO else CardBlockKind.EMOJI
        val text = if (content.hasPhoto) "" else content.emoji
        CardBlock(kind, text, null, 0f, 0f, geo.w, geo.photoHeight(big = true))
    }
    CardDesign.CLASSIC, CardDesign.THEME ->
        if (content.hasPhoto) {
            CardBlock(CardBlockKind.PHOTO, "", null, geo.margin, geo.margin, geo.contentW, geo.photoHeight(big = false))
        } else {
            null
        }
}

/**
 * Lo que abre la ficha (o la primera página), a todo el ancho: foto, título, porciones y sabor.
 * Con dos columnas, [left] y [right] empiezan debajo; con una, [left] es [stacker] y [right] null.
 */
private class Start(val stacker: Stacker, val top: List<CardBlock>, val left: Stacker, val right: Stacker?) {
    val blocks: List<CardBlock> get() = top + stacker.blocks + (if (left !== stacker) left.blocks else emptyList()) +
        right?.blocks.orEmpty()
}

private fun start(content: CardContent, geo: Geo, m: TextMeasurer, bottom: Float, withPhoto: Boolean): Start {
    val photo = if (withPhoto) photoBlock(geo, content) else null
    val s = Stacker(geo, m, if (photo == null) geo.margin else photo.y + photo.height + geo.sectionGap, bottom)
    val top = listOfNotNull(photo).toMutableList()
    val titleStyle = geo.style(CardTextRole.TITLE)
    val title = Item(CardBlockKind.TITLE, capLines(content.title, titleStyle, geo.contentW, MAX_TITLE_LINES, m), CardTextRole.TITLE)
    if (photo != null && geo.design == CardDesign.BIG_PHOTO) {
        val b = s.measure(title, 0f)
        val y = photo.height - geo.margin * 0.5f - b.height
        if (y >= geo.margin) top += b.copy(y = y, overPhoto = true) else s.place(title)
    } else {
        s.place(title)
    }
    s.place(Item(CardBlockKind.META, content.servingsLine, CardTextRole.META))
    content.flavorLine?.let { s.place(Item(CardBlockKind.FLAVOR, it, CardTextRole.META)) }
    if (!geo.twoColumns) return Start(s, top, s, null)
    val y0 = s.y + geo.sectionGap
    return Start(
        s, top,
        Stacker(geo, m, y0, bottom, geo.margin, geo.columnW),
        Stacker(geo, m, y0, bottom, geo.rightX, geo.columnW),
    )
}

private fun heading(text: String) = Item(CardBlockKind.HEADING, text, CardTextRole.HEADING, newSection = true)

private fun ingredientItems(c: CardContent) =
    if (c.ingredients.isEmpty()) {
        emptyList()
    } else {
        listOf(heading("Ingredientes")) + c.ingredients.map { Item(CardBlockKind.INGREDIENT, "• $it", CardTextRole.BODY) }
    }

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
 * ingredientes («…y N ingredientes más»). En «Clásico» de la cuadrada y la horizontal, los
 * ingredientes van a la izquierda y los pasos y las notas a la derecha, y cada columna se recorta por
 * su cuenta. Nunca corta un bloque a mitad de línea.
 */
fun layoutImage(content: CardContent, format: CardFormat, design: CardDesign, measurer: TextMeasurer): ImageLayout {
    val notes = content.notes?.let { listOf(heading("Notas"), Item(CardBlockKind.NOTES, it, CardTextRole.BODY)) }.orEmpty()
    val left = ingredientItems(content)
    val right = stepItems(content) + notes
    for (scale in IMAGE_SCALES) {
        val geo = Geo(format, design, scale)
        val footer = footerBlock(geo, measurer, content.footer)
        val st = start(content, geo, measurer, footer.y - geo.sectionGap, withPhoto = true)
        val fits = if (st.right == null) st.left.fits(left + right) else st.left.fits(left) && st.right.fits(right)
        if (fits) {
            if (st.right == null) {
                (left + right).forEach(st.left::place)
            } else {
                left.forEach(st.left::place)
                right.forEach(st.right::place)
            }
            return ImageLayout(format, design, scale, st.blocks + footer, 0, 0, false)
        }
    }
    return truncatedImage(content, format, design, measurer)
}

private fun truncatedImage(content: CardContent, format: CardFormat, design: CardDesign, m: TextMeasurer): ImageLayout {
    val geo = Geo(format, design, MIN_CARD_SCALE)
    val footer = footerBlock(geo, m, content.footer)
    val bottom = footer.y - geo.sectionGap
    val ingredients = ingredientItems(content)
    var st = start(content, geo, m, bottom, withPhoto = true)
    var photoDropped = false
    if (!st.left.fits(ingredients) && st.top.any { it.kind == CardBlockKind.PHOTO || it.kind == CardBlockKind.EMOJI }) {
        st = start(content, geo, m, bottom, withPhoto = false)
        photoDropped = true
    }
    fun done(hiddenIngredients: Int, hiddenSteps: Int, notesTruncated: Boolean) = ImageLayout(
        format, design, MIN_CARD_SCALE, st.blocks + footer, hiddenIngredients, hiddenSteps, notesTruncated, photoDropped,
    )

    var hiddenIngredients = 0
    if (st.left.fits(ingredients)) {
        ingredients.forEach(st.left::place)
    } else {
        val n = content.ingredients.size
        val cut = { k: Int -> ingredients.take(1 + k) + more(moreIngredientsText(n - k)) }
        val k = (n - 1 downTo 0).firstOrNull { st.left.fits(cut(it)) }
        if (k != null) cut(k).forEach(st.left::place)
        hiddenIngredients = n - (k ?: 0)
        // En una sola columna no queda sitio para nada más.
        if (st.right == null) return done(hiddenIngredients, content.steps.size, content.notes != null)
    }
    val column = st.right ?: st.left
    val hiddenSteps = fitSteps(content, column, m)
    val notesTruncated = fitNotes(content, column)
    return done(hiddenIngredients, hiddenSteps, notesTruncated)
}

/** Pasos con «…y N pasos más», dejando sitio para el título de las notas y sus 3 primeras líneas. Devuelve los ocultos. */
private fun fitSteps(content: CardContent, s: Stacker, m: TextMeasurer): Int {
    val steps = stepItems(content)
    if (steps.isEmpty()) return 0
    val geo = s.geo
    val reserve = content.notes?.let { text ->
        val body = geo.style(CardTextRole.BODY)
        val noteLines = minOf(3, m.lines(text, body, s.width))
        geo.sectionGap + s.measure(heading("Notas"), 0f).height + geo.itemGap + noteLines * body.lineHeightPx
    } ?: 0f
    val n = content.steps.size
    val cut = { k: Int -> steps.take(1 + k) + listOfNotNull(if (k < n) more(moreStepsText(n - k)) else null) }
    val k = (n downTo 0).firstOrNull { s.fits(cut(it), reserve) }
    if (k != null) cut(k).forEach(s::place)
    return n - (k ?: 0)
}

/** Notas enteras si caben; si no, las palabras que quepan y «…». Devuelve si se recortaron. */
private fun fitNotes(content: CardContent, s: Stacker): Boolean {
    val text = content.notes ?: return false
    val whole = listOf(heading("Notas"), Item(CardBlockKind.NOTES, text, CardTextRole.BODY))
    if (s.fits(whole)) {
        whole.forEach(s::place)
        return false
    }
    val words = text.split(' ')
    val cut = { k: Int -> listOf(heading("Notas"), Item(CardBlockKind.NOTES, words.take(k).joinToString(" ").trimEnd() + "…", CardTextRole.BODY)) }
    val k = largest(words.size - 1) { s.fits(cut(it)) }
    if (k >= 1) cut(k).forEach(s::place)
    return true
}

/**
 * La ficha en PDF: todo el texto, partido en páginas sin perder nada (el título se corta a
 * [MAX_TITLE_LINES]). En «Clásico» de A4 y Carta la primera página lleva dos columnas (ingredientes a
 * la izquierda; pasos y notas a la derecha); lo que no cabe sigue en una columna en las páginas
 * siguientes, primero los ingredientes. Un título de sección nunca queda solo al final de una
 * columna; un bloque que no cabe ni en una página entera se parte por palabras y, si una palabra
 * tampoco cabe, por caracteres (el resto lleva [CardBlock.continuation] y su [CardBlock.separator]).
 * Cada página lleva el pie con «n/total» si hay más de una.
 */
fun paginatePdf(content: CardContent, format: CardFormat, design: CardDesign, measurer: TextMeasurer): List<PdfPage> {
    val geo = Geo(format, design, 1f)
    // El hueco del pie se mide con el texto más largo posible.
    val bottom = footerBlock(geo, measurer, "${content.footer} · 99/99").y - geo.sectionGap
    val notes = content.notes?.let { text ->
        val paragraphs = text.split('\n').filter { it.isNotBlank() }
        listOf(heading("Notas")) + paragraphs.map { Item(CardBlockKind.NOTES, it, CardTextRole.BODY) }
    }.orEmpty()
    val left = ingredientItems(content)
    val right = stepItems(content) + notes

    val pages = mutableListOf<List<CardBlock>>()
    val first = start(content, geo, measurer, bottom, withPhoto = true)
    val queue: ArrayDeque<Item>
    var top: List<CardBlock>
    var s: Stacker
    fun fresh() = Stacker(geo, measurer, geo.margin, bottom)
    if (first.right != null) {
        val restLeft = first.left.pour(left)
        val restRight = first.right.pour(right)
        pages += first.blocks
        queue = ArrayDeque(restLeft + restRight)
        top = emptyList()
        s = fresh()
    } else {
        queue = ArrayDeque(left + right)
        top = first.top
        s = first.stacker
    }
    fun newPage() {
        pages += top + s.blocks
        top = emptyList()
        s = fresh()
    }

    while (queue.isNotEmpty()) {
        val item = queue.removeFirst()
        if (item.kind == CardBlockKind.HEADING) {
            if (!s.fits(listOfNotNull(item, queue.firstOrNull())) && !s.empty) newPage()
            s.place(item)
            continue
        }
        if (s.fits(listOf(item))) {
            s.place(item)
            continue
        }
        if (!s.empty && fresh().fits(listOf(item))) {
            newPage()
            s.place(item)
            continue
        }
        // Más alto que lo que queda: se parte por palabras.
        val words = item.text.split(' ')
        val k = largest(words.size - 1) { s.fits(listOf(item.copy(text = words.take(it).joinToString(" ")))) }
        if (k >= 1) {
            s.place(item.copy(text = words.take(k).joinToString(" ")))
            val rest = words.drop(k).joinToString(" ")
            if (rest.isNotEmpty()) {
                queue.addFirst(item.copy(text = rest, newSection = false, continuation = true, separator = " "))
            }
        } else if (!s.empty) {
            queue.addFirst(item)
        } else {
            // Ni la primera palabra cabe en una página vacía: se parte por caracteres.
            val c = maxOf(1, largest(item.text.length - 1) { s.fits(listOf(item.copy(text = item.text.take(it)))) })
            s.place(item.copy(text = item.text.take(c)))
            queue.addFirst(item.copy(text = item.text.drop(c), newSection = false, continuation = true, separator = ""))
        }
        newPage()
    }
    if (top.isNotEmpty() || s.blocks.isNotEmpty() || pages.isEmpty()) pages += top + s.blocks
    val total = pages.size
    return pages.mapIndexed { i, blocks ->
        val text = if (total > 1) "${content.footer} · ${i + 1}/$total" else content.footer
        PdfPage(format, design, i + 1, total, blocks + footerBlock(geo, measurer, text))
    }
}
