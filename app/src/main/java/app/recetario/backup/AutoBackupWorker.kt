package app.recetario.backup

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.recetario.RecetarioApp
import java.util.concurrent.TimeUnit

/** Nombre único de la copia semanal en WorkManager. */
const val WEEKLY_BACKUP_WORK = "copia-semanal"

/**
 * Copia semanal (§8.5): primero la copia del teléfono, comprobada; después, si hay archivo de Drive, la copia allí
 * ([BackupManager.backupNow]). Sin recetas no escribe nada y termina bien. Solo se reintenta (con espera creciente)
 * si falla la copia del teléfono, y como mucho 3 veces: después espera a la semana siguiente. Los fallos quedan
 * anotados (`localError`, `lastError`) y se muestran en Inicio y en Copias.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = (applicationContext as RecetarioApp).container.backupManager
        return when (val result = manager.backupNow()) {
            AutoBackupResult.LocalFailed -> if (runAttemptCount >= MAX_RETRIES) {
                Log.w(TAG, "La copia semanal en el teléfono sigue fallando; se intentará la semana que viene")
                Result.success()
            } else {
                Log.w(TAG, "No se pudo hacer la copia semanal en el teléfono; se reintentará")
                Result.retry()
            }
            AutoBackupResult.NothingToSave, is AutoBackupResult.Saved -> {
                if (result is AutoBackupResult.Saved && result.drive == false) Log.w(TAG, "La copia semanal no llegó a Drive")
                Result.success()
            }
        }
    }

    private companion object {
        const val TAG = "AutoBackupWorker"

        /** Con `runAttemptCount` (0 la primera vez) de 3 o más ya no se reintenta. */
        const val MAX_RETRIES = 3
    }
}

/** Cada 7 días; si falla la copia del teléfono, se reintenta a los 15 minutos, 30, 60… */
fun weeklyBackupRequest(): PeriodicWorkRequest =
    PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
        .build()

/** Programa la copia semanal; si ya lo estaba, la deja como está (KEEP: no se reinicia la cuenta de los 7 días). */
fun scheduleWeeklyBackup(workManager: WorkManager) {
    workManager.enqueueUniquePeriodicWork(WEEKLY_BACKUP_WORK, ExistingPeriodicWorkPolicy.KEEP, weeklyBackupRequest())
}
