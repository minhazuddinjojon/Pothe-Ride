package com.potheride.app.core.ride

import com.potheride.app.core.pricing.VehicleClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaCalculatorTest {

    @Test
    fun rushHourTakesLongerThanTheMiddleOfTheNight() {
        val evening = EtaCalculator.travelMinutes(10.0, VehicleClass.CAR, 18)
        val night = EtaCalculator.travelMinutes(10.0, VehicleClass.CAR, 3)
        assertTrue("evening peak should be slower: $evening vs $night", evening > night)
    }

    @Test
    fun bothDhakaPeaksAreSlowerThanMidday() {
        val midday = EtaCalculator.congestionFactor(13)
        assertTrue(EtaCalculator.congestionFactor(9) > midday)
        assertTrue(EtaCalculator.congestionFactor(18) > midday)
    }

    @Test
    fun aBikeBeatsACngOverTheSameDistance() {
        val bike = EtaCalculator.travelMinutes(10.0, VehicleClass.BIKE, 14)
        val cng = EtaCalculator.travelMinutes(10.0, VehicleClass.CNG, 14)
        assertTrue(bike < cng)
    }

    @Test
    fun anEtaIsNeverZeroMinutes() {
        assertEquals(1, EtaCalculator.travelMinutes(0.0, VehicleClass.CAR, 14))
        assertEquals(1, EtaCalculator.travelMinutes(-5.0, VehicleClass.CAR, 14))
    }

    @Test
    fun aTenKilometreCarTripInEveningTrafficIsRealisticForDhaka() {
        // Not the 12 minutes a naive 50 km/h model would predict.
        val minutes = EtaCalculator.travelMinutes(10.0, VehicleClass.CAR, 18)
        assertTrue("got $minutes minutes", minutes in 40..60)
    }

    @Test
    fun pickupEtaIsAfterDepartureAndDropoffIsAfterPickup() {
        val departure = 1_700_000_000_000L
        val pickup = EtaCalculator.pickupEtaMillis(departure, 4.0, VehicleClass.CAR, 14)
        val dropoff = EtaCalculator.dropoffEtaMillis(pickup, 8.0, VehicleClass.CAR, 14)
        assertTrue(pickup > departure)
        assertTrue(dropoff > pickup)
    }

    @Test
    fun congestionFactorIsDefinedForEveryHourIncludingOutOfRangeInput() {
        for (h in -5..30) {
            assertTrue(EtaCalculator.congestionFactor(h) > 0.0)
        }
    }
}
