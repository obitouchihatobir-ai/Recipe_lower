package app.recetario.cooking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.recetario.RecetarioApp

/** Al descartar el aviso «⏱ ¡Tiempo!» se apaga ese temporizador (la pantalla deja de mostrar «¡Tiempo!»). */
class TimerDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as RecetarioApp
        TimerNotifications.dismissFromIntent(intent, app.container.timerManager)
    }
}
