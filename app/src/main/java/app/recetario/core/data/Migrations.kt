package app.recetario.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versión 2 (etapa 2): diario, despensa, menú semanal y valoraciones de maridaje.
 * **Solo crea** tablas e índices: no toca ninguna tabla de la versión 1 (diseño de la etapa 2, §6.1).
 * El SQL es el que Room genera para las entidades (ver `schemas/…/2.json`); lo comprueba `MigrationTest`.
 */
internal val MIGRATION_1_2_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `cook_log` (`id` TEXT NOT NULL, `recipeId` TEXT, `recipeTitle` TEXT NOT NULL, " +
        "`date` TEXT NOT NULL, `rating` INTEGER, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) " +
        "ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_cook_log_recipeId_date` ON `cook_log` (`recipeId`, `date`)",
    "CREATE TABLE IF NOT EXISTS `cook_log_photos` (`logId` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
        "`fileName` TEXT NOT NULL, PRIMARY KEY(`logId`, `position`), FOREIGN KEY(`logId`) REFERENCES " +
        "`cook_log`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `pantry_items` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
        "`normalized` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pantry_items_normalized` ON `pantry_items` (`normalized`)",
    "CREATE TABLE IF NOT EXISTS `meal_plan` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `slot` TEXT NOT NULL, " +
        "`recipeId` TEXT NOT NULL, `servings` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`recipeId`) " +
        "REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_meal_plan_date_slot` ON `meal_plan` (`date`, `slot`)",
    "CREATE INDEX IF NOT EXISTS `index_meal_plan_date` ON `meal_plan` (`date`)",
    "CREATE INDEX IF NOT EXISTS `index_meal_plan_recipeId` ON `meal_plan` (`recipeId`)",
    "CREATE TABLE IF NOT EXISTS `pairing_ratings` (`pairKey` TEXT NOT NULL, `aKind` TEXT NOT NULL, " +
        "`aId` TEXT NOT NULL, `bKind` TEXT NOT NULL, `bId` TEXT NOT NULL, `liked` INTEGER NOT NULL, " +
        "`ratedAt` INTEGER NOT NULL, PRIMARY KEY(`pairKey`))",
    "CREATE INDEX IF NOT EXISTS `index_pairing_ratings_aId` ON `pairing_ratings` (`aId`)",
    "CREATE INDEX IF NOT EXISTS `index_pairing_ratings_bId` ON `pairing_ratings` (`bId`)",
)

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_1_2_SQL.forEach(db::execSQL)
    }
}
