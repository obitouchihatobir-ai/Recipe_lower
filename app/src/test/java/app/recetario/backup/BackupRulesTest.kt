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
 * Copia de seguridad de Android (§8.5): solo la base de datos y los ajustes, sin fotos (no caben en sus 25 MB),
 * sin las copias del teléfono, sin borradores y sin la caché; igual en la nube y al pasar a otro teléfono.
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

    private fun checkSection(name: String, rules: List<Rule>) {
        assertWithMessage("$name: hay reglas").that(rules).isNotEmpty()
        for ((domain, path) in listOf("database" to "recetario.db", "database" to "recetario.db-wal", "file" to "datastore/ajustes.preferences_pb")) {
            assertWithMessage("$name: $domain/$path se guarda").that(rules.backsUp(domain, path)).isTrue()
        }
        for ((domain, path) in listOf(
            "file" to "photos/0b9c-portada.jpg",
            "file" to "backups/Recetario-2026-11-15-0300.zip",
            "file" to "backups/antes-de-importar-2026-11-13-2140.zip",
            "file" to "drafts/new.json",
            "database" to "androidx.work.workdb",
            "root" to "cache/camera/foto.jpg",
        )) {
            assertWithMessage("$name: $domain/$path no se guarda").that(rules.backsUp(domain, path)).isFalse()
        }
        val excluded = rules.filter { !it.include }.map { it.domain to it.path }
        assertWithMessage("$name: exclusiones explícitas").that(excluded)
            .containsAtLeast("file" to "photos/", "file" to "backups/", "file" to "drafts/")
        // Lint (FullBackupContent, fatal al compilar la versión release) exige que cada exclusión esté dentro de una inclusión.
        for (exclude in rules.filter { !it.include }) {
            assertWithMessage("$name: ${exclude.path} está dentro de algo incluido")
                .that(rules.any { it.include && it.domain == exclude.domain && covers(it.path, exclude.path) }).isTrue()
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
