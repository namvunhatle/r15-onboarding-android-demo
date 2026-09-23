// AGP 9 compiles Kotlin itself (built-in Kotlin) — no kotlin-android plugin. Compose needs the Kotlin compose compiler plugin.
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("com.android.library") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
