package app.recetario.core.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import java.io.File

/**
 * Crea con [helper] una base de datos v1 de verdad (la de la 0.1.0) llamada [name], con una categoría propia y la
 * receta «r1» («Sopa de tortilla»), y la cierra. Su archivo es `context.getDatabasePath(name)`.
 */
fun createV1Database(helper: MigrationTestHelper, name: String) {
    helper.createDatabase(name, 1).use { db ->
        db.execSQL(
            "INSERT INTO categories (id, name, emoji, colorHex, baseType, sortOrder, isDefault) " +
                "VALUES ('cat-propia', 'Antojitos', '🌮', '#C0583A', 'FOOD', 7, 0)",
        )
        db.execSQL(
            "INSERT INTO recipes (id, title, categoryId, coverPhoto, description, totalMinutes, servings, " +
                "difficulty, sweet, salty, sour, bitter, spicy, intensity, rich, serveTemp, moments, alcoholic, " +
                "course, notes, favorite, sourceUrl, aiGenerated, createdAt, updatedAt) VALUES " +
                "('r1', 'Sopa de tortilla', 'cat-propia', NULL, 'Caldo de jitomate', 45, 4, " +
                "'EASY', 0, 3, 1, 0, 2, 'MEDIUM', 1, 'HOT', 'LUNCH,DINNER', NULL, 'MAIN', '', 0, NULL, 0, " +
                "1763500000000, 1763510000000)",
        )
    }
}

/**
 * `user_version` y títulos de las recetas de un archivo de base de datos, leído a mano: una copia, en solo lectura
 * (no deja `-wal` ni `-shm`); la base de datos de la app ([live]), como la abre Room (en WAL).
 */
fun readDatabaseFile(file: File, live: Boolean = false): Pair<Int, List<String>> {
    val flags = if (live) SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING else SQLiteDatabase.OPEN_READONLY
    val db = SQLiteDatabase.openDatabase(file.path, null, flags)
    try {
        val titles = db.rawQuery("SELECT title FROM recipes ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        return db.version to titles
    } finally {
        db.close()
    }
}
