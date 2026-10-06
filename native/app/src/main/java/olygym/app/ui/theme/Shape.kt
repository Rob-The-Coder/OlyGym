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

/** --m3-shape-full: pills — chips, the segmented control, the stepper. */
internal val FullShape = RoundedCornerShape(percent = 50)
