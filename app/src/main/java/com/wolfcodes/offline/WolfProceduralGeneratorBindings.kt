package com.wolfcodes.offline

/**
 * Closed, compile-time catalog. Registry data may authorize one of these exact
 * bindings; it can never instantiate a class or load code dynamically.
 *
 * Source fingerprints are pinned after the source files are committed.
 * Resource limits intentionally remain PENDING (zero) until real reviewed
 * measurements exist; tests supply separate, explicitly test-only limits.
 */
internal object WolfBuiltInGeneratorBindings {
    val ATMOSPHERE = WolfGeneratorBinding(
        assetId = "wolf_generator_atmosphere_fog",
        generatorVersion = "1.0.0",
        family = WolfGeneratorFamily.ATMOSPHERE,
        parameterSchemaId = "wolf.parameters.atmosphere.v1",
        parameterSchemaVersion = "1",
        outputKind = "ATMOSPHERE_LAYER",
        implementationBindingId = "wolf.binding.atmosphere.fog.v1",
        implementationVersion = "1.0.0",
        abiVersion = "WPGL-1",
        seedAlgorithmId = WolfSeedDerivation.ALGORITHM_ID,
        sourceRepositoryPath = "app/src/main/java/com/wolfcodes/offline/WolfAtmosphereProceduralGenerator.kt",
        sourceRevision = "c9be633bdd18596814b0baeb7895dd5fa137addc",
        sourceSha256 = "6401eb77c4e5738ef48d65870e8ffcb2b8edb6506097e51552146f79a2d46790",
        hardLimits = WolfGeneratorHardLimits(
            maxWorkingSetBytes = maxWorkingSetBytes = 0L,
            maxTemporaryStorageBytes = 0L,
            maxReusableOutputBytes = 0L,
            maxTransientElementsPerFrame = 0L,
            maxRetainedFrameCount = 0L,
            maxExecutionMillis = 0L
        )
    )

    val WEATHER = WolfGeneratorBinding(
        assetId = "wolf_generator_weather_rain",
        generatorVersion = "1.0.0",
        family = WolfGeneratorFamily.WEATHER,
        parameterSchemaId = "wolf.parameters.weather.v1",
        parameterSchemaVersion = "1",
        outputKind = "WEATHER_LAYER",
        implementationBindingId = "wolf.binding.weather.rain.v1",
        implementationVersion = "1.0.0",
        abiVersion = "WPGL-1",
        seedAlgorithmId = WolfSeedDerivation.ALGORITHM_ID,
        sourceRepositoryPath = "app/src/main/java/com/wolfcodes/offline/WolfWeatherProceduralGenerator.kt",
        sourceRevision = "0f0017fc2617e9f8c595bbd824aae1f1232fb500",
        sourceSha256 = "1b8e85a83a32f2d150bdb3dd95f2fdb0c5037bcf573672c7f93819704ebb67e2",
        hardLimits = WolfGeneratorHardLimits(
            maxWorkingSetBytes = maxWorkingSetBytes = 0L,
            maxTemporaryStorageBytes = 0L,
            maxReusableOutputBytes = 0L,
            maxTransientElementsPerFrame = 0L,
            maxRetainedFrameCount = 0L,
            maxExecutionMillis = 0L
        )
    )

    val LIGHTING = WolfGeneratorBinding(
        assetId = "wolf_generator_lighting_interplay",
        generatorVersion = "1.0.0",
        family = WolfGeneratorFamily.LIGHTING_EFFECT,
        parameterSchemaId = "wolf.parameters.lighting.v1",
        parameterSchemaVersion = "1",
        outputKind = "LIGHTING_LAYER",
        implementationBindingId = "wolf.binding.lighting.interplay.v1",
        implementationVersion = "1.0.0",
        abiVersion = "WPGL-1",
        seedAlgorithmId = WolfSeedDerivation.ALGORITHM_ID,
        sourceRepositoryPath = "app/src/main/java/com/wolfcodes/offline/WolfLightingProceduralGenerator.kt",
        sourceRevision = "ae394fc127fd8b00c02f756e6da1916aff40a73e",
        sourceSha256 = "83204f1d69e99d8be9af6c7b2ace539659d4b85ab143d7615a6c4c604c9ce6fa",
        hardLimits = WolfGeneratorHardLimits(
            maxWorkingSetBytes = maxWorkingSetBytes = 0L,
            maxTemporaryStorageBytes = 0L,
            maxReusableOutputBytes = 0L,
            maxTransientElementsPerFrame = 0L,
            maxRetainedFrameCount = 0L,
            maxExecutionMillis = 0L
        )
    )
}

/** All executable implementations are explicitly enumerated; no reflection is used. */
internal object WolfBuiltInGeneratorCatalog {
    val generators: List<WolfBuiltInProceduralGenerator> = listOf(
        WolfAtmosphereProceduralGenerator,
        WolfWeatherProceduralGenerator,
        WolfLightingProceduralGenerator
    )

    val bindings: List<WolfGeneratorBinding>
        get() = generators.map { it.binding }
}
