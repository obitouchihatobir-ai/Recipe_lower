package app.recetario.core.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Categorías de serie: IDs fijos, se pueden renombrar pero no borrar. */
object DefaultCategories {
    const val FOOD_ID = "cat-comidas"
    const val DRINK_ID = "cat-bebidas"
    const val DESSERT_ID = "cat-postres"

    val ids = setOf(FOOD_ID, DRINK_ID, DESSERT_ID)

    private data class Seed(val id: String, val name: String, val emoji: String, val color: String, val baseType: String)

    private val seeds = listOf(
        Seed(FOOD_ID, "Comidas", "🍲", "#C8553D", "FOOD"),
        Seed(DRINK_ID, "Bebidas", "🍹", "#2A7F8E", "DRINK"),
        Seed(DESSERT_ID, "Postres", "🍮", "#B5733A", "DESSERT"),
    )

    fun seed(db: SupportSQLiteDatabase) {
        seeds.forEachIndexed { index, s ->
            db.execSQL(
                "INSERT INTO categories (id, name, emoji, colorHex, baseType, sortOrder, isDefault) VALUES (?, ?, ?, ?, ?, ?, 1)",
                arrayOf<Any>(s.id, s.name, s.emoji, s.color, s.baseType, index),
            )
        }
    }

    val callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
    }
}
