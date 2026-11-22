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
        // Godot uses hidden project asset directories when present; retain them in this POC APK.
        aaptOptions {
            ignoreAssetsPattern = "!.svn:!.git:!.gitignore:!.ds_store:!*.scc:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
        }
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
val libgdxNativeArtifacts by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val libgdxNativeAbis = mapOf(
    "arm64-v8a" to "natives-arm64-v8a",
    "armeabi-v7a" to "natives-armeabi-v7a",
    "x86" to "natives-x86",
    "x86_64" to "natives-x86_64"
)

dependencies {
    add("godotImplementation", "org.godotengine:godot:4.7.2.stable")
    add("godotImplementation", "androidx.fragment:fragment:1.9.1")
    add("godotImplementation", "androidx.core:core-splashscreen:1.2.0")

    // Filament 1.75.0 is the newest checked stable line in this POC that
    // remains on compileSdk 34; newer checked tags require API 37.
    add("filamentImplementation", "com.google.android.filament:filament-android:1.75.0")

    add("libgdxImplementation", "com.badlogicgames.gdx:gdx:1.14.2")
    add("libgdxImplementation", "com.badlogicgames.gdx:gdx-backend-android:1.14.2")
    libgdxNativeAbis.values.forEach { classifier ->
        add("libgdxNativeArtifacts", "com.badlogicgames.gdx:gdx-platform:1.14.2:$classifier")
    }

    add("lottieImplementation", "com.airbnb.android:lottie:6.7.1")

    // SceneView is resolved independently as a Filament-based UI facade,
    // not counted as another rendering engine or packaged in the production app.
    sceneViewPoc("io.github.sceneview:sceneview:4.70.0")

    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

// libGDX's Android backend loads libgdx.so at runtime. The Maven native
// classifiers are ZIP/JAR archives, so unpack each ABI into this POC flavor's
// generated jniLibs tree before AGP merges native libraries into its APK.
android.sourceSets.getByName("libgdx").jniLibs.srcDir(
    layout.buildDirectory.dir("generated/libgdx/jniLibs")
)

val extractLibgdxNativeTasks = libgdxNativeAbis.map { (abi, classifier) ->
    tasks.register<Copy>("extractLibgdxNative${abi.replace("-", "").replace("_", "")}") {
        from(providers.provider {
            zipTree(libgdxNativeArtifacts.files.single { it.name.endsWith("$classifier.jar") })
        }) {
            include("*.so")
        }
        into(layout.buildDirectory.dir("generated/libgdx/jniLibs/$abi"))
    }
}

tasks.configureEach {
    if (name.startsWith("mergeLibgdx") &&
        (name.endsWith("NativeLibs") || name.endsWith("JniLibFolders"))
    ) {
        dependsOn(extractLibgdxNativeTasks)
    }
}
