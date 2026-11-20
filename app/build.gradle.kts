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
}

val verifyOfflineRuntime by tasks.registering {
    group = "verification"
    description = "Rejects Android runtime permissions, APIs, endpoints, and direct dependencies that require network access."

    val productionSource = layout.projectDirectory.dir("src/main")
    val manifestFile = layout.projectDirectory.file("src/main/AndroidManifest.xml")
    val moduleBuildFile = layout.projectDirectory.file("build.gradle.kts")

    inputs.dir(productionSource)
    inputs.file(manifestFile)
    inputs.file(moduleBuildFile)

    doLast {
        val manifestText = manifestFile.asFile.readText(Charsets.UTF_8)
        val forbiddenManifestValues = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android:usesCleartextTraffic=\"true\"",
            "android:networkSecurityConfig"
        )
        val manifestViolations = forbiddenManifestValues.filter { manifestText.contains(it, ignoreCase = true) }
        check(manifestViolations.isEmpty()) {
            "Offline runtime manifest violation(s): ${manifestViolations.joinToString()}"
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
            .filter { Regex("""^\s*(implementation|api|runtimeOnly)\s*\(""").containsMatchIn(it) }
            .map { it.trim() }
        val allowedRuntimeDependencies = setOf(
            """implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")"""
        )
        val unsupportedDependencies = runtimeDependencyDeclarations.filterNot { it in allowedRuntimeDependencies }
        check(unsupportedDependencies.isEmpty()) {
            "Unexpected direct runtime dependency declaration(s): ${unsupportedDependencies.joinToString()}"
        }

        logger.lifecycle(
            "Offline runtime contract passed: no network permission, network API, hard-coded HTTP(S) endpoint, " +
                "or unapproved direct runtime dependency was found in production sources."
        )
    }
}
