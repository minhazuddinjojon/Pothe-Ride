package com.potheride.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The depth system.
 *
 * The wireframes are flat boards, but the brief asks for a modern three-dimensional
 * feel. Rather than inventing decoration, depth here encodes *hierarchy*: how far a
 * surface floats tells you how much it wants your attention, and it is the only thing
 * shadow is allowed to communicate. Four steps, no more — a shadow scale with ten
 * values is a shadow scale nobody can use consistently.
 *
 * Each level pairs a tight, dark "contact" shadow with a wide, soft "ambient" one.
 * A single shadow at these radii reads as a grey smear; the pair is what makes a card
 * look like it is resting above the page rather than printed on it.
 */
enum class Depth(
    val contactElevation: Dp,
    val ambientElevation: Dp
) {
    /** Flush with the page. Dividers and inline rows. */
    FLAT(0.dp, 0.dp),

    /** Resting cards: trip rows, fare summaries, document status tiles. */
    RESTING(1.dp, 4.dp),

    /** Things that float over content: the map's bottom sheet, the home hero card. */
    FLOATING(3.dp, 12.dp),

    /** Modal weight: dialogs, the expanded request sheet a driver must answer. */
    LIFTED(6.dp, 24.dp);
}

/**
 * Applies a [Depth] as a two-layer shadow.
 *
 * Compose's `shadow` clips to the shape, so this is applied as two chained modifiers
 * rather than one: the wide ambient pass first, then the tight contact pass on top.
 */
fun Modifier.depth(
    level: Depth,
    shape: Shape = RoundedCornerShape(16.dp),
    ambientTint: Color = Color(0x14000000),
    contactTint: Color = Color(0x1F000000)
): Modifier {
    if (level == Depth.FLAT) return this
    return this
        .shadow(
            elevation = level.ambientElevation,
            shape = shape,
            clip = false,
            ambientColor = ambientTint,
            spotColor = ambientTint
        )
        .shadow(
            elevation = level.contactElevation,
            shape = shape,
            clip = false,
            ambientColor = contactTint,
            spotColor = contactTint
        )
}

/**
 * Press feedback: the surface dips slightly toward the page.
 *
 * Scale, not colour. A colour flash on a blue button against a white card is hard to
 * see in daylight, which is the condition this app is actually used in; a physical
 * dip reads at a glance and costs nothing in contrast.
 */
fun Modifier.pressDepth(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressDepth"
    )
    scale(scale)
}
