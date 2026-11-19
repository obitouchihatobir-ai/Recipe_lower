package app.recetario.cooking

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.recetario.RecetarioApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano de los temporizadores del modo cocina (tipo `specialUse`, «cooking_timer»).
 * - Se arranca al iniciar un temporizador (siempre con la app delante) y se para cuando no queda ninguno activo.
 * - Mantiene la notificación en curso al día cada segundo y un wake lock parcial mientras alguno corre: sin él,
 *   con la pantalla apagada la CPU se duerme y el aviso llegaría tarde.
 * - Cuando uno termina publica «⏱ ¡Tiempo! — …» (alarma y vibración del canal). Si las notificaciones no pueden
 *   sonar (permiso denegado o bloqueadas), hace sonar la alarma del sistema él mismo hasta que se apaga en la app
 *   o pasan 10 minutos (lo mismo que dura el aviso de la notificación, `TimerNotifications.ALARM_MS`).
 *
 * Observa `TimerManager.timers`: los avisos salen de la transición a terminado en la misma pasada que decide si
 * el servicio sigue, así nunca se para antes de avisar.
 */
class TimerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val inForeground = MutableStateFlow(false)
    private val refresh = MutableStateFlow(0)
    private lateinit var manager: TimerManager
    private lateinit var ringer: AlarmRinger

    /** Terminados que ya avisaron (para no repetir el aviso). */
    private val alerted = mutableSetOf<String>()

    /** Terminados que está haciendo sonar el propio servicio. */
    private val ringingFor = mutableSetOf<String>()
    private var ringTimeout: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        manager = (application as RecetarioApp).container.timerManager
        ringer = AlarmRinger(this)
        TimerNotifications.createChannels(this)
        alerted += manager.timers.value.filter { it.finished }.map { it.id }
        scope.launch {
            combine(manager.timers, inForeground, refresh) { timers, foreground, _ -> timers to foreground }
                .collectLatest { (timers, foreground) -> render(timers, foreground) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Siempre enseguida: un servicio lanzado con startForegroundService debe pasar a primer plano.
        val notification = TimerNotifications.ongoing(this, manager.timers.value, manager.now(), System.currentTimeMillis())
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(TimerNotifications.ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(TimerNotifications.ONGOING_ID, notification)
            }
            inForeground.value = true
        } catch (_: RuntimeException) {
            // El sistema no lo deja pasar a primer plano: los temporizadores siguen, pero sin servicio.
            stopSelf()
        }
        return START_NOT_STICKY // los temporizadores viven en memoria: tras matar el proceso no hay nada que retomar
    }

    override fun onDestroy() {
        scope.cancel()
        ringer.stop()
        releaseWakeLock()
        super.onDestroy()
    }

    private suspend fun render(timers: List<CookingTimer>, foreground: Boolean) {
        alertNewlyFinished(timers)
        stopRingingForDismissed(timers)
        if (!foreground) return
        if (timers.none { !it.finished } && ringingFor.isEmpty()) {
            stopTimers()
            return
        }
        while (true) {
            val now = manager.now()
            TimerNotifications.notifyOngoing(this, TimerNotifications.ongoing(this, timers, now, System.currentTimeMillis()))
            holdWakeLock(timers, now)
            if (timers.none { it.isRunning }) return
            delay(1_000)
        }
    }

    private fun alertNewlyFinished(timers: List<CookingTimer>) {
        val finished = timers.filter { it.finished }
        for (timer in finished) {
            if (timer.id in alerted) continue
            alerted += timer.id
            if (TimerNotifications.finishedCanAlert(this)) {
                TimerNotifications.notifyFinished(this, timer)
            } else {
                ringingFor += timer.id
                ringer.start()
                ringTimeout?.cancel()
                ringTimeout = scope.launch {
                    delay(TimerNotifications.ALARM_MS)
                    ringingFor.clear()
                    ringer.stop()
                    refresh.value++
                }
            }
        }
        alerted.retainAll(finished.map { it.id }.toSet())
    }

    private fun stopRingingForDismissed(timers: List<CookingTimer>) {
        ringingFor.retainAll(timers.filter { it.finished }.map { it.id }.toSet())
        if (ringingFor.isEmpty()) {
            ringTimeout?.cancel()
            ringer.stop()
        }
    }

    private fun stopTimers() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        inForeground.value = false
        stopSelf()
    }

    /** Wake lock parcial mientras corre alguno o suena la alarma; caduca solo por si algo falla. */
    private fun holdWakeLock(timers: List<CookingTimer>, now: Long) {
        val longest = timers.filter { it.isRunning }.maxOfOrNull { it.remainingMs(now) }
        val ms = when {
            longest != null -> longest + 60_000
            ringingFor.isNotEmpty() -> TimerNotifications.ALARM_MS + 5_000
            else -> null
        }
        if (ms == null) {
            releaseWakeLock()
            return
        }
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "recetario:temporizadores")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        lock.acquire(ms.coerceAtMost(MAX_WAKE_MS))
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    companion object {
        private const val MAX_WAKE_MS = 12 * 60 * 60 * 1000L

        /** Arranca (o despierta) el servicio. Llamar desde la app en primer plano, al tocar un temporizador. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TimerService::class.java))
            } catch (_: IllegalStateException) {
                // La app ya no estaba en primer plano: los temporizadores siguen dentro de la app.
            }
        }
    }
}

/**
 * La alarma del sistema en bucle, con vibración, cuando el aviso de fin no puede sonar por sí mismo. Usa un
 * MediaPlayer en bucle con uso de alarma: funciona en todas las versiones (Ringtone.setLooping es de Android 9).
 */
internal class AlarmRinger(private val context: Context) {
    private var player: MediaPlayer? = null
    private var vibrating = false

    fun start() {
        if (player != null || vibrating) return
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        player = loop(TimerNotifications.alarmSound(), fallback) ?: loop(fallback, null)
        val vibrator = vibrator()
        if (vibrator?.hasVibrator() == true) {
            runCatching {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VibrationEffect.createWaveform(TimerNotifications.VIBRATION, 0), TimerNotifications.alarmAttributes)
                vibrating = true
            }
        }
    }

    /**
     * Prepara y arranca [sound] en bucle; null si no se puede (sin sonido de alarma elegido, archivo ilegible…). Si
     * falla después, al prepararlo o sonando, se cambia una vez por [fallback] (el sonido de notificación): sin eso,
     * quien no dio permiso para notificar solo tendría la vibración.
     */
    private fun loop(sound: Uri, fallback: Uri?): MediaPlayer? {
        val mediaPlayer = MediaPlayer()
        return try {
            mediaPlayer.setAudioAttributes(TimerNotifications.alarmAttributes)
            mediaPlayer.setDataSource(context, sound)
            mediaPlayer.isLooping = true
            mediaPlayer.setOnPreparedListener { it.start() }
            mediaPlayer.setOnErrorListener { failed, what, extra ->
                Log.w(TAG, "El sonido de la alarma falló ($what, $extra)")
                failed.release()
                if (player === failed) player = fallback?.let { loop(it, null) }
                true
            }
            mediaPlayer.prepareAsync()
            mediaPlayer
        } catch (_: Exception) {
            // IOException, IllegalStateException, IllegalArgumentException o SecurityException: sin este sonido.
            mediaPlayer.release()
            null
        }
    }

    fun stop() {
        player?.let {
            runCatching { it.stop() } // en preparación no se puede parar: basta con liberarlo
            it.release()
        }
        player = null
        if (vibrating) runCatching { vibrator()?.cancel() }
        vibrating = false
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private companion object {
        const val TAG = "AlarmRinger"
    }
}
