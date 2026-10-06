plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
    // Mapa de mosaicos. Sin clave de API y sin servicios de Google.
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // SGP4/SDP4 sobre Java: propagación orbital en el propio teléfono.
    // Ojo: el grupo es el del mantenedor del fork, no el del paquete original.
    implementation("com.github.davidmoten:predict4java:1.3.1")

    // ── Solo para desarrollo ───────────────────────────────────────────────
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
