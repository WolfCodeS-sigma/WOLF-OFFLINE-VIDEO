import korlibs.korge.Korge
import korlibs.korge.view.position
import korlibs.korge.view.solidRect
import korlibs.image.color.Colors
import korlibs.math.geom.Size

/**
 * Minimal isolated KorGE Android target smoke scene.
 * This is a compatibility fixture, not a WOLF production scene or asset.
 */
suspend fun main() = Korge(
    windowSize = Size(160, 160),
    backgroundColor = Colors["#101010"]
) {
    solidRect(80, 80, Colors["#8b0000"]) {
        position(40, 40)
    }
}
