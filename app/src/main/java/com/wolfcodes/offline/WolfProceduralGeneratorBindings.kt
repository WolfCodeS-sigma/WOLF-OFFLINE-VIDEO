package com.wolfcodes.offline

/**
 * Closed, compile-time catalog. Registry data may authorize one of these exact
 * bindings; it can never instantiate a class or load code dynamically.
 *
 * Source fingerprints are pinned after the source files are committed.
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
        sourceRevision = "0000000000000000000000000000000000000000",
        sourceSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        hardLimits = WolfGeneratorHardLimits(
            maxWorkingSetBytes = 131072L,
            maxTemporaryStorageBytes = 65536L,
            maxReusableOutputBytes = 16384L,
            maxTransientElementsPerFrame = 64L,
            maxRetainedFrameCount = 1L,
            maxExecutionMillis = 250L
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
        sourceRevision = "0000000000000000000000000000000000000000",
        sourceSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        hardLimits = WolfGeneratorHardLimits(
            maxWorkingSetBytes = 131072L,
            maxTemporaryStorageBytes = 65536L,
            maxReusableOutputBytes = 16384L,
            maxTransientElementsPerFrame = 64L,
            maxRetainedFrameCount = 1L,
            maxExecutionMillis = 250L
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
        sourceRevision = "0000000000000000000000000000000000000000",
        sourceSha256 = "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
        hardLimits = WolfGeneratorHardLimits(
            maxWorkingSetBytes = 131072L,
            maxTemporaryStorageBytes = 65536L,
            maxReusableOutputBytes = 16384L,
            maxTransientElementsPerFrame = 64L,
            maxRetainedFrameCount = 1L,
            maxExecutionMillis = 250L
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
