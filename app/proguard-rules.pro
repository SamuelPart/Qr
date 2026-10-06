# Reglas de ofuscación. Ahora mismo la compilación de release no ofusca
# (isMinifyEnabled = false), así que este archivo está vacío a propósito: solo
# existe para que la referencia de build.gradle.kts no quede rota.
#
# Si algún día activas la ofuscación, estas reglas conservan lo que osmdroid y
# predict4java esperan encontrar en tiempo de ejecución:
#
# -keep class org.osmdroid.** { *; }
# -keep class com.github.amsacode.predict4java.** { *; }
# -dontwarn org.osmdroid.**
