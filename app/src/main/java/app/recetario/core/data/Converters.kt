package app.recetario.core.data

import androidx.room.TypeConverter
import app.recetario.recipes.model.Moment
import java.time.Instant

/** Las fechas se guardan como milisegundos y el conjunto de momentos como nombres separados por comas. */
class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun momentsToText(value: Set<Moment>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun textToMoments(value: String): Set<Moment> =
        value.split(',').filter { it.isNotBlank() }.map { Moment.valueOf(it) }.toSet()
}
