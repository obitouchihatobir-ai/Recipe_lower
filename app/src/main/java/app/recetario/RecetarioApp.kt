package app.recetario

import android.app.Application
import android.content.Context

class RecetarioApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Dependencias de la app, creadas a mano. Cada tarea registra aquí lo que crea. */
class AppContainer(context: Context)
