package app.recetario

import android.app.Application
import android.content.Context
import app.recetario.core.data.AppDatabase
import app.recetario.recipes.data.PhotoStore
import app.recetario.recipes.data.RecipeRepository
import app.recetario.recipes.data.RoomRecipeRepository
import app.recetario.shopping.RoomShoppingRepository
import app.recetario.shopping.ShoppingRepository
import kotlinx.coroutines.flow.flowOf
import java.io.File
import java.time.Clock

class RecetarioApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Dependencias de la app, creadas a mano. Cada tarea registra aquí lo que crea.
 * Las pruebas pasan su propio reloj y una base de datos en memoria.
 */
class AppContainer(
    context: Context,
    val clock: Clock = Clock.systemDefaultZone(),
    val database: AppDatabase = AppDatabase.create(context),
) {
    val photoStore = PhotoStore(File(context.filesDir, "photos"))
    val draftsDir = File(context.filesDir, "drafts")
    val recipeRepository: RecipeRepository = RoomRecipeRepository(database, photoStore, clock)

    /** El tamaño de la taza (240 ml) lo conectará a los ajustes la Tarea 12. */
    val shoppingRepository: ShoppingRepository = RoomShoppingRepository(database, clock, flowOf(240))
}
