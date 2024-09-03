plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
//    id("com.google.gms.google-services")

    //KSP
    id("com.google.devtools.ksp")
}


android {
    namespace = "com.terasumi.sellerkeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.terasumi.sellerkeyboard"
        minSdk = 25 // Android 7.1
        targetSdk = 34 // Android 14
        versionCode = 17
        versionName = "1.2.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Use the appropriate version
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
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    implementation("androidx.navigation:navigation-runtime-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
//    implementation("com.google.firebase:firebase-database:21.0.0")
//    implementation("com.google.firebase:firebase-firestore:25.0.0")
//    implementation("com.google.firebase:firebase-storage:21.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    //Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    //google voice input
    implementation(files("libs/voiceimeutils.jar"))
//    implementation("com.google.ar:core:1.44.0") // Thay thế bằng phiên bản ARCore mới nhất nếu cần
    // Material Design 3
    implementation("androidx.compose.material3:material3:1.2.1")

    // Optional - Included automatically by material, only add when you need
    // the icons but not the material library (e.g. when using Material3 or a
    // custom design system based on Foundation)
    implementation("androidx.compose.material:material-icons-core")
    // Optional - Add full set of material icons
    implementation("androidx.compose.material:material-icons-extended")
    // Optional - Add window size utils
    implementation("androidx.compose.material3:material3-window-size-class")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    //Navigation compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    //com.google.accompanist:accompanist-pager
    implementation("com.google.accompanist:accompanist-pager:0.20.0")

    //SQLite
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    ksp("androidx.room:room-compiler:2.6.1")
    //Gson
    implementation("com.google.code.gson:gson:2.11.0")

    //exp4j
    implementation("net.objecthunter:exp4j:0.4.8")

    //relay

    //lottie
    implementation("com.airbnb.android:lottie-compose:5.2.0")
}