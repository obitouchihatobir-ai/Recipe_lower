package app.recetario.cooking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.recetario.MainActivity
import app.recetario.R

/**
 * Notificaciones de los temporizadores:
 * - «Temporizadores» (importancia baja, sin sonido): la notificación en curso del servicio, con la cuenta atrás
 *   del que antes termina y una línea por temporizador («Paso 2 · 4:58»).
 * - «Temporizador terminado» (importancia alta): «⏱ ¡Tiempo! — Paso 2: …» con el sonido de alarma del sistema
 *   y vibración; insistente, suena hasta que se mira o se apaga en la app.
 */
object TimerNotifications {
    const val CHANNEL_RUNNING = "temporizadores"
    const val CHANNEL_DONE = "temporizador_terminado"
    const val ONGOING_ID = 1001

    /** Id de los avisos de fin; cada temporizador lo publica con su propio id como etiqueta. */
    const val FINISHED_ID = 1002

    /** Medio segundo de vibración, una pausa y otra vez. */
    val VIBRATION = longArrayOf(0, 500, 400, 500, 400, 800)

    val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** El sonido de alarma que el usuario eligió en el sistema. */
    fun alarmSound(): Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

    fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_RUNNING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Temporizadores")
                    .setDescription("Los temporizadores del modo cocina mientras corren")
                    .setShowBadge(false)
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_DONE, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Temporizador terminado")
                    .setDescription("Suena y vibra cuando termina un temporizador")
                    .setSound(alarmSound(), alarmAttributes)
                    .setVibrationEnabled(true)
                    .setVibrationPattern(VIBRATION)
                    .build(),
            ),
        )
    }

    /** True si la app puede publicar notificaciones (permiso en Android 13 o superior). */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** True si el aviso de fin llegará a verse y sonar; si no, el servicio hace sonar la alarma él mismo. */
    fun finishedCanAlert(context: Context): Boolean {
        if (!canPost(context)) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val importance = manager.getNotificationChannelCompat(CHANNEL_DONE)?.importance ?: NotificationManagerCompat.IMPORTANCE_HIGH
        return importance != NotificationManagerCompat.IMPORTANCE_NONE
    }

    /**
     * La notificación en curso. [elapsedNow] es la hora del reloj de los temporizadores y [wallNow] la del reloj
     * de pared (el cronómetro de la notificación cuenta hacia un instante de pared).
     */
    fun ongoing(context: Context, timers: List<CookingTimer>, elapsedNow: Long, wallNow: Long): Notification {
        val active = timers.filter { !it.finished }
        val lines = ongoingTimerLines(timers, elapsedNow)
        val soonest = active.filter { it.isRunning }.minByOrNull { it.remainingMs(elapsedNow) }
        val title = when (active.size) {
            0 -> "⏱ ¡Tiempo!"
            1 -> active.single().label
            else -> "${active.size} temporizadores"
        }
        val text = if (lines.isEmpty()) timers.filter { it.finished }.joinToString(", ") { it.label } else lines.joinToString(", ")
        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (lines.isEmpty()) text else lines.joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
            .apply {
                if (soonest != null) {
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setShowWhen(true)
                    setWhen(wallNow + soonest.remainingMs(elapsedNow))
                } else {
                    setShowWhen(false)
                }
            }
            .build()
    }

    /** «⏱ ¡Tiempo! — Paso 2: Sofríe la cebolla…», con alarma y vibración, que suena hasta que se atiende. */
    fun finished(context: Context, timer: CookingTimer): Notification =
        NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(finishedTimerTitle(timer.label))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            // El sonido y la vibración los pone el canal (minSdk 26).
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }

    @SuppressLint("MissingPermission") // canPost comprueba el permiso.
    fun notifyOngoing(context: Context, notification: Notification) {
        if (canPost(context)) NotificationManagerCompat.from(context).notify(ONGOING_ID, notification)
    }

    @SuppressLint("MissingPermission") // canPost comprueba el permiso.
    fun notifyFinished(context: Context, timer: CookingTimer) {
        if (canPost(context)) NotificationManagerCompat.from(context).notify(timer.id, FINISHED_ID, finished(context, timer))
    }

    /** Quita el aviso de fin de [timerId] (al apagarlo o sumarle un minuto desde la app). */
    fun cancelFinished(context: Context, timerId: String) {
        NotificationManagerCompat.from(context).cancel(timerId, FINISHED_ID)
    }

    /** Vuelve a la app tal como estaba (normalmente, al modo cocina). */
    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
