package app.recetario.recipes.model

import app.recetario.recipes.quantity.MeasureUnit
import java.time.Instant

enum class BaseType(val label: String) { FOOD("Comida"), DRINK("Bebida"), DESSERT("Postre"), OTHER("Otro") }

enum class Difficulty(val label: String) { EASY("Fácil"), MEDIUM("Media"), HARD("Difícil") }

enum class Intensity(val label: String) { LIGHT("Suave"), MEDIUM("Media"), INTENSE("Intensa") }

enum class ServeTemp(val label: String) { HOT("Caliente"), COLD("Fría"), ROOM("Ambiente") }

enum class Moment(val label: String) {
    BREAKFAST("Desayuno"),
    LUNCH("Comida"),
    SNACK("Merienda"),
    DINNER("Cena"),
    NIGHT("Aperitivo o noche"),
}

enum class Course(val label: String) { STARTER("Entrada"), MAIN("Principal"), SIDE("Acompañamiento") }

/** Los cinco sabores van de 0 a 3. */
data class FlavorProfile(
    val sweet: Int = 0,
    val salty: Int = 0,
    val sour: Int = 0,
    val bitter: Int = 0,
    val spicy: Int = 0,
    val intensity: Intensity = Intensity.MEDIUM,
    val rich: Boolean = false,
)

data class Ingredient(
    val id: String,
    val section: String?,
    val name: String,
    val quantity: Double?,
    val unit: MeasureUnit,
    val customUnit: String?,
    val note: String,
)

data class Step(
    val id: String,
    val text: String,
    val photos: List<String>,
    val timerSeconds: Int?,
    val ingredientIds: Set<String>,
)

data class Recipe(
    val id: String,
    val title: String,
    val categoryId: String,
    val coverPhoto: String?,
    val description: String,
    val totalMinutes: Int?,
    val servings: Int,
    val difficulty: Difficulty,
    val flavor: FlavorProfile,
    val serveTemp: ServeTemp?,
    val moments: Set<Moment>,
    val alcoholic: Boolean?,
    val course: Course?,
    val ingredients: List<Ingredient>,
    val steps: List<Step>,
    val tags: List<String>,
    val notes: String,
    val favorite: Boolean,
    val sourceUrl: String?,
    val aiGenerated: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class RecipeSummary(
    val id: String,
    val title: String,
    val categoryId: String,
    val coverPhoto: String?,
    val totalMinutes: Int?,
    val flavor: FlavorProfile,
    val serveTemp: ServeTemp?,
    val moments: Set<Moment>,
    val alcoholic: Boolean?,
    val difficulty: Difficulty,
    val favorite: Boolean,
    val tags: List<String>,
    val ingredientNames: List<String>,
    val updatedAt: Instant,
)

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val baseType: BaseType,
    val sortOrder: Int,
    val isDefault: Boolean,
)
