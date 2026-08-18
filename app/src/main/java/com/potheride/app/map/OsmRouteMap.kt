package com.potheride.app.map

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.potheride.app.core.geo.LatLng
import com.potheride.app.ui.theme.LocalMapColors
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Must be called once, before the first [MapView] is created — from [android.app.Application]
 * is the safe place, since a `Composable` may be entered more than once.
 *
 * ### Why this cannot be skipped
 * OSMDroid's tile server rejects requests carrying the library's default user agent —
 * this is OSM's stated policy against unattributed bulk consumers, not a bug — and the
 * failure is **silent**: no exception, no error callback, just a permanently blank grid
 * where the map should be. Someone who hits this without knowing the cause can spend a
 * long time suspecting the network, the API key, or the overlay code before finding the
 * real answer is a missing line here.
 */
fun configureOsmdroid(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = context.getExternalFilesDir("osmdroid") ?: context.filesDir
        osmdroidTileCache = java.io.File(osmdroidBasePath, "tiles")
    }
}

/**
 * The tile-backed map, replacing [com.potheride.app.ui.components.RouteMapView] wherever
 * live network is available.
 *
 * Kept as a genuinely separate composable rather than a mode flag on the old view: the
 * canvas-drawn fallback has no tile dependency at all and stays exactly as useful as it
 * always was — for previews, for the emulator with no network, and as the screen's
 * fallback while a first route request is in flight.
 */
@Composable
fun OsmRouteMap(
    modifier: Modifier = Modifier,
    route: List<LatLng>,
    sharedStretch: List<LatLng> = emptyList(),
    origin: LatLng? = route.firstOrNull(),
    destination: LatLng? = route.lastOrNull(),
    livePosition: LatLng? = null
) {
    val context = LocalContext.current
    val colors = LocalMapColors.current
    val mapView = remember(context) {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
        }
    }

    DisposableEffect(mapView) {
        onDispose { mapView.onDetach() }
    }

    // Redrawn on every recomposition of these values rather than diffed incrementally —
    // a route has at most a few hundred points, and OSMDroid's overlay list rebuild is
    // fast enough at that size that incremental diffing would be complexity with no
    // measurable benefit.
    DisposableEffect(route, sharedStretch, origin, destination, livePosition, colors) {
        mapView.overlays.clear()

        if (route.size >= 2) {
            mapView.overlays.add(
                Polyline(mapView).apply {
                    outlinePaint.color = colors.route.toArgb()
                    outlinePaint.strokeWidth = ROUTE_STROKE_WIDTH_PX
                    setPoints(route.map { it.toGeoPoint() })
                }
            )
        }
        // Drawn after the main route so the overlaid stretch renders on top of it,
        // matching the wireframe's green-route-with-blue-overlay treatment.
        if (sharedStretch.size >= 2) {
            mapView.overlays.add(
                Polyline(mapView).apply {
                    outlinePaint.color = colors.sharedStretch.toArgb()
                    outlinePaint.strokeWidth = SHARED_STRETCH_STROKE_WIDTH_PX
                    setPoints(sharedStretch.map { it.toGeoPoint() })
                }
            )
        }
        origin?.let { mapView.overlays.add(coloredMarker(mapView, it, colors.origin.toArgb())) }
        destination?.let { mapView.overlays.add(coloredMarker(mapView, it, colors.destination.toArgb())) }
        // Drawn last so the live position is always the top-most marker.
        livePosition?.let { mapView.overlays.add(coloredMarker(mapView, it, colors.livePosition.toArgb())) }

        val centreOn = livePosition ?: route.getOrNull(route.size / 2) ?: origin
        centreOn?.let { mapView.controller.setCenter(it.toGeoPoint()) }

        mapView.invalidate()
        onDispose { }
    }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { mapView })
}

private fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(lat, lng)

/**
 * A solid-colour dot, drawn in code rather than shipped as a drawable resource.
 *
 * The wireframes' markers are plain filled circles with no icon glyph, and every marker
 * on the map — origin, destination, live position — is the same shape in a different
 * colour. A single programmatic drawable covers all three, and it means adding a new
 * marker role later needs no new asset, only a new [android.graphics.Color] value.
 */
private fun dotDrawable(colorArgb: Int): android.graphics.drawable.Drawable {
    val diameterPx = (MARKER_DIAMETER_DP * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(
        diameterPx, diameterPx, android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = colorArgb
        style = android.graphics.Paint.Style.FILL
    }
    val radius = diameterPx / 2f
    canvas.drawCircle(radius, radius, radius, paint)
    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = radius * 0.18f
    }
    canvas.drawCircle(radius, radius, radius - borderPaint.strokeWidth / 2, borderPaint)
    return android.graphics.drawable.BitmapDrawable(android.content.res.Resources.getSystem(), bitmap)
}

private fun coloredMarker(mapView: MapView, point: LatLng, colorArgb: Int): Marker =
    Marker(mapView).apply {
        position = point.toGeoPoint()
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = dotDrawable(colorArgb)
    }

private const val ROUTE_STROKE_WIDTH_PX = 10f
private const val SHARED_STRETCH_STROKE_WIDTH_PX = 14f
private const val MARKER_DIAMETER_DP = 18
