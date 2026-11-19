import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
//    id("com.google.gms.google-services")

    //KSP
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}


android {
    namespace = "com.terasumi.sellerkeyboard"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.terasumi.sellerkeyboard"
        minSdk = 26 // Android 8.0
        targetSdk = 36 // Android 14
        versionCode = 25
        versionName = "1.3.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        create("upgradeFixture") {
            initWith(getByName("debug"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("debug")
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
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
        compose = true
    }
    sourceSets {
        getByName("debug").assets.srcDir(rootProject.file("benchmark-fixtures"))
        getByName("upgradeFixture").apply {
            java.srcDir("src/debug/java")
            manifest.srcFile("src/debug/AndroidManifest.xml")
            assets.srcDir(rootProject.file("benchmark-fixtures"))
        }
        // The debug-signed, non-release benchmark APK keeps the shell/same-signature-guarded
        // evidence bridge so physical capture can continue after its attended install.
        getByName("benchmark").apply {
            java.srcDir("src/debug/java")
            assets.srcDir(rootProject.file("benchmark-fixtures"))
        }
        getByName("test").resources.srcDir(rootProject.file("benchmark-fixtures"))
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("upgradeFixture")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(24)
            output.versionName.set("1.3.4-upgrade-fixture")
        }
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler/reports")
    metricsDestination = layout.buildDirectory.dir("compose_compiler/metrics")
}

dependencies {
    // The September 2026 BOM selects UI 1.12.1, which requires compileSdk 37.
    // Share the SDK-36-compatible BOM between production and instrumentation.
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")

    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.annotation:annotation-jvm:1.11.0")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("dev.convex:android-convexmobile:0.8.0@aar") {
        isTransitive = true
    }

    // Serialization 1.9.x matches Kotlin 2.2; 1.12.x requires Kotlin 2.4.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
//    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("androidx.preference:preference-ktx:1.2.1")
    // Lifecycle 2.11 pulls Compose artifacts that require compileSdk 37.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    // Keep the system-trace API used by the existing Macrobenchmark evidence gates.
    implementation("androidx.tracing:tracing:1.3.0")
//    implementation("com.google.firebase:firebase-database:21.0.0")
//    implementation("com.google.firebase:firebase-firestore:25.0.0")
//    implementation("com.google.firebase:firebase-storage:21.0.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    //Glide for image loading
    // Glide 5.0.9 (and its image codecs) require compileSdk 37.
    implementation("com.github.bumptech.glide:glide:4.16.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Voice typing uses Android SpeechRecognizer directly.
//    implementation("com.google.ar:core:1.44.0") // Thay thế bằng phiên bản ARCore mới nhất nếu cần
    // Material Design 3
    implementation("androidx.compose.material3:material3:1.4.0")

    // Optional - Included automatically by material, only add when you need
    // the icons but not the material library (e.g. when using Material3 or a
    // custom design system based on Foundation)
    implementation("androidx.compose.material:material-icons-core")
    // Optional - Add full set of material icons
    implementation("androidx.compose.material:material-icons-extended")
    // Optional - Add window size utils
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.emoji2:emoji2-emojipicker:1.7.0")
    implementation("com.google.mlkit:translate:17.0.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    add("upgradeFixtureImplementation", "androidx.compose.ui:ui-test-manifest")
    //Navigation compose
    // Navigation 2.10.x requires compileSdk 37; keep the current SDK 36 contract.
    implementation("androidx.navigation:navigation-compose:2.9.7")
    //com.google.accompanist:accompanist-pager
    implementation("com.google.accompanist:accompanist-pager:0.36.0")
    //SQLite — using raw SQLiteOpenHelper (see database/DatabaseHelper.kt).
    // Room dependencies removed: project does not use Room annotations.
    // Re-add when migrating to Room: room-runtime, room-ktx, room-compiler (KSP).
    //Gson
    implementation("com.google.code.gson:gson:2.15.0")
    //exp4j
    implementation("net.objecthunter:exp4j:0.4.8")
    //relay
    //lottie
    implementation("com.airbnb.android:lottie-compose:6.7.1")
}

afterEvaluate {
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest")

    fun registerFocusedTestTask(
        name: String,
        taskDescription: String,
        testPattern: String
    ) = tasks.register<Test>(name) {
        group = "verification"
        description = taskDescription
        testClassesDirs = debugUnitTest.get().testClassesDirs
        classpath = debugUnitTest.get().classpath
        filter.includeTestsMatching(testPattern)
        testLogging.showStandardStreams = true
        outputs.upToDateWhen { false }
    }

    val candidateCorpusTask = registerFocusedTestTask(
        name = "candidateCorpusTest",
        taskDescription = "Runs the deterministic bilingual candidate and protected-token corpus gates.",
        testPattern = "*.CandidateCorpusEvaluationTest"
    )
    candidateCorpusTask.configure {
        filter.includeTestsMatching("*.AutoCorrectionUndoTrackerTest")
    }
    registerFocusedTestTask(
        name = "sellerWorkflowBenchmark",
        taskDescription = "Runs the deterministic 40-scenario seller interaction and safety gates.",
        testPattern = "*.SellerWorkflowBenchmarkTest"
    )
    registerFocusedTestTask(
        name = "keyDispatchPerformanceTest",
        taskDescription = "Measures the deterministic internal semantic key-dispatch p95 gate.",
        testPattern = "*.KeyDispatchPerformanceTest"
    )
}
