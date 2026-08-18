package com.potheride.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.potheride.app.ui.theme.AlertRed
import com.potheride.app.ui.theme.Depth
import com.potheride.app.ui.theme.EyebrowStyle
import com.potheride.app.ui.theme.LocalCtaColor
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.ui.theme.SignalAmber
import com.potheride.app.ui.theme.depth
import com.potheride.app.ui.theme.metaTextStyle
import com.potheride.app.ui.theme.pressDepth

/**
 * Components transcribed directly from the wireframe boards, with the depth system
 * layered on. Anything already served well by [PotheCard] or [PrimaryButton] stays
 * there; this file only holds shapes the boards introduce that the app did not have.
 */

/** The small caps label that opens each field group — `MOBILE NUMBER`, `PAY WITH`. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = EyebrowStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

/** The monospace metadata line — `CNG · 2 seats left · leaves 9:50am`. */
@Composable
fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 2
) {
    Text(text, style = metaTextStyle(), color = color, modifier = modifier, maxLines = maxLines)
}

/**
 * The forward action: a full-width blue pill.
 *
 * Distinct from [PrimaryButton], which paints itself in `colorScheme.primary`. Screens
 * that follow the wireframes should reach for this one; see [LocalCtaColor].
 */
@Composable
fun CtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressDepth(interaction)
            .depth(if (enabled) Depth.RESTING else Depth.FLAT, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LocalCtaColor.current,
            contentColor = Color.White,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** The red-outlined action — `SOS`, `Decline`. Never filled: it must not invite a tap. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().height(54.dp).pressDepth(interaction),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** What a verification or ride step is currently doing. */
enum class BadgeTone { PENDING, POSITIVE, NEGATIVE, NEUTRAL }

/**
 * The outlined status pill — amber `Pending review`, green `Approved` / `92% overlap`.
 *
 * Outlined rather than filled so a row of three of them does not turn a white card
 * into a traffic light. The colour carries the meaning; the fill stays out of the way.
 */
@Composable
fun StatusBadge(
    text: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier
) {
    val colour = when (tone) {
        BadgeTone.PENDING -> SignalAmber
        BadgeTone.POSITIVE -> RouteGreen
        BadgeTone.NEGATIVE -> AlertRed
        BadgeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, colour, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = colour,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * The four-box OTP entry from wireframe 01.
 *
 * Display only — it renders [code] and reports taps. Keeping the text field out of it
 * means one hidden field owns the input and the IME behaves; four real fields fighting
 * over focus is the classic way this component goes wrong.
 */
@Composable
fun OtpBoxes(
    code: String,
    length: Int = 4,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
    ) {
        repeat(length) { index ->
            val filled = index < code.length
            val focused = index == code.length
            val borderWidth by animateFloatAsState(
                targetValue = if (focused) 2f else 1f,
                animationSpec = tween(150),
                label = "otpBorder"
            )
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        borderWidth.dp,
                        if (focused) LocalCtaColor.current else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (filled) code[index].toString() else "·",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (filled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * The `−  3  +` seat stepper from wireframe 08.
 *
 * The buttons disable at the bounds rather than silently ignoring the tap, so a driver
 * who has hit the vehicle's capacity can see *why* nothing is happening.
 */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 8
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton("−", enabled = value > min) { onValueChange(value - 1) }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        StepperButton("+", enabled = value < max) { onValueChange(value + 1) }
    }
}

@Composable
private fun StepperButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

/**
 * A document upload slot — a label, its state, and a circular `+`.
 *
 * Shows the filename once something is attached rather than a generic tick, because
 * "which of the two NID sides did I already upload?" is the question this row exists
 * to answer.
 */
@Composable
fun UploadRow(
    label: String,
    attachedName: String?,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onPick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (attachedName == null) label else label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (attachedName == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (attachedName != null) {
                MetaText(attachedName, maxLines = 1, color = RouteGreen)
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The dashed rule that separates the fare line items from the total on the booking
 * confirm board. Drawn rather than composed from characters so it stretches cleanly
 * to any width.
 */
@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    val colour = MaterialTheme.colorScheme.outline
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = colour,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }
    )
}

/**
 * The black hero card from wireframe board 01B — the one thing the home screen wants
 * you to do.
 *
 * Filled near-black rather than the blue CTA colour on purpose: it is a *destination*,
 * not the confirming action of a form. Reserving blue for "this commits something"
 * keeps the two readable apart on a screen that shows both.
 */
@Composable
fun HeroCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .pressDepth(interaction)
            .depth(Depth.FLOATING, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        MetaText(
            subtitle,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            maxLines = 1
        )
    }
}

/**
 * A card that floats. The elevated sibling of [PotheCard], for the home hero, the map
 * bottom sheet, and anything else the depth scale says should sit above the page.
 */
@Composable
fun DepthCard(
    modifier: Modifier = Modifier,
    level: Depth = Depth.RESTING,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressDepth(interaction) else Modifier)
            .depth(level, shape)
            .clip(shape)
            .background(containerColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else Modifier
            )
            .padding(contentPadding),
        content = content
    )
}
