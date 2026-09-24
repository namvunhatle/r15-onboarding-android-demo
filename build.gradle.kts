// AGP 9 compiles Kotlin itself (built-in Kotlin) — no kotlin-android plugin is applied.
// Pin KGP to 2.4.20, the compiler main builds with; AGP alone would fall back to its bundled 2.2.10.
buildscript {
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") }
}
plugins {
    id("com.android.application") version "9.4.1" apply false
}
