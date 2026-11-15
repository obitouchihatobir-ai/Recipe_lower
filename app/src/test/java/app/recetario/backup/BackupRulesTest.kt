package app.recetario.backup

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recetario.R
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Copia de seguridad de Android (§8.5): solo la base de datos y los ajustes (una lista cerrada), sin fotos (no caben en
 * sus 25 MB), sin las copias del teléfono, sin borradores, sin el estado del teléfono y sin la caché; igual en la nube
 * y al pasar a otro teléfono.
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private data class Rule(val section: String, val include: Boolean, val domain: String, val path: String)

    /** Reglas del XML compilado, con la sección en la que están (`full-backup-content`, `cloud-backup`…). */
    private fun rules(id: Int): List<Rule> {
        val parser: XmlResourceParser = context.resources.getXml(id)
        val result = mutableListOf<Rule>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "include", "exclude" -> result += Rule(
                    section,
                    include = parser.name == "include",
                    domain = parser.getAttributeValue(null, "domain"),
                    path = parser.getAttributeValue(null, "path"),
                )
                else -> section = parser.name
            }
        }
        return result
    }

    private fun covers(rulePath: String, path: String): Boolean {
        val dir = rulePath.trimEnd('/')
        return rulePath == "." || path == dir || path.startsWith("$dir/")
    }

    /** Como Android: entra si alguna regla lo incluye y ninguna lo excluye. */
    private fun List<Rule>.backsUp(domain: String, path: String): Boolean =
        any { it.include && it.domain == domain && covers(it.path, path) } &&
            none { !it.include && it.domain == domain && covers(it.path, path) }

    /** Solo una lista de lo que se guarda (sin exclusiones): lo que se añada a `files/` no entra solo. */
    private fun checkSection(name: String, rules: List<Rule>) {
        assertWithMessage("$name: solo inclusiones").that(rules.filter { !it.include }).isEmpty()
        assertWithMessage("$name: lista de lo que se guarda").that(rules.map { it.domain to it.path }).containsExactly(
            "database" to "recetario.db",
            "database" to "recetario.db-wal",
            "file" to "datastore/ajustes.preferences_pb",
        )
        for ((domain, path) in listOf(
            "file" to "photos/0b9c-portada.jpg",
            "file" to "backups/Recetario-2026-11-15-0300.zip",
            "file" to "backups/antes-de-importar-2026-11-13-2140.zip",
            "file" to "drafts/new.json",
            // El estado del teléfono (archivo de Drive, fechas de las copias, avisos…) no viaja a otro teléfono.
            "file" to "datastore/estado-local.preferences_pb",
            "database" to "androidx.work.workdb",
            "root" to "cache/camera/foto.jpg",
        )) {
            assertWithMessage("$name: $domain/$path no se guarda").that(rules.backsUp(domain, path)).isFalse()
        }
    }

    @Test
    fun fullBackupContent_android11AndOlder() {
        val rules = rules(R.xml.backup_rules)
        assertThat(rules.map { it.section }.toSet()).containsExactly("full-backup-content")
        checkSection("backup_rules", rules)
    }

    @Test
    fun dataExtractionRules_cloudAndDeviceTransfer() {
        val rules = rules(R.xml.data_extraction_rules)
        assertThat(rules.map { it.section }.toSet()).containsExactly("cloud-backup", "device-transfer")
        checkSection("cloud-backup", rules.filter { it.section == "cloud-backup" })
        checkSection("device-transfer", rules.filter { it.section == "device-transfer" })
    }

    @Test
    fun manifest_usesTheRules() {
        val manifest = File(System.getProperty("user.dir"), "src/main/AndroidManifest.xml")
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val app = doc.getElementsByTagName("application").item(0) as org.w3c.dom.Element
        val android = "http://schemas.android.com/apk/res/android"
        assertThat(app.getAttributeNS(android, "allowBackup")).isEqualTo("true")
        assertThat(app.getAttributeNS(android, "fullBackupContent")).isEqualTo("@xml/backup_rules")
        assertThat(app.getAttributeNS(android, "dataExtractionRules")).isEqualTo("@xml/data_extraction_rules")
    }
}
