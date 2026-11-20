package app.recetario.pantry

import java.time.Instant

/** Un artículo de la despensa: [name] es lo que escribió el usuario y [normalized] su clave única. */
data class PantryItem(val id: String, val name: String, val normalized: String, val addedAt: Instant)
