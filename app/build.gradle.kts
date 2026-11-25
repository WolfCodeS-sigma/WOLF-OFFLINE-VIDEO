plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wolfcodes.offline"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.wolfcodes.offline"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("junit:junit:4.13.2")
}

val verifyOfflineRuntime by tasks.registering {
    group = "verification"
    description = "Rejects network permissions, merged network permissions, common network APIs, endpoints, and unapproved direct runtime dependencies."

    val productionSource = layout.projectDirectory.dir("src/main")
    val manifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    val moduleBuildFile = layout.projectDirectory.file("build.gradle.kts")
    val mergedManifestDirectory = layout.buildDirectory.dir("intermediates/merged_manifest/debug")

    dependsOn("processDebugMainManifest")
    inputs.dir(productionSource)
    inputs.file(manifestFile)
    inputs.file(moduleBuildFile)
    outputs.upToDateWhen { false }

    doLast {
        val forbiddenManifestValues = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android:usesCleartextTraffic=\"true\"",
            "android:networkSecurityConfig"
        )

        val sourceManifestText = manifestFile.asFile.readText(Charsets.UTF_8)
        val sourceManifestViolations = forbiddenManifestValues.filter {
            sourceManifestText.contains(it, ignoreCase = true)
        }
        check(sourceManifestViolations.isEmpty()) {
            "Offline runtime source manifest violation(s): ${sourceManifestViolations.joinToString()}"
        }

        val mergedManifestRoot = mergedManifestDirectory.get().asFile
        val mergedManifests = if (mergedManifestRoot.exists()) {
            fileTree(mergedManifestRoot) { include("**/AndroidManifest.xml") }.files
        } else {
            emptySet()
        }
        check(mergedManifests.isNotEmpty()) {
            "The merged debug manifest was not found; the offline manifest contract cannot be verified."
        }

        val mergedManifestViolations = mergedManifests.flatMap { mergedManifest ->
            val text = mergedManifest.readText(Charsets.UTF_8)
            forbiddenManifestValues.filter { text.contains(it, ignoreCase = true) }.map {
                "${mergedManifest.relativeTo(projectDir)}: $it"
            }
        }
        check(mergedManifestViolations.isEmpty()) {
            "Offline runtime merged manifest violation(s): ${mergedManifestViolations.joinToString()}"
        }

        val forbiddenSourcePatterns = listOf(
            Regex("""\b(?:java|javax)\.net\."""),
            Regex("""\b(?:okhttp3|retrofit2|io\.ktor\.client|com\.android\.volley|com\.google\.firebase)\."""),
            Regex("""\bWebView\b"""),
            Regex("""\b(?:HttpURLConnection|HttpsURLConnection|Socket|ServerSocket|DownloadManager)\b"""),
            Regex("""android\.permission\.(?:INTERNET|ACCESS_NETWORK_STATE)\b""")
        )
        val endpointPattern = Regex("""["']https?://""")
        val productionFiles = fileTree(productionSource) {
            include("**/*.kt", "**/*.java", "**/*.xml")
        }.files
        val sourceViolations = productionFiles.flatMap { source ->
            source.readLines(Charsets.UTF_8).mapIndexedNotNull { index, line ->
                val networkApiFound = forbiddenSourcePatterns.any { it.containsMatchIn(line) }
                val endpointFound = source.extension in setOf("kt", "java") &&
                    endpointPattern.containsMatchIn(line)
                if (networkApiFound || endpointFound) {
                    "${source.relativeTo(projectDir)}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }
        }
        check(sourceViolations.isEmpty()) {
            "Offline runtime source violation(s):\n${sourceViolations.joinToString("\n")}"
        }

        val runtimeDependencyDeclarations = moduleBuildFile.asFile.readLines(Charsets.UTF_8)
            .filter { Regex("""^\s*(?!(?:test|androidTest|testFixtures))(?:implementation|api|runtimeOnly|[A-Za-z][A-Za-z0-9_]*(?:Implementation|Api|RuntimeOnly))\s*\(""").containsMatchIn(it) }
            .map { it.trim() }
        val allowedRuntimeDependencies = setOf(
            """implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")"""
        )
        val unsupportedDependencies = runtimeDependencyDeclarations.filterNot {
            it in allowedRuntimeDependencies
        }
        check(unsupportedDependencies.isEmpty()) {
            "Unexpected direct runtime dependency declaration(s): ${unsupportedDependencies.joinToString()}"
        }

        logger.lifecycle(
            "Offline runtime contract passed: source and merged manifests contain no network permission, " +
                "production sources contain no detected common network API or hard-coded HTTP(S) endpoint, " +
                "and direct runtime dependencies match the approved local runtime."
        )
    }
}
