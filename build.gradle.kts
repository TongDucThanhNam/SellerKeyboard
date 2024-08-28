// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.5.2" apply false
    //Firebase
    id("com.google.gms.google-services") version "4.4.2" apply false
    //Kotlin
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false

    //KSP
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}