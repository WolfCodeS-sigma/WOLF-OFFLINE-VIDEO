package com.wolfcodes.offline

internal object WolfAtmosphereProceduralGenerator : WolfBuiltInProceduralGenerator {
    override val binding: WolfGeneratorBinding
        get() = WolfBuiltInGeneratorBindings.ATMOSPHERE

    override fun generate(context: WolfGeneratorExecutionContext): WolfProceduralEffectPayload {
        val variation = Math.floorMod(context.derivedSeed, 61L).toInt() - 30
        val density = (context.request.parameters.fogStrengthPermille + variation)
            .coerceIn(0, 1000)
        val drift = 20 + Math.floorMod(context.derivedSeed ushr 1, 81L).toInt()
        return WolfAtmosphereLayer(
            densityPermille = density,
            horizontalDriftPermille = drift.coerceIn(0, 100)
        )
    }
}
