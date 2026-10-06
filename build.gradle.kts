// Plugins a nivel de proyecto. No se aplican aquí, solo se declaran: cada
// módulo decide cuáles usa. El módulo es uno solo, `app`.
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // Desde Kotlin 2.0 el compilador de Compose es un plugin aparte. Sin esta
    // línea, las funciones @Composable no compilan.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
