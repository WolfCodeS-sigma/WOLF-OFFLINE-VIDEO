package com.wolfcodes.offline

internal object WolfLightingProceduralGenerator : WolfBuiltInProceduralGenerator {
    override val binding: WolfGeneratorBinding
        get() = WolfBuiltInGeneratorBindings.LIGHTING

    override fun generate(context: WolfGeneratorExecutionContext): WolfProceduralEffectPayload {
        val atmosphere = context.previousEffects
            .mapNotNull { it.payload as? WolfAtmosphereLayer }
            .lastOrNull()
            ?: throw IllegalStateException("Lighting generation requires an atmosphere output.")
        val weather = context.previousEffects
            .mapNotNull { it.payload as? WolfWeatherLayer }
            .lastOrNull()
            ?: throw IllegalStateException("Lighting generation requires a weather output.")

        val reduction = atmosphere.densityPermille / 8 +
            weather.rainStrengthPermille / 10
        val exposure = (1000 - reduction + context.request.parameters.lightingBiasPermille / 10)
            .coerceIn(500, 1000)

        return WolfLightingLayer(
            exposurePermille = exposure,
            fogScatteringPermille = (atmosphere.densityPermille / 2).coerceIn(0, 500),
            wetReflectionPermille = (weather.rainStrengthPermille / 3).coerceIn(0, 333)
        )
    }
}
