package com.potheride.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.potheride.app.core.geo.GeoUtils
import com.potheride.app.core.geo.LatLng
import com.potheride.app.ui.theme.AlertRed
import com.potheride.app.ui.theme.Cloud
import com.potheride.app.ui.theme.Ink
import com.potheride.app.ui.theme.Line
import com.potheride.app.ui.theme.Mist
import com.potheride.app.ui.theme.RouteGreen
import com.potheride.app.ui.theme.SignalAmber
import com.potheride.app.ui.theme.Snow

/**
 * Renders a trip on a coordinate-accurate canvas: real latitudes and longitudes,
 * projected through [com.potheride.app.core.geo.Bounds] so the shape on screen is
 * the shape of the actual route, correctly oriented with north at the top.
 *
 * This is a deliberate stand-in for Google Maps, not a mock-up. Everything the
 * product needs from a map at MVP stage — the published path, where the passenger
 * boards and alights, which stretch they share, and where the driver is right now —
 * is real data drawn to scale. Swapping in the Maps SDK is a matter of replacing
 * this Canvas with a `GoogleMap` composable and feeding the same [route] to a
 * `Polyline`; nothing above this file changes, and no API key or billing account is
 * needed to run and review the app in the meantime. See docs/MAPS.md.
 */
@Composable
fun RouteMapView(
    route: List<LatLng>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 240.dp,
    pickup: LatLng? = null,
    drop: LatLng? = null,
    sharedPath: List<LatLng> = emptyList(),
    driverPosition: LatLng? = null,
    label: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(Cloud)
    ) {
        if (route.size < 2) {
            Text(
                text = label ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Mist,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // Pad the bounds so markers near an edge are not clipped in half.
            val allPoints = remember(route, pickup, drop, driverPosition) {
                buildList {
                    addAll(route)
                    pickup?.let { add(it) }
                    drop?.let { add(it) }
                    driverPosition?.let { add(it) }
                }
            }
            val bounds = remember(allPoints) {
                GeoUtils.boundsOf(allPoints, paddingDegrees = 0.004)
            }

            // Animating the puck between fixes stops it teleporting each GPS update.
            val driverUnit = driverPosition?.let { bounds.toUnitSquare(it) }
            val animatedX by animateFloatAsState(
                targetValue = driverUnit?.first ?: 0f,
                animationSpec = tween(900),
                label = "driverX"
            )
            val animatedY by animateFloatAsState(
                targetValue = driverUnit?.second ?: 0f,
                animationSpec = tween(900),
                label = "driverY"
            )

            Canvas(Modifier.fillMaxSize().padding(20.dp)) {
                fun project(p: LatLng): Offset {
                    val unit = bounds.toUnitSquare(p)
                    return Offset(unit.first * size.width, unit.second * size.height)
                }

                drawGridBackdrop()

                val projected = route.map { project(it) }

                // Full published route: a soft casing under a dashed line, so the
                // driver's whole path reads as context rather than competing with the
                // passenger's own leg.
                for (i in 0 until projected.size - 1) {
                    drawLine(
                        color = Snow,
                        start = projected[i], end = projected[i + 1],
                        strokeWidth = 14f, cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Mist,
                        start = projected[i], end = projected[i + 1],
                        strokeWidth = 5f, cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))
                    )
                }

                // The stretch this passenger actually rides, drawn solid and on top.
                if (sharedPath.size >= 2) {
                    val shared = sharedPath.map { project(it) }
                    for (i in 0 until shared.size - 1) {
                        drawLine(
                            color = RouteGreen,
                            start = shared[i], end = shared[i + 1],
                            strokeWidth = 9f, cap = StrokeCap.Round
                        )
                    }
                }

                // Intermediate waypoints, small and quiet.
                projected.forEachIndexed { i, offset ->
                    if (i != 0 && i != projected.lastIndex) {
                        drawCircle(color = Snow, radius = 7f, center = offset)
                        drawCircle(color = Mist, radius = 7f, center = offset, style = Stroke(width = 2.5f))
                    }
                }

                // Route endpoints: square for the driver's origin and destination, so
                // they never read as the passenger's own pickup and drop pins.
                drawRouteEndpoint(projected.first(), Ink)
                drawRouteEndpoint(projected.last(), Ink)

                pickup?.let { drawMarker(project(it), RouteGreen) }
                drop?.let { drawMarker(project(it), AlertRed) }

                if (driverUnit != null) {
                    val position = Offset(animatedX * size.width, animatedY * size.height)
                    drawCircle(color = SignalAmber.copy(alpha = 0.25f), radius = 26f, center = position)
                    drawCircle(color = Snow, radius = 13f, center = position)
                    drawCircle(color = SignalAmber, radius = 9f, center = position)
                }
            }
        }

        label?.takeIf { route.size >= 2 }?.let {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Snow)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Ink)
            }
        }

        overlay()
    }
}

/** Faint grid suggesting a map surface without pretending to be real cartography. */
private fun DrawScope.drawGridBackdrop() {
    val step = 44f
    var x = 0f
    while (x < size.width) {
        drawLine(Line.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(Line.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

private fun DrawScope.drawRouteEndpoint(center: Offset, color: Color) {
    drawCircle(color = Snow, radius = 11f, center = center)
    drawCircle(color = color, radius = 7f, center = center)
}

/** A teardrop-ish pin: filled disc with a white ring so it holds up over any line. */
private fun DrawScope.drawMarker(center: Offset, color: Color) {
    drawCircle(color = color.copy(alpha = 0.18f), radius = 22f, center = center)
    drawCircle(color = Snow, radius = 14f, center = center)
    drawCircle(color = color, radius = 10f, center = center)
    drawCircle(color = Snow, radius = 4f, center = center)
}
