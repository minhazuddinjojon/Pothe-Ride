package com.potheride.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.potheride.app.core.format.AppLanguage
import com.potheride.app.core.format.Formatters
import com.potheride.app.core.i18n.Strings
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.RideState
import com.potheride.app.data.model.DriverSummary
import com.potheride.app.ui.theme.AlertRed
import com.potheride.app.ui.theme.InfoBlue
import com.potheride.app.ui.theme.InfoBlueSoft
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.ui.theme.RouteGreenSoft
import com.potheride.app.ui.theme.SignalAmber
import com.potheride.app.ui.theme.SignalAmberSoft
import com.potheride.app.ui.theme.Snow

/** Localised label for a ride state. */
fun rideStateLabel(state: RideState, strings: Strings): String = when (state) {
    RideState.REQUESTED -> strings.statusRequested
    RideState.ACCEPTED -> strings.statusAccepted
    RideState.DRIVER_ARRIVING -> strings.statusDriverArriving
    RideState.PICKED_UP -> strings.statusPickedUp
    RideState.COMPLETED -> strings.statusCompleted
    RideState.PAID -> strings.statusPaid
    RideState.DECLINED -> strings.statusDeclined
    RideState.CANCELLED -> strings.statusCancelled
}

fun vehicleLabel(type: VehicleClass, language: AppLanguage): String =
    if (language == AppLanguage.BANGLA) type.displayBn else type.displayEn

/**
 * The Requested -> Paid progress tracker. Only the happy path is drawn; a declined
 * or cancelled ride gets its own terminal treatment instead of being wedged into a
 * timeline it never travelled.
 */
@Composable
fun RideProgressTracker(
    current: RideState,
    strings: Strings,
    modifier: Modifier = Modifier
) {
    if (!current.isOnHappyPath) {
        val isCancelled = current == RideState.CANCELLED || current == RideState.DECLINED
        Box(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isCancelled) MaterialTheme.colorScheme.errorContainer else RouteGreenSoft)
                .padding(16.dp)
        ) {
            Text(
                rideStateLabel(current, strings),
                style = MaterialTheme.typography.titleMedium,
                color = if (isCancelled) AlertRed else RouteGreen
            )
        }
        return
    }

    val steps = RideState.HAPPY_PATH
    val currentIndex = steps.indexOf(current).coerceAtLeast(0)

    Column(modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            val done = index < currentIndex
            val isCurrent = index == currentIndex
            Row(verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    done -> RouteGreen
                                    isCurrent -> SignalAmber
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Snow,
                                modifier = Modifier.size(15.dp)
                            )
                        } else if (isCurrent) {
                            Box(
                                Modifier.size(9.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onTertiary)
                            )
                        }
                    }
                    if (index != steps.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(26.dp)
                                .background(
                                    if (done) RouteGreen else MaterialTheme.colorScheme.outline
                                )
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    rideStateLabel(step, strings),
                    style = if (isCurrent) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.bodyMedium,
                    color = when {
                        done -> MaterialTheme.colorScheme.onSurface
                        isCurrent -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
    }
}

/** Read-only or interactive star row. */
@Composable
fun StarRating(
    stars: Int,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null,
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..5) {
            val filled = i <= stars
            Icon(
                imageVector = if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (filled) SignalAmber else MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(size)
                    .then(
                        if (onSelect != null) Modifier.clickable { onSelect(i) } else Modifier
                    )
            )
        }
    }
}

/**
 * Driver identity block: who they are, whether they're verified, what they drive.
 * Shown before booking, because deciding to get into a stranger's car on the
 * strength of a fare figure alone is not a decision anyone should be asked to make.
 */
@Composable
fun DriverRow(
    driver: DriverSummary,
    strings: Strings,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InitialsAvatar(driver.initials)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    driver.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                if (driver.verified) {
                    Pill(strings.verified, RouteGreenSoft, RouteGreen)
                } else {
                    Pill(strings.unverified, SignalAmberSoft, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    if (driver.rating != null) {
                        append("★ ${Formatters.rating(driver.rating, language)}")
                        append(" · ")
                        append("${Formatters.localizeDigits(driver.totalTrips.toString(), language)} ${strings.tripsCompleted}")
                    } else {
                        append(strings.newDriver)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${vehicleLabel(driver.vehicleType, language)} · ${driver.vehiclePlate}" +
                    (driver.vehicleModel?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Pickup-to-drop-off pair with a connecting thread — the visual shorthand the whole
 * app uses for "a journey", so a route reads the same on every screen.
 */
@Composable
fun JourneyRow(
    from: String,
    to: String,
    modifier: Modifier = Modifier,
    fromColor: Color = RouteGreen,
    toColor: Color = AlertRed
) {
    Row(modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 5.dp)
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(fromColor))
            Box(
                Modifier.width(2.dp).height(22.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Box(Modifier.size(9.dp).clip(CircleShape).background(toColor))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                from,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            Text(
                to,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Route-overlap badge. Colour-coded because the number carries real meaning: a high
 * overlap is a cheaper ride for the passenger and almost no imposition on the driver.
 */
@Composable
fun OverlapBadge(percent: Int, language: AppLanguage, label: String, modifier: Modifier = Modifier) {
    val (bg, fg) = when {
        percent >= 75 -> RouteGreenSoft to RouteGreen
        percent >= 45 -> InfoBlueSoft to InfoBlue
        else -> SignalAmberSoft to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Pill(
        text = "${Formatters.localizeDigits(percent.toString(), language)}% $label",
        background = bg,
        contentColor = fg,
        modifier = modifier
    )
}

/** Simple determinate bar; avoids the Material3 progress API that changed shape in 1.6. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    barColor: Color = RouteGreen
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(barColor)
        )
    }
}
