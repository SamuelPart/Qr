import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Clave de CARTO, si la hay.
 *
 * No se escribe en el repositorio a propósito: es público, y una clave ahí la
 * puede copiar cualquiera para gastar tu cuota. Vive en `local.properties`,
 * que Android Studio ya crea y que está en .gitignore.
 *
 *   carto.apiKey=cb1_...
 *
 * Si no está, la compilación sigue funcionando: los mapas de CARTO se ven con
 * la marca de agua «API KEY REQUIRED», que es el comportamiento documentado
 * de CARTO cuando la petición va sin clave.
 */
val claveCarto: String = run {
    val archivo = rootProject.file("local.properties")
    if (!archivo.exists()) return@run ""
    val propiedades = Properties()
    archivo.inputStream().use { propiedades.load(it) }
    // Se quitan los espacios por si el correo partió la clave en dos líneas.
    propiedades.getProperty("carto.apiKey").orEmpty().replace(Regex("\\s+"), "")
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

        // Llega al código como BuildConfig.CARTO_KEY.
        buildConfigField("String", "CARTO_KEY", "\"${claveCarto.replace("\"", "\\\"")}\"")
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
