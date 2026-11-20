package app.recetario.backup

import app.recetario.core.ui.theme.ThemeChoice
import app.recetario.core.ui.theme.ThemeSettings
import app.recetario.diary.CookLogEntry
import app.recetario.pantry.PantryItem
import app.recetario.pantry.pantryKey
import app.recetario.pantry.samePantryName
import app.recetario.recipes.model.BaseType
import app.recetario.recipes.model.Category
import app.recetario.recipes.model.Course
import app.recetario.recipes.model.Difficulty
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Ingredient
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.Moment
import app.recetario.recipes.model.Recipe
import app.recetario.recipes.model.ServeTemp
import app.recetario.recipes.model.Step
import app.recetario.recipes.quantity.MeasureUnit
import app.recetario.shopping.ShopSection
import app.recetario.shopping.ShoppingItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Versión del formato de `backup.json`. Hay que subirla con cualquier cambio que una versión anterior de la
 * app no sepa leer: un campo obligatorio nuevo, un valor nuevo de un enum (unidad, sección…) o un cambio de
 * significado. Los campos nuevos opcionales (con valor por defecto) no la cambian: las versiones anteriores
 * ignoran las claves que no conocen.
 *
 * - 1: recetas, categorías, compra y ajustes (la 0.1.0).
 * - 2: además diario (con sus fotos en el manifiesto), despensa, menú, valoraciones de maridaje y los básicos de
 *   la despensa. Una copia 1 se lee con esas listas vacías.
 */
const val BACKUP_SCHEMA_VERSION = 2

/** Nombre del JSON dentro del zip y carpeta de las fotos (`images/<archivo>`). */
const val BACKUP_JSON_ENTRY = "backup.json"
const val BACKUP_IMAGES_PREFIX = "images/"

/** Límites al leer una copia: un `backup.json` o una foto mayores no son nuestros (500 recetas ocupan pocos MB). */
const val BACKUP_MAX_JSON_BYTES = 16 * 1024 * 1024
const val BACKUP_MAX_IMAGE_BYTES = 25 * 1024 * 1024

/**
 * Contenido de `backup.json`. Las fechas van en ISO-8601 (`Instant` en UTC; las horas, `HH:mm`).
 * No incluye el estado de las copias, el permiso de notificaciones, los borradores ni la clave de la IA.
 * [images] es el manifiesto: las fotos que de verdad van en `images/`. Si falta alguna, la copia está
 * incompleta (por ejemplo, un zip cortado). Vacío en las copias sin fotos.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val appVersion: String,
    val createdAt: String,
    val categories: List<BackupCategory>,
    val recipes: List<BackupRecipe>,
    val shoppingItems: List<BackupShoppingItem>,
    val settings: BackupSettings,
    val images: List<String> = emptyList(),
    val cookLog: List<BackupCookLog> = emptyList(),
    val pantry: List<BackupPantryItem> = emptyList(),
    val mealPlan: List<BackupMealPlan> = emptyList(),
    val pairingRatings: List<BackupPairingRating> = emptyList(),
)

@Serializable
data class BackupCategory(
    val id: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val baseType: BaseType,
    val sortOrder: Int,
    val isDefault: Boolean,
)

/**
 * Receta completa. [categoryBaseType] repite el tipo base de su categoría: si al importar la categoría no
 * existe (ni en el teléfono ni en la copia), la receta pasa a la categoría de serie de ese tipo.
 */
@Serializable
data class BackupRecipe(
    val id: String,
    val title: String,
    val categoryId: String,
    val categoryBaseType: BaseType? = null,
    val coverPhoto: String?,
    val description: String,
    val totalMinutes: Int?,
    val servings: Int,
    val difficulty: Difficulty,
    val flavor: BackupFlavor,
    val serveTemp: ServeTemp?,
    val moments: List<Moment>,
    val alcoholic: Boolean?,
    val course: Course?,
    val ingredients: List<BackupIngredient>,
    val steps: List<BackupStep>,
    val tags: List<String>,
    val notes: String,
    val favorite: Boolean,
    val sourceUrl: String?,
    val aiGenerated: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupFlavor(
    val sweet: Int,
    val salty: Int,
    val sour: Int,
    val bitter: Int,
    val spicy: Int,
    val intensity: Intensity,
    val rich: Boolean,
)

/** El orden de la lista es el de la receta. */
@Serializable
data class BackupIngredient(
    val id: String,
    val section: String?,
    val name: String,
    val quantity: Double?,
    val unit: MeasureUnit,
    val customUnit: String?,
    val note: String,
)

/** [photos] son nombres de archivo de `images/`, en orden; [ingredientIds], los ingredientes que usa el paso. */
@Serializable
data class BackupStep(
    val id: String,
    val text: String,
    val photos: List<String>,
    val timerSeconds: Int?,
    val ingredientIds: List<String>,
)

@Serializable
data class BackupShoppingItem(
    val id: String,
    val name: String,
    val quantity: Double?,
    val unit: MeasureUnit,
    val customUnit: String?,
    val section: ShopSection,
    val checked: Boolean,
    val sourceRecipeId: String?,
    val createdAt: String,
)

/** Una entrada del diario: [date] es `AAAA-MM-DD`; [photos], nombres de archivo de `images/`, en orden (4 como mucho). */
@Serializable
data class BackupCookLog(
    val id: String,
    val recipeId: String?,
    val recipeTitle: String,
    val date: String,
    val rating: Int?,
    val note: String,
    val photos: List<String>,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupPantryItem(
    val id: String,
    val name: String,
    val normalized: String,
    val addedAt: String,
)

/** Un hueco del menú: [slot] es `DESAYUNO`, `COMIDA` o `CENA`. */
@Serializable
data class BackupMealPlan(
    val id: String,
    val date: String,
    val slot: String,
    val recipeId: String,
    val servings: Int,
)

/** Valoración de una pareja: cada lado es `RECIPE` o `CATALOG`. */
@Serializable
data class BackupPairingRating(
    val pairKey: String,
    val aKind: String,
    val aId: String,
    val bKind: String,
    val bId: String,
    val liked: Boolean,
    val ratedAt: String,
)

/** [pantryBasics]: null en las copias v1 (al reemplazar, quedan los de serie). */
@Serializable
data class BackupSettings(
    val themeSettings: BackupThemeSettings,
    val cupMl: Int,
    val pantryBasics: List<String>? = null,
)

@Serializable
data class BackupThemeSettings(
    val auto: Boolean,
    val choice: ThemeChoice,
    val dayTheme: ThemeChoice,
    val nightStart: String,
    val nightEnd: String,
)

private val backupJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Huecos del menú semanal, en el orden del día. */
val MEAL_SLOTS = listOf("DESAYUNO", "COMIDA", "CENA")

/** Tipos de cada lado de una valoración de maridaje. */
val PAIRING_SIDE_KINDS = setOf("RECIPE", "CATALOG")

/** Un plato del menú semanal: [slot] es uno de [MEAL_SLOTS]. */
data class PlannedMeal(val id: String, val date: LocalDate, val slot: String, val recipeId: String, val servings: Int)

/** Valoración de una pareja de maridaje (ver `pairing_ratings`). */
data class PairingRating(
    val pairKey: String,
    val aKind: String,
    val aId: String,
    val bKind: String,
    val bId: String,
    val liked: Boolean,
    val ratedAt: Instant,
)

fun encodeBackup(file: BackupFile): String = backupJson.encodeToString(BackupFile.serializer(), file)

sealed interface BackupDecodeResult {
    data class Ok(val file: BackupFile) : BackupDecodeResult

    /** La copia es de una versión más nueva de la app (aunque el resto no se entienda). */
    data object NewerSchema : BackupDecodeResult

    /** No es un `backup.json` del Recetario o está dañado. */
    data object Invalid : BackupDecodeResult
}

/**
 * Lee el JSON una sola vez; mira primero `schemaVersion`, para avisar de una copia más nueva aunque su formato
 * haya cambiado, y después convierte el árbol ya leído.
 */
fun decodeBackup(text: String): BackupDecodeResult {
    val element = try {
        backupJson.parseToJsonElement(text)
    } catch (_: Exception) {
        return BackupDecodeResult.Invalid
    }
    val version = try {
        element.jsonObject["schemaVersion"]?.jsonPrimitive?.int
    } catch (_: Exception) {
        null
    } ?: return BackupDecodeResult.Invalid
    if (version < 1) return BackupDecodeResult.Invalid
    if (version > BACKUP_SCHEMA_VERSION) return BackupDecodeResult.NewerSchema
    return try {
        BackupDecodeResult.Ok(backupJson.decodeFromJsonElement(BackupFile.serializer(), element))
    } catch (_: Exception) {
        BackupDecodeResult.Invalid
    }
}

/** Arma la copia a partir de los datos de la app; [images] son las fotos que irán en el zip. */
fun backupFileOf(
    appVersion: String,
    createdAt: Instant,
    categories: List<Category>,
    recipes: List<Recipe>,
    shoppingItems: List<ShoppingItem>,
    themeSettings: ThemeSettings,
    cupMl: Int,
    images: List<String> = emptyList(),
    cookLog: List<CookLogEntry> = emptyList(),
    pantry: List<PantryItem> = emptyList(),
    mealPlan: List<PlannedMeal> = emptyList(),
    pairingRatings: List<PairingRating> = emptyList(),
    pantryBasics: List<String>? = null,
): BackupFile {
    val baseTypes = categories.associate { it.id to it.baseType }
    return BackupFile(
        appVersion = appVersion,
        createdAt = createdAt.toString(),
        categories = categories.map { it.toBackup() },
        recipes = recipes.map { it.toBackup(baseTypes[it.categoryId]) },
        shoppingItems = shoppingItems.map { it.toBackup() },
        settings = BackupSettings(themeSettings.toBackup(), cupMl, pantryBasics),
        images = images,
        cookLog = cookLog.map { it.toBackup() },
        pantry = pantry.map { it.toBackup() },
        mealPlan = mealPlan.map { it.toBackup() },
        pairingRatings = pairingRatings.map { it.toBackup() },
    )
}

/** La copia no se puede usar (fechas ilegibles, identificadores repetidos, rutas de foto peligrosas…). */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class ImportedRecipe(val recipe: Recipe, val categoryBaseType: BaseType?)

/** Datos de una copia ya comprobados y convertidos al modelo de la app, listos para escribir. */
data class ImportData(
    val categories: List<Category>,
    val recipes: List<ImportedRecipe>,
    val shoppingItems: List<ShoppingItem>,
    val themeSettings: ThemeSettings,
    val cupMl: Int,
    val cookLog: List<CookLogEntry> = emptyList(),
    val pantry: List<PantryItem> = emptyList(),
    val mealPlan: List<PlannedMeal> = emptyList(),
    val pairingRatings: List<PairingRating> = emptyList(),
    /** Null en las copias v1: al reemplazar, vuelven los de serie. */
    val pantryBasics: List<String>? = null,
) {
    /** Fotos a las que apuntan las recetas (portadas y pasos) y el diario. */
    val photoNames: Set<String>
        get() = recipes.flatMapTo(LinkedHashSet()) { (r, _) -> listOfNotNull(r.coverPhoto) + r.steps.flatMap { it.photos } }
            .apply { cookLog.flatMapTo(this) { it.photos } }
}

/**
 * Comprueba toda la copia y la convierte; lanza [InvalidBackupException] si algo no vale. Se llama antes de
 * tocar la base de datos. Los identificadores no pueden estar vacíos ni repetirse (en ingredientes y pasos,
 * en toda la copia) y el manifiesto de fotos solo admite nombres simples de archivo. Una receta que apunta a
 * una foto con un nombre que no es simple se trata como si la foto faltara: se importa sin ella (ese nombre
 * nunca es una foto nuestra y, guardado, podría hacer borrar otro archivo). Porciones y sabores fuera de rango
 * se ajustan a lo que la app sabe mostrar.
 *
 * Esquema 2: el diario (valoración vacía o de 1 a 5; sus fotos, como las de las recetas, y 4 como mucho), la
 * despensa (`normalized` se recalcula; un artículo repetido —también en plural— se queda una vez), el menú (huecos
 * conocidos y sin dos platos en el mismo) y las valoraciones (lados `RECIPE` o `CATALOG`, `pairKey` único).
 */
fun BackupFile.toImportData(): ImportData {
    if (images.any { !isSafeBackupFileName(it) }) throw InvalidBackupException("Nombre de foto no permitido en el manifiesto")
    requireUniqueIds("categoría", categories.map { it.id })
    requireUniqueIds("receta", recipes.map { it.id })
    requireUniqueIds("ingrediente", recipes.flatMap { r -> r.ingredients.map { it.id } })
    requireUniqueIds("paso", recipes.flatMap { r -> r.steps.map { it.id } })
    requireUniqueIds("artículo", shoppingItems.map { it.id })
    requireUniqueIds("entrada del diario", cookLog.map { it.id })
    requireUniqueIds("artículo de la despensa", pantry.map { it.id })
    requireUniqueIds("plato del menú", mealPlan.map { it.id })
    requireUniqueIds("valoración", pairingRatings.map { it.pairKey })
    val meals = mealPlan.map { it.toPlannedMeal() }
    if (meals.map { it.date to it.slot }.toSet().size != meals.size) throw InvalidBackupException("Hay dos platos en el mismo hueco del menú")
    return ImportData(
        categories = categories.map { it.toCategory() },
        recipes = recipes.map { ImportedRecipe(it.toRecipe(), it.categoryBaseType) },
        shoppingItems = shoppingItems.map { it.toShoppingItem() },
        themeSettings = settings.themeSettings.toThemeSettings(),
        cupMl = settings.cupMl,
        cookLog = cookLog.map { it.toEntry() },
        pantry = pantry.map { it.toPantryItem() }.fold(ArrayList()) { kept, item ->
            if (item.normalized.isNotEmpty() && kept.none { samePantryName(it.name, item.name) || it.normalized == item.normalized }) kept += item
            kept
        },
        mealPlan = meals,
        pairingRatings = pairingRatings.map { it.toPairingRating() },
        pantryBasics = settings.pantryBasics,
    )
}

private fun requireUniqueIds(what: String, ids: List<String>) {
    if (ids.any { it.isBlank() }) throw InvalidBackupException("Hay un identificador de $what vacío")
    if (ids.toSet().size != ids.size) throw InvalidBackupException("Hay identificadores de $what repetidos")
}

private fun instant(text: String): Instant = try {
    Instant.parse(text)
} catch (e: Exception) {
    throw InvalidBackupException("Fecha ilegible: «$text»", e)
}

private fun date(text: String): LocalDate = try {
    LocalDate.parse(text)
} catch (e: Exception) {
    throw InvalidBackupException("Fecha ilegible: «$text»", e)
}

private fun time(text: String): LocalTime = try {
    LocalTime.parse(text)
} catch (e: Exception) {
    throw InvalidBackupException("Hora ilegible: «$text»", e)
}


private fun Category.toBackup() = BackupCategory(id, name, emoji, colorHex, baseType, sortOrder, isDefault)

private fun BackupCategory.toCategory() = Category(id, name, emoji, colorHex, baseType, sortOrder, isDefault)

private fun Recipe.toBackup(categoryBaseType: BaseType?) = BackupRecipe(
    id = id, title = title, categoryId = categoryId, categoryBaseType = categoryBaseType, coverPhoto = coverPhoto,
    description = description, totalMinutes = totalMinutes, servings = servings, difficulty = difficulty,
    flavor = BackupFlavor(flavor.sweet, flavor.salty, flavor.sour, flavor.bitter, flavor.spicy, flavor.intensity, flavor.rich),
    serveTemp = serveTemp, moments = moments.toList(), alcoholic = alcoholic, course = course,
    ingredients = ingredients.map { BackupIngredient(it.id, it.section, it.name, it.quantity, it.unit, it.customUnit, it.note) },
    steps = steps.map { BackupStep(it.id, it.text, it.photos, it.timerSeconds, it.ingredientIds.toList()) },
    tags = tags, notes = notes, favorite = favorite, sourceUrl = sourceUrl, aiGenerated = aiGenerated,
    createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
)

private fun BackupRecipe.toRecipe() = Recipe(
    id = id, title = title, categoryId = categoryId, coverPhoto = coverPhoto?.takeIf(::isSafeBackupFileName), description = description,
    totalMinutes = totalMinutes, servings = servings.coerceAtLeast(1), difficulty = difficulty,
    flavor = FlavorProfile(
        sweet = flavor.sweet.coerceIn(0, 3), salty = flavor.salty.coerceIn(0, 3), sour = flavor.sour.coerceIn(0, 3),
        bitter = flavor.bitter.coerceIn(0, 3), spicy = flavor.spicy.coerceIn(0, 3),
        intensity = flavor.intensity, rich = flavor.rich,
    ),
    serveTemp = serveTemp, moments = moments.toSet(), alcoholic = alcoholic, course = course,
    ingredients = ingredients.map { Ingredient(it.id, it.section, it.name, it.quantity, it.unit, it.customUnit, it.note) },
    steps = steps.map { Step(it.id, it.text, it.photos.filter(::isSafeBackupFileName), it.timerSeconds, it.ingredientIds.toSet()) },
    tags = tags, notes = notes, favorite = favorite, sourceUrl = sourceUrl, aiGenerated = aiGenerated,
    createdAt = instant(createdAt), updatedAt = instant(updatedAt),
)

private fun ShoppingItem.toBackup() =
    BackupShoppingItem(id, name, quantity, unit, customUnit, section, checked, sourceRecipeId, createdAt.toString())

private fun BackupShoppingItem.toShoppingItem() =
    ShoppingItem(id, name, quantity, unit, customUnit, section, checked, sourceRecipeId, instant(createdAt))

private fun CookLogEntry.toBackup() = BackupCookLog(
    id, recipeId, recipeTitle, date.toString(), rating, note, photos, createdAt.toString(), updatedAt.toString(),
)

private const val MAX_DIARY_PHOTOS = 4

private fun BackupCookLog.toEntry(): CookLogEntry {
    if (rating != null && rating !in 1..5) throw InvalidBackupException("Valoración fuera de rango: $rating")
    return CookLogEntry(
        id = id, recipeId = recipeId?.takeIf { it.isNotBlank() }, recipeTitle = recipeTitle, date = date(date), rating = rating,
        note = note, photos = photos.filter(::isSafeBackupFileName).distinct().take(MAX_DIARY_PHOTOS),
        createdAt = instant(createdAt), updatedAt = instant(updatedAt),
    )
}

private fun PantryItem.toBackup() = BackupPantryItem(id, name, normalized, addedAt.toString())

private fun BackupPantryItem.toPantryItem() = PantryItem(id, name.trim(), pantryKey(name), instant(addedAt))

private fun PlannedMeal.toBackup() = BackupMealPlan(id, date.toString(), slot, recipeId, servings)

private fun BackupMealPlan.toPlannedMeal(): PlannedMeal {
    if (slot !in MEAL_SLOTS) throw InvalidBackupException("Hueco del menú desconocido: «$slot»")
    if (recipeId.isBlank()) throw InvalidBackupException("Plato del menú sin receta")
    return PlannedMeal(id, date(date), slot, recipeId, servings.coerceAtLeast(1))
}

private fun PairingRating.toBackup() = BackupPairingRating(pairKey, aKind, aId, bKind, bId, liked, ratedAt.toString())

private fun BackupPairingRating.toPairingRating(): PairingRating {
    if (aKind !in PAIRING_SIDE_KINDS || bKind !in PAIRING_SIDE_KINDS) throw InvalidBackupException("Tipo de maridaje desconocido")
    return PairingRating(pairKey, aKind, aId, bKind, bId, liked, instant(ratedAt))
}

private fun ThemeSettings.toBackup() = BackupThemeSettings(auto, choice, dayTheme, nightStart.toString(), nightEnd.toString())

private fun BackupThemeSettings.toThemeSettings() = ThemeSettings(auto, choice, dayTheme, time(nightStart), time(nightEnd))

/** Qué es una entrada del zip. Solo se extraen `backup.json` y las fotos planas de `images/`. */
sealed interface BackupEntry {
    data object Json : BackupEntry
    data class Image(val fileName: String) : BackupEntry

    /** La propia carpeta `images/` (algunos compresores la añaden). */
    data object Folder : BackupEntry

    /** Un archivo suelto que no es nuestro (se ignora). */
    data object Other : BackupEntry

    /** Ruta que podría salirse de la carpeta de destino (zip-slip) o con subcarpetas: la copia se rechaza. */
    data object Unsafe : BackupEntry
}

fun classifyBackupEntry(name: String): BackupEntry = when {
    name == BACKUP_JSON_ENTRY -> BackupEntry.Json
    name == BACKUP_IMAGES_PREFIX -> BackupEntry.Folder
    name.startsWith(BACKUP_IMAGES_PREFIX) -> {
        val file = name.removePrefix(BACKUP_IMAGES_PREFIX)
        if (isSafeBackupFileName(file)) BackupEntry.Image(file) else BackupEntry.Unsafe
    }
    isSafeBackupFileName(name) -> BackupEntry.Other
    else -> BackupEntry.Unsafe
}

/** Un nombre simple de archivo: sin separadores, sin `..`, sin caracteres de control y de 255 caracteres como mucho. */
fun isSafeBackupFileName(name: String): Boolean =
    name.isNotEmpty() && name.length <= 255 && name != "." && ".." !in name &&
        name.none { it == '/' || it == '\\' || it.code < 0x20 }
