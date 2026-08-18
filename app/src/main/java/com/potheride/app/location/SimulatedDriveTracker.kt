package com.potheride.app.location

import com.potheride.app.core.geo.GeoUtils
import com.potheride.app.core.geo.LatLng
import com.potheride.app.core.pricing.VehicleClass
import com.potheride.app.core.ride.EtaCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Walks a vehicle along a published route at a realistic speed, emitting a position
 * every tick.
 *
 * This exists because live tracking is unreviewable and undemoable on a stock
 * emulator: without a mock location set, the fused provider emits nothing, and the
 * screen sits empty in a way that looks like a bug. The driver screen offers this
 * as an explicit, clearly-labelled "simulate driving" mode. It is a demo aid, not a
 * fallback that silently replaces real GPS — the UI always says which one is
 * feeding the map.
 */
class SimulatedDriveTracker(
    private val route: List<LatLng>,
    private val vehicleClass: VehicleClass,
    private val hourOfDay: Int,
    private val tickMillis: Long = 1_000L,
    private val speedMultiplier: Double = 30.0
) {
    val totalKm: Double = GeoUtils.polylineLengthKm(route)

    /** Emits (position, distance travelled) until the destination is reached. */
    fun drive(startFromKm: Double = 0.0): Flow<Pair<LatLng, Double>> = flow {
        if (route.size < 2) return@flow

        val minutes = EtaCalculator.travelMinutes(totalKm, vehicleClass, hourOfDay)
        val kmPerTick = (totalKm / (minutes * 60.0)) * (tickMillis / 1000.0) * speedMultiplier

        var travelled = startFromKm.coerceIn(0.0, totalKm)
        while (travelled < totalKm) {
            val point = GeoUtils.pointAtDistance(route, travelled)
            emit(point.position to travelled)
            delay(tickMillis)
            travelled = (travelled + kmPerTick).coerceAtMost(totalKm)
        }
        emit(route.last() to totalKm)
    }
}
