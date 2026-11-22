plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wolfcodes.enginepoc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wolfcodes.enginepoc"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-poc"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "engine"
    productFlavors {
        create("godot") { dimension = "engine" }
        create("filament") { dimension = "engine" }
        create("libgdx") { dimension = "engine" }
        create("lottie") { dimension = "engine" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        animationsDisabled = true
    }
}

val sceneViewPoc by configurations.creating

dependencies {
    add("godotImplementation", "org.godotengine:godot:4.7.2.stable")
    add("godotImplementation", "androidx.fragment:fragment:1.9.1")
    add("godotImplementation", "androidx.core:core-splashscreen:1.2.0")

    add("filamentImplementation", "com.google.android.filament:filament-android:1.81.0")

    add("libgdxImplementation", "com.badlogicgames.gdx:gdx:1.14.2")
    add("libgdxImplementation", "com.badlogicgames.gdx:gdx-backend-android:1.14.2")

    add("lottieImplementation", "com.airbnb.android:lottie:6.7.1")

    // SceneView is resolved independently as a Filament-based UI facade,
    // not counted as another rendering engine or packaged in the production app.
    sceneViewPoc("io.github.sceneview:sceneview:4.70.0")

    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
