package app.recetario.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.today.MealTimes
import app.recetario.today.ThermalThresholds
import app.recetario.today.TimeRange
import app.recetario.weather.LocationMode
import app.recetario.weather.Place
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.LocalTime
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class BackupSettingsV2bTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val envs = mutableListOf<BackupTestEnv>()

    private fun env() = BackupTestEnv(tmp).also { envs += it }

    @After
    fun tearDown() = envs.forEach { it.close() }

    private val custom = MealTimes(
        breakfast = TimeRange(LocalTime.of(8, 0), LocalTime.of(10, 30)),
        dinner = TimeRange(LocalTime.of(20, 0), LocalTime.of(23, 30)),
    )
    private val lima = Place("Lima", "Lima", "Perú", -12.04637, -77.04279)
    private val quito = Place("Quito", "Pichincha", "Ecuador", -0.22985, -78.52495)

    private fun json(zip: ByteArray): String = ZipInputStream(zip.inputStream()).use { z ->
        generateSequence { z.nextEntry }.first { it.name == BACKUP_JSON_ENTRY }
        z.readBytes().decodeToString()
    }

    @Test
    fun backup_v2b_settingsRoundTrip_andOldBackupKeepsDefaults() = runBlocking<Unit> {
        val a = env()
        a.settings.setMealTimes(custom)
        a.settings.setThermalThresholds(ThermalThresholds(12, 28))
        a.settings.setThemeBySun(true)
        a.settings.setCity(lima)
        val zip = a.exportBytes()
        // La ubicación nunca va en la copia.
        val text = json(zip)
        assertThat(text).doesNotContain("Lima")
        assertThat(text).doesNotContain("-12.0")

        val b = env()
        b.settings.setCity(quito)
        // «Combinar» no cambia los ajustes.
        b.import(zip, ImportMode.MERGE)
        assertThat(b.settings.mealTimes.first()).isEqualTo(MealTimes())
        // «Reemplazar todo» sí, salvo la ubicación, que es de este teléfono.
        b.import(zip, ImportMode.REPLACE_ALL)
        assertThat(b.settings.mealTimes.first()).isEqualTo(custom)
        assertThat(b.settings.thermalThresholds.first()).isEqualTo(ThermalThresholds(12, 28))
        assertThat(b.settings.themeBySun.first()).isTrue()
        assertThat(b.settings.city.first()).isEqualTo(quito)
        assertThat(b.settings.locationMode.first()).isEqualTo(LocationMode.CITY)

        // Una copia de antes (la 0.1.0, sin estos campos): al reemplazar, los de serie.
        b.import(v1BackupZip(), ImportMode.REPLACE_ALL)
        assertThat(b.settings.mealTimes.first()).isEqualTo(MealTimes())
        assertThat(b.settings.thermalThresholds.first()).isEqualTo(ThermalThresholds())
        assertThat(b.settings.themeBySun.first()).isFalse()
        assertThat(b.settings.city.first()).isEqualTo(quito)
    }
}
