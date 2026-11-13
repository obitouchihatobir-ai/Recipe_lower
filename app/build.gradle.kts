import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// AGP 9 lleva Kotlin integrado: no se aplica org.jetbrains.kotlin.android.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// La versión se cambia solo en app/version.properties (versionName=X.Y.Z).
// versionCode = major*10000 + minor*100 + patch, así crece con cada versión.
val appVersionName: String = Properties()
    .apply { file("version.properties").inputStream().use { load(it) } }
    .getProperty("versionName")
    ?: error("Falta versionName en app/version.properties")
val appVersionCode: Int = run {
    val match = Regex("""(\d+)\.(\d{1,2})\.(\d{1,2})""").matchEntire(appVersionName)
        ?: error("versionName debe ser X.Y.Z, con Y y Z entre 0 y 99: «$appVersionName»")
    val (major, minor, patch) = match.destructured
    major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
}

// Llave de firma descifrada por scripts/signing/decrypt-keystore.sh (solo en
// el flujo release.yml). Sin ella, assembleRelease genera un APK sin firmar.
val releaseKeystore: String? = providers.environmentVariable("RECETARIO_KEYSTORE").orNull

android {
    namespace = "app.recetario"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.recetario"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        releaseKeystore?.let { keystorePath ->
            create("release") {
                val passphrase = providers.environmentVariable("SIGNING_PASSPHRASE").orNull
                    ?: error("RECETARIO_KEYSTORE está definida pero falta SIGNING_PASSPHRASE")
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = passphrase
                keyAlias = "recetario"
                keyPassword = passphrase
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Etapa 1: sin minificar, para no tener fallos que solo salgan en release.
            isMinifyEnabled = false
            // null sin RECETARIO_KEYSTORE: el APK release sale sin firmar.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation("app.recetario:logic")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.reorderable)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
