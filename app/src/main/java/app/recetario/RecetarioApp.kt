package app.recetario

import android.app.Application
import android.content.Context
import app.recetario.core.data.AppDatabase
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.data.RoomRecipeRepository
import java.io.File
import java.time.Clock
class RecetarioApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Dependencias de la app, creadas a mano. Cada tarea registra aquí lo que crea. */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    val database: AppDatabase = AppDatabase.create(context)
    val photoStore = PhotoStore(File(context.filesDir, "photos"))
    val recipeRepository: RecipeRepository = RoomRecipeRepository(database, photoStore, clock)
}
