package app.recetario.core.data

import android.content.Context
import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La primera migración es el riesgo principal de la etapa 2: una base de datos v1 con datos
 * debe llegar a v2 sin perder ni cambiar nada (diseño de la etapa 2, §6.1).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val dbName = "migracion-prueba.db"

    @Test
    fun migrate1to2_keepsAllData() {
        val before = helper.createDatabase(dbName, 1).use { db ->
            insertV1Data(db)
            dump(db)
        }
        // Que la prueba no pase por estar vacía: cada tabla v1 tiene filas.
        V1_TABLES.forEach { table -> assertThat(before.getValue(table)).isNotEmpty() }

        val migrated = helper.runMigrationsAndValidate(dbName, AppDatabase.VERSION, true, MIGRATION_1_2)

        assertThat(dump(migrated)).isEqualTo(before)
        migrated.query("PRAGMA foreign_key_check").use { assertThat(it.count).isEqualTo(0) }
        migrated.close()
    }

    @Test
    fun migrate1to2_createsEmptyNewTables() {
        helper.createDatabase(dbName, 1).use { insertV1Data(it) }
        helper.runMigrationsAndValidate(dbName, AppDatabase.VERSION, true, MIGRATION_1_2).use { db ->
            NEW_TABLES.forEach { table ->
                db.query("SELECT COUNT(*) FROM `$table`").use {
                    it.moveToFirst()
                    assertThat(it.getInt(0)).isEqualTo(0)
                }
            }
        }

        // La base de datos que abre la app (misma configuración que AppDatabase.create) lee las tablas nuevas.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.create(context, dbName)
        try {
            runBlocking {
                assertThat(db.diaryDao().getAll()).isEmpty()
                assertThat(db.diaryDao().getAllPhotos()).isEmpty()
                assertThat(db.pantryDao().getAll()).isEmpty()
                assertThat(db.mealPlanDao().getAll()).isEmpty()
                assertThat(db.pairingRatingDao().getAll()).isEmpty()
                assertThat(db.recipeDao().observeRecipe("r1").first()?.recipe?.title).isEqualTo("Sopa de tortilla")
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun migrate1to2_onlyCreates() {
        val statements = MIGRATION_1_2_SQL
        assertThat(statements).isNotEmpty()
        statements.forEach { sql ->
            val upper = sql.uppercase()
            assertThat(upper).doesNotContain("DROP")
            assertThat(upper).doesNotContain("ALTER")
            assertThat(upper).doesNotContain("DELETE FROM")
            assertThat(upper).doesNotContain("UPDATE ")
            assertThat(upper).doesNotContain("INSERT")
            assertThat(upper.startsWith("CREATE TABLE") || upper.startsWith("CREATE INDEX") ||
                upper.startsWith("CREATE UNIQUE INDEX")).isTrue()
        }
    }

    private fun insertV1Data(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO categories (id, name, emoji, colorHex, baseType, sortOrder, isDefault) " +
                "VALUES ('cat-propia', 'Antojitos', '🌮', '#C0583A', 'FOOD', 7, 0)",
        )
        db.execSQL(
            "INSERT INTO recipes (id, title, categoryId, coverPhoto, description, totalMinutes, servings, " +
                "difficulty, sweet, salty, sour, bitter, spicy, intensity, rich, serveTemp, moments, alcoholic, " +
                "course, notes, favorite, sourceUrl, aiGenerated, createdAt, updatedAt) VALUES " +
                "('r1', 'Sopa de tortilla', 'cat-propia', 'portada.jpg', 'Caldo de jitomate', 45, 4, " +
                "'EASY', 0, 3, 1, 0, 2, 'MEDIUM', 1, 'HOT', 'LUNCH,DINNER', NULL, 'MAIN', 'Con aguacate', 1, NULL, 0, " +
                "1763500000000, 1763510000000)",
        )
        db.execSQL(
            "INSERT INTO ingredients (id, recipeId, position, section, name, quantity, unit, customUnit, note) VALUES " +
                "('i1', 'r1', 0, NULL, 'Jitomate', 500.0, 'GRAM', NULL, 'maduro'), " +
                "('i2', 'r1', 1, 'Para servir', 'Chile pasilla', 2.5, 'CUSTOM', 'piezas', '')",
        )
        db.execSQL(
            "INSERT INTO steps (id, recipeId, position, text, timerSeconds) VALUES " +
                "('s1', 'r1', 0, 'Licúa el jitomate', 300)",
        )
        db.execSQL("INSERT INTO step_photos (stepId, position, fileName) VALUES ('s1', 0, 'paso-1.jpg')")
        db.execSQL("INSERT INTO step_ingredients (stepId, ingredientId) VALUES ('s1', 'i1')")
        db.execSQL("INSERT INTO tags (id, name, normalized) VALUES (1, 'Picante', 'picante')")
        db.execSQL("INSERT INTO recipe_tags (recipeId, tagId, position) VALUES ('r1', 1, 0)")
        db.execSQL(
            "INSERT INTO shopping_items (id, name, quantity, unit, customUnit, section, checked, sourceRecipeId, " +
                "createdAt) VALUES ('c1', 'Aguacate', 2.0, 'PIECE', NULL, 'FRUITS_VEG', 1, 'r1', 1763520000000)",
        )
    }

    /** Todas las filas de las tablas v1, columna por columna, en orden de `rowid`. */
    private fun dump(db: SupportSQLiteDatabase): Map<String, List<Map<String, Any?>>> =
        V1_TABLES.associateWith { table ->
            db.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.columnNames.indices.associate { i -> cursor.getColumnName(i) to cursor.valueAt(i) })
                    }
                }
            }
        }

    private fun Cursor.valueAt(i: Int): Any? = when (getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
        Cursor.FIELD_TYPE_BLOB -> getBlob(i).toList()
        else -> getString(i)
    }

    private companion object {
        val V1_TABLES = listOf(
            "categories", "recipes", "ingredients", "steps", "step_photos",
            "step_ingredients", "tags", "recipe_tags", "shopping_items",
        )
        val NEW_TABLES = listOf("cook_log", "cook_log_photos", "pantry_items", "meal_plan", "pairing_ratings")
    }
}
