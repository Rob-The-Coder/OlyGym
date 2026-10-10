package olygym.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** --m3-shape-xs|s|m|l|xl from frontend/src/m3.tokens.css. */
internal val OlyGymShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** --m3-shape-full: pills — every button, the segmented control, the stepper. */
internal val FullShape = RoundedCornerShape(percent = 50)

/**
 * What a card, a tile and a grouped section are drawn in.
 *
 * 20, not the 14 the port started with: m3.components.css draws .card, .sect-b and .tile at 20px,
 * and --r-card (16) is only what the complex card still asks for. A card is the app's largest
 * recurring surface, so 6dp of radius across every screen is the difference between looking like
 * the React app and looking like a port of it.
 */
internal val CardShape = RoundedCornerShape(20.dp)

/** --r-xl, top corners only: the bottom sheet, at the 28 the web's .sheet uses. */
internal val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/** The bottom bar's Start control: M3 gives a FAB a 16dp corner, not a circle. */
internal val FabShape = RoundedCornerShape(16.dp)
