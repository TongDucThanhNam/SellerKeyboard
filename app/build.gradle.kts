plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
//    id("com.google.gms.google-services")
}


android {
    namespace = "com.terasumi.sellerkeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.terasumi.sellerkeyboard"
        minSdk = 24 // Android 7.0
        targetSdk = 34 // Android 14
        versionCode = 5
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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

    //
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.ar.sceneform:filament-android:1.17.1")
    implementation("com.google.ar.sceneform:rendering:1.17.1")
    implementation("androidx.annotation:annotation-jvm:1.8.2")
//    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("androidx.navigation:navigation-runtime:2.7.7")
    implementation("androidx.navigation:navigation-ui:2.7.7")
    implementation("androidx.navigation:navigation-fragment:2.7.7")
//    implementation("com.google.firebase:firebase-database:21.0.0")
//    implementation("com.google.firebase:firebase-firestore:25.0.0")
//    implementation("com.google.firebase:firebase-storage:21.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    //Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    //google voice input
    implementation(files("libs/voiceimeutils.jar"))
//    implementation("com.google.ar:core:1.44.0") // Thay thế bằng phiên bản ARCore mới nhất nếu cần
}