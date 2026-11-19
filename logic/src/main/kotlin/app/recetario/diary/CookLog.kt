package app.recetario.diary

import java.time.Instant
import java.time.LocalDate

/** Una vez que se cocinó una receta. `rating` es null o de 1 a 5; `photos` tiene 4 como máximo. */
data class CookLogEntry(
    val id: String,
    val recipeId: String?,
    val recipeTitle: String,
    val date: LocalDate,
    val rating: Int?,
    val note: String,
    val photos: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
