package app.recetario.cooking

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.AppContainer
import app.recetario.MainActivity
import app.recetario.RecetarioApp
import app.recetario.testing.installTestContainer
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.ShadowPowerManager
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class TimerServiceTest {
    private lateinit var container: AppContainer
    private val app: RecetarioApp get() = ApplicationProvider.getApplicationContext()
    private val notifications: NotificationManager get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        container = installTestContainer()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() {
        container.timerManager.timers.value.forEach { container.timerManager.cancel(it.id) }
        container.database.close()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun startService(): TimerService =
        Robolectric.buildService(TimerService::class.java).create().startCommand(0, 1).get().also { idle() }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun finishedNotifications() =
        shadowOf(notifications).allNotifications.filter { it.channelId == TimerNotifications.CHANNEL_DONE }

    @Test
    fun startCommand_goesForeground_listingEachTimer() {
        container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = "s2")
        container.timerManager.start("Paso 3: Cuece a fuego medio…", 1200, id = "s3")
        val service = startService()

        val shadow = shadowOf(service)
        assertThat(shadow.lastForegroundNotificationId).isEqualTo(TimerNotifications.ONGOING_ID)
        val n = shadow.lastForegroundNotification
        assertThat(n.channelId).isEqualTo(TimerNotifications.CHANNEL_RUNNING)
        assertThat(n.title()).isEqualTo("2 temporizadores")
        assertThat(n.text()).isEqualTo("Paso 2 · 5:00, Paso 3 · 20:00")
        assertThat(n.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(shadow.isStoppedBySelf).isFalse()
        // Con temporizadores en marcha la CPU no se duerme (si no, con la pantalla apagada sonarían tarde).
        assertThat(ShadowPowerManager.getLatestWakeLock()?.isHeld).isTrue()
    }

    @Test
    fun oneTimer_titleIsItsLabel() {
        container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = "s2")
        val n = shadowOf(startService()).lastForegroundNotification
        assertThat(n.title()).isEqualTo("Paso 2: Sofríe la cebolla…")
        assertThat(n.text()).isEqualTo("Paso 2 · 5:00")
    }

    @Test
    fun cancellingTheLastTimer_stopsTheService_andReleasesTheWakeLock() {
        container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = "s2")
        val service = startService()
        val lock = ShadowPowerManager.getLatestWakeLock()
        assertThat(lock?.isHeld).isTrue()

        container.timerManager.cancel("s2")
        idle()

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        assertThat(lock?.isHeld).isFalse()
    }

    @Test
    fun pausedTimers_keepTheServiceWithoutWakeLock() {
        container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = "s2")
        val service = startService()
        container.timerManager.pause("s2")
        idle()
        assertThat(shadowOf(service).isStoppedBySelf).isFalse()
        assertThat(ShadowPowerManager.getLatestWakeLock()?.isHeld).isFalse()
    }

    @Test
    fun finishedTimer_postsTheAlarmNotification_andStopsWhenNoneAreLeft() {
        container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = "s2")
        val service = startService()

        container.timerManager.tick(container.timerManager.now() + 300_000)
        idle()

        val posted = finishedNotifications().single()
        assertThat(posted.title()).isEqualTo("⏱ ¡Tiempo! — Paso 2: Sofríe la cebolla…")
        assertThat(posted.category).isEqualTo(Notification.CATEGORY_ALARM)
        assertThat(posted.flags and Notification.FLAG_INSISTENT).isNotEqualTo(0)
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()

        // Apagarlo desde la app quita el aviso.
        TimerNotifications.cancelFinished(app, "s2")
        assertThat(finishedNotifications()).isEmpty()
    }

    @Test
    fun finishedTimer_withOthersRunning_keepsTheServiceGoing() {
        container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = "s2")
        container.timerManager.start("Paso 3: Cuece a fuego medio…", 1200, id = "s3")
        val service = startService()
        container.timerManager.tick(container.timerManager.now() + 300_000)
        idle()
        assertThat(finishedNotifications()).hasSize(1)
        assertThat(shadowOf(service).isStoppedBySelf).isFalse()
    }

    @Test
    fun withoutNotificationPermission_theServiceRingsItself_untilTheTimerIsDismissed() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = "s2")
        val service = startService()

        container.timerManager.tick(container.timerManager.now() + 300_000)
        idle()
        assertThat(finishedNotifications()).isEmpty()
        assertThat(shadowOf(service).isStoppedBySelf).isFalse() // sigue vivo mientras suena

        container.timerManager.cancel("s2")
        idle()
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    /** Termina los temporizadores [ids] (arrancados con 5 min) y devuelve su aviso de fin. */
    private fun finished(vararg ids: String): List<Notification> {
        ids.forEach { container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = it) }
        container.timerManager.tick(container.timerManager.now() + 300_000)
        return ids.map { id -> TimerNotifications.finished(app, container.timerManager.timers.value.single { it.id == id }) }
    }

    @Test
    fun finishedNotification_stopsAlertingAfterTenMinutes() {
        assertThat(finished("s2").single().timeoutAfter).isEqualTo(10 * 60_000L)
    }

    @Test
    fun tappingTheFinishedNotification_opensTheAppAndTurnsThatTimerOff() {
        val (forS2, _) = finished("s2", "s3")
        val open = shadowOf(forS2.contentIntent).savedIntent
        assertThat(open.component?.className).isEqualTo(MainActivity::class.java.name)
        ActivityScenario.launch<MainActivity>(open).use { idle() }
        // Solo se apaga el suyo.
        assertThat(container.timerManager.timers.value.map { it.id }).containsExactly("s3")
    }

    @Test
    fun dismissingTheFinishedNotification_turnsThatTimerOff() {
        val (forS2, forS3) = finished("s2", "s3")
        assertThat(forS2.deleteIntent).isNotNull()
        assertThat(forS2.deleteIntent).isNotEqualTo(forS3.deleteIntent)
        val delete = shadowOf(forS2.deleteIntent)
        assertThat(delete.isBroadcast).isTrue()
        val intent = delete.savedIntent
        val receiver = Class.forName(intent.component!!.className).getDeclaredConstructor().newInstance() as BroadcastReceiver
        receiver.onReceive(app, intent)
        assertThat(container.timerManager.timers.value.map { it.id }).containsExactly("s3")
    }

    @Test
    fun tappingAnOldFinishedNotification_neverTurnsOffTheSameStepRunningAgain() {
        val (forS2) = finished("s2")
        val open = shadowOf(forS2.contentIntent).savedIntent
        // Se vuelve a cocinar la receta: el mismo paso (mismo id) está otra vez en marcha.
        container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = "s2")

        ActivityScenario.launch<MainActivity>(open).use { idle() }

        assertThat(container.timerManager.timers.value.single().isRunning).isTrue()
    }

    @Test
    fun reopeningFromRecents_withTheNotificationThatOpenedTheApp_doesNotTurnAnythingOff() {
        val (forS2) = finished("s2")
        // Android repite el intent con el que se abrió la tarea, marcado como lanzado desde Recientes.
        val replay = Intent(shadowOf(forS2.contentIntent).savedIntent).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        ActivityScenario.launch<MainActivity>(replay).use { idle() }

        assertThat(container.timerManager.timers.value.map { it.id }).containsExactly("s2")
    }

    @Test
    fun aNotificationTapArrivingWhileTheAppIsOpen_turnsOffOnlyAFinishedTimer() {
        val (forS2, forS3) = finished("s2", "s3")
        container.timerManager.start("Paso 3: Cuece a fuego medio…", 1200, id = "s3")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            controller.newIntent(Intent(shadowOf(forS3.contentIntent).savedIntent))
            controller.newIntent(Intent(shadowOf(forS2.contentIntent).savedIntent).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
            idle()
            // s3 está otra vez en marcha y s2 solo se repitió desde Recientes: los dos siguen.
            assertThat(container.timerManager.timers.value.map { it.id }).containsExactly("s2", "s3")

            controller.newIntent(Intent(shadowOf(forS2.contentIntent).savedIntent))
            idle()
            assertThat(container.timerManager.timers.value.map { it.id }).containsExactly("s3")
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun dismissingAnOldFinishedNotification_neverTurnsOffARunningTimer() {
        val (forS2) = finished("s2")
        container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = "s2")
        // El aviso de la vez anterior se descarta (o caduca a los 10 minutos) con el paso otra vez en marcha.
        val intent = shadowOf(forS2.deleteIntent).savedIntent
        val receiver = Class.forName(intent.component!!.className).getDeclaredConstructor().newInstance() as BroadcastReceiver

        receiver.onReceive(app, intent)

        assertThat(container.timerManager.timers.value.single().isRunning).isTrue()
    }

    @Test
    fun alarmRinger_ifTheAlarmSoundFailsWhilePreparing_ringsTheNotificationSoundInstead() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(30_000, 0) }
        val players = mutableListOf<MediaPlayer>()
        ShadowMediaPlayer.setCreateListener { player, _ -> players += player }
        val ringer = AlarmRinger(app)
        ringer.start()
        idle()

        // El sonido de alarma elegido no se puede reproducir (archivo borrado, formato raro…).
        shadowOf(players.single()).invokeErrorListener(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0)
        idle()

        assertThat(players).hasSize(2)
        assertThat(shadowOf(players[0]).state).isEqualTo(ShadowMediaPlayer.State.END)
        val fallback = players[1]
        assertThat(fallback.isLooping).isTrue()
        assertThat(shadowOf(fallback).state).isEqualTo(ShadowMediaPlayer.State.STARTED)
        assertThat(shadowOf(fallback).audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_ALARM)
        ringer.stop()
        assertThat(shadowOf(fallback).state).isEqualTo(ShadowMediaPlayer.State.END)
    }

    @Test
    fun withoutNotificationPermission_theAlarmRingsForTenMinutesAtMost() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container.timerManager.start("Paso 2: Sofríe la cebolla…", 300, id = "s2")
        val service = startService()
        container.timerManager.tick(container.timerManager.now() + 300_000)
        idle()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(9))
        assertThat(shadowOf(service).isStoppedBySelf).isFalse() // sigue sonando
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(1).plusSeconds(1))
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test
    fun alarmRinger_loopsTheAlarmSoundWithAMediaPlayerUntilStopped() {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(30_000, 0) }
        val players = mutableListOf<MediaPlayer>()
        ShadowMediaPlayer.setCreateListener { player, _ -> players += player }
        val ringer = AlarmRinger(app)

        ringer.start()
        idle()

        // Un MediaPlayer en bucle funciona en todas las versiones (Ringtone.setLooping solo desde Android 9).
        val player = players.single()
        assertThat(player.isLooping).isTrue()
        assertThat(shadowOf(player).state).isEqualTo(ShadowMediaPlayer.State.STARTED)
        assertThat(shadowOf(player).audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_ALARM)

        ringer.stop()
        assertThat(shadowOf(player).state).isEqualTo(ShadowMediaPlayer.State.END)
    }

    @Test
    fun channels_haveTheRightNamesImportanceAndSound() {
        TimerNotifications.createChannels(app)
        val running = notifications.getNotificationChannel(TimerNotifications.CHANNEL_RUNNING)
        assertThat(running.name.toString()).isEqualTo("Temporizadores")
        assertThat(running.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)

        val done = notifications.getNotificationChannel(TimerNotifications.CHANNEL_DONE)
        assertThat(done.name.toString()).isEqualTo("Temporizador terminado")
        assertThat(done.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(done.sound).isEqualTo(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        assertThat(done.audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_ALARM)
        assertThat(done.shouldVibrate()).isTrue()
    }

    @Test
    fun ongoingNotification_countsDownToTheSoonestRunningTimer() {
        val now = 10_000L
        val wall = 1_000_000L
        val timers = listOf(
            CookingTimer("a", "Paso 3: Cuece", 1200, endsAtElapsedMs = now + 1_170_000, remainingMsWhenPaused = null, finished = false),
            CookingTimer("b", "Paso 2: Sofríe", 300, endsAtElapsedMs = now + 298_000, remainingMsWhenPaused = null, finished = false),
            CookingTimer("c", "Paso 4: Reposa", 600, endsAtElapsedMs = null, remainingMsWhenPaused = 400_000, finished = false),
        )
        val n = TimerNotifications.ongoing(app, timers, elapsedNow = now, wallNow = wall)
        assertThat(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isTrue()
        assertThat(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        assertThat(n.`when`).isEqualTo(wall + 298_000)
        assertThat(n.title()).isEqualTo("3 temporizadores")
        assertThat(n.text()).isEqualTo("Paso 2 · 4:58, Paso 3 · 19:30, Paso 4 · ⏸ 6:40")
        assertThat(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
            .isEqualTo("Paso 2 · 4:58\nPaso 3 · 19:30\nPaso 4 · ⏸ 6:40")
        assertThat(n.contentIntent).isNotNull()
    }

    @Test
    fun ongoingNotification_allPaused_hasNoChronometer() {
        val timers = listOf(
            CookingTimer("c", "Paso 4: Reposa", 600, endsAtElapsedMs = null, remainingMsWhenPaused = 400_000, finished = false),
        )
        val n = TimerNotifications.ongoing(app, timers, elapsedNow = 0, wallNow = 0)
        assertThat(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isFalse()
        assertThat(n.text()).isEqualTo("Paso 4 · ⏸ 6:40")
    }

    @Test
    fun manifest_declaresThePermissionsAndTheSpecialUseService() {
        val pm = app.packageManager
        @Suppress("DEPRECATION")
        val permissions = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions!!.toList()
        assertThat(permissions).containsAtLeast(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
            Manifest.permission.VIBRATE,
            Manifest.permission.WAKE_LOCK,
        )
        @Suppress("DEPRECATION")
        val service = pm.getServiceInfo(ComponentName(app, TimerService::class.java), 0)
        assertThat(service.exported).isFalse()
        assertThat(service.foregroundServiceType).isEqualTo(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }
}
