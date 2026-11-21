package app.recetario.pairing

import app.recetario.recipes.model.Course
import app.recetario.recipes.model.FlavorProfile
import app.recetario.recipes.model.Intensity
import app.recetario.recipes.model.ServeTemp

/** Elemento de prueba: por defecto, una receta propia de comida sin sabores apuntados. */
internal fun item(
    id: String,
    role: PairRole = PairRole.FOOD,
    sweet: Int = 0,
    salty: Int = 0,
    sour: Int = 0,
    bitter: Int = 0,
    spicy: Int = 0,
    intensity: Intensity = Intensity.MEDIUM,
    rich: Boolean = false,
    kind: String = "RECIPE",
    course: Course? = null,
    alcoholic: Boolean? = null,
    serveTemp: ServeTemp? = null,
    name: String = id,
    article: String? = null,
) = PairingItem(
    kind = kind,
    id = id,
    name = name,
    role = role,
    course = course,
    alcoholic = alcoholic,
    flavor = FlavorProfile(sweet, salty, sour, bitter, spicy, intensity, rich),
    serveTemp = serveTemp,
    article = article,
)
