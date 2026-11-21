package com.wolfcodes.offline

internal object WolfWeatherProceduralGenerator : WolfBuiltInProceduralGenerator {
    override val binding: WolfGeneratorBinding
        get() = WolfBuiltInGeneratorBindings.WEATHER

    override fun generate(context: WolfGeneratorExecutionContext): WolfProceduralEffectPayload {
        val atmosphere = context.previousEffects
            .mapNotNull { it.payload as? WolfAtmosphereLayer }
            .lastOrNull()
            ?: throw IllegalStateException("Weather generation requires an approved atmosphere output.")

        val variation = Math.floorMod(context.derivedSeed, 41L).toInt() - 20
        val rain = (context.request.parameters.rainStrengthPermille + variation)
            .coerceIn(0, 1000)
        val fogCoupling = (atmosphere.densityPermille / 8).coerceIn(0, 125)
        val slant = 180 + Math.floorMod(context.derivedSeed ushr 2, 101L).toInt()

        return WolfWeatherLayer(
            rainStrengthPermille = rain,
            fogCouplingPermille = fogCoupling,
            slantPermille = slant.coerceIn(180, 280)
        )
    }
}
