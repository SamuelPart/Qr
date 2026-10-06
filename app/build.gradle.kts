import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Clave de CARTO con la que se compila por defecto.
 *
 * Es la del titular del proyecto, y viene puesta para que la app funcione sin
 * configurar nada: sin clave, CARTO devuelve cada mosaico con la marca de agua
 * «API KEY REQUIRED» encima.
 *
 * Se puede sustituir sin tocar el código. Orden de prioridad:
 *
 *   1. `carto.apiKey` en local.properties de la raíz
 *   2. `carto.apiKey` en ~/.gradle/gradle.properties
 *   3. esta constante
 *
 * Poner la propiedad vacía (`carto.apiKey=`) desactiva la clave y deja la app
 * compilando con la marca de agua, que es como estaba antes.
 *
 * Aviso, porque el repositorio es público: esta clave queda a la vista de
 * cualquiera que mire el repositorio o descompile el APK. Eso es inherente a
 * una clave de mapas —viaja en cada petición de mosaico— y la protección real
 * no es el secreto, sino la restricción por dominio del panel de CARTO.
 */
const val CLAVE_CARTO_POR_DEFECTO = "cb1_4bhs_1_fe78826369c026ab7150d80e"

/**
 * Resuelve la clave que se va a usar, y de dónde salió.
 *
 * Devuelve el valor ya limpio de espacios y saltos de línea: el correo de
 * CARTO parte la clave en dos con mucha facilidad.
 */
val claveResuelta: Pair<String, String> = run {
    val propiedades = Properties()
    val archivo = rootProject.file("local.properties")
    if (archivo.exists()) archivo.inputStream().use { propiedades.load(it) }

    val nombres = listOf("carto.apiKey", "carto.apikey", "carto.key", "CARTO_KEY", "cartoKey")
    val deLocal = nombres.firstNotNullOfOrNull { propiedades.getProperty(it) }
    val deGradle = providers.gradleProperty("carto.apiKey").orNull

    val crudo = deLocal ?: deGradle ?: CLAVE_CARTO_POR_DEFECTO
    val origen = when {
        deLocal != null -> "local.properties"
        deGradle != null -> "gradle.properties"
        else -> "la clave por defecto del proyecto"
    }

    // Un valor vacío en local.properties significa «sin clave a propósito».
    val forzadoSinClave = (deLocal != null || deGradle != null) && crudo.isBlank()
    val limpio = if (forzadoSinClave) "" else crudo.trim().replace(Regex("\\s+"), "")

    limpio to origen
}

val claveCarto: String = claveResuelta.first

// Deja constancia en la ventana Build de si la clave llegó y de dónde salió.
// Sin esto, un fallo de cableado y una clave inválida se ven igual: la marca
// de agua en el mapa.
if (claveCarto.isBlank()) {
    logger.lifecycle(
        "CARTO: se compila SIN CLAVE (indicado en local.properties). " +
            "Los fondos de mapa saldrán con la marca de agua «API KEY REQUIRED»."
    )
} else {
    logger.lifecycle(
        "CARTO: clave cargada desde ${claveResuelta.second} — ${claveCarto.length} " +
            "caracteres (${claveCarto.take(8)}…${claveCarto.takeLast(4)})"
    )
}

android {
    // El paquete de la app. Coincide con el de los archivos Kotlin.
    namespace = "com.samuelpart.ojodedios"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.samuelpart.ojodedios"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Llega al código como BuildConfig.CARTO_KEY. Se entrecomilla para
        // Java; una clave de CARTO solo lleva letras, dígitos, guiones y
        // guiones bajos, así que no puede romper la cadena generada.
        buildConfigField("String", "CARTO_KEY", "\"$claveCarto\"")
    }

    buildTypes {
        release {
            // Sin ofuscación: el APK es para uso propio, y si algo falla en
            // release, un rastreo de pila legible vale más que unos KB de menos.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Desde AGP 8, BuildConfig no se genera salvo que se pida. Sin esto,
        // BuildConfig.CARTO_KEY no existiría y no compilaría.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ── Compose ────────────────────────────────────────────────────────────
    // El BOM fija las versiones de todas las piezas de Compose entre sí, así
    // que las dependencias de abajo van sin número de versión a propósito.
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // ── Las dos únicas dependencias que no son de AndroidX ─────────────────
    // Mapa de mosaicos. La librería no pide cuenta ni servicios de Google;
    // el proveedor de mosaicos sí puede pedir clave (ver arriba, CARTO).
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // SGP4/SDP4 sobre Java: propagación orbital en el propio teléfono.
    // Ojo: el grupo es el del mantenedor del fork, no el del paquete original.
    implementation("com.github.davidmoten:predict4java:1.3.1")

    // ── Solo para desarrollo ───────────────────────────────────────────────
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
