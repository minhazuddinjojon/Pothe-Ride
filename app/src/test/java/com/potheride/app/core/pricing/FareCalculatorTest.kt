package com.potheride.app.core.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FareCalculatorTest {

    private fun fare(
        vehicle: VehicleClass = VehicleClass.CAR,
        km: Double = 10.0,
        minutes: Int = 30,
        seats: Int = 1,
        overlap: Float = 0.8f,
        hour: Int = 14
    ) = FareCalculator.calculate(vehicle, km, minutes, seats, overlap, hour)

    @Test
    fun takaArithmeticIsExactAcrossAdditionAndScaling() {
        // Held as poisha specifically so repeated arithmetic does not drift the way
        // accumulated Doubles do.
        var total = Taka.ZERO
        repeat(10) { total += Taka.ofTaka(0.1) }
        assertEquals(Taka.ofTaka(1L), total)
    }

    @Test
    fun takaRoundsToWholeCurrency() {
        assertEquals(Taka.ofTaka(121L), Taka.ofTaka(120.6).roundedToTaka())
        assertEquals(Taka.ofTaka(120L), Taka.ofTaka(120.4).roundedToTaka())
    }

    @Test
    fun aLongerTripCostsMore() {
        assertTrue(fare(km = 20.0).totalFare > fare(km = 5.0).totalFare)
    }

    @Test
    fun moreSeatsMultiplyTheTotalButNotThePerSeatPrice() {
        val one = fare(seats = 1)
        val three = fare(seats = 3)
        assertEquals(one.perSeatFare, three.perSeatFare)
        assertEquals(one.perSeatFare * 3, three.totalFare)
    }

    @Test
    fun higherRouteOverlapEarnsADeeperDiscount() {
        // The whole premise of the product: if the driver was going there anyway,
        // the passenger should pay less than for a detour.
        val loose = fare(overlap = 0.2f)
        val tight = fare(overlap = 1.0f)
        assertTrue(tight.sharedDiscountRate > loose.sharedDiscountRate)
        assertTrue(tight.perSeatFare < loose.perSeatFare)
    }

    @Test
    fun discountStaysWithinTheConfiguredBand() {
        val rules = PricingRules.DEFAULT
        for (overlap in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val rate = fare(overlap = overlap).sharedDiscountRate
            assertTrue(rate >= rules.minSharedDiscount - 1e-9)
            assertTrue(rate <= rules.maxSharedDiscount + 1e-9)
        }
    }

    @Test
    fun aBikeIsCheaperThanAMicrobusForTheSameTrip() {
        assertTrue(fare(vehicle = VehicleClass.BIKE).totalFare < fare(vehicle = VehicleClass.MICROBUS).totalFare)
    }

    @Test
    fun vehicleOrderingIsMonotonicByClass() {
        val bike = fare(vehicle = VehicleClass.BIKE).perSeatFare
        val cng = fare(vehicle = VehicleClass.CNG).perSeatFare
        val car = fare(vehicle = VehicleClass.CAR).perSeatFare
        val micro = fare(vehicle = VehicleClass.MICROBUS).perSeatFare
        assertTrue(bike < cng)
        assertTrue(cng < car)
        assertTrue(car < micro)
    }

    @Test
    fun aVeryShortTripStillChargesTheMinimumFare() {
        val result = fare(km = 0.1, minutes = 1, overlap = 1.0f)
        val minimum = PricingRules.DEFAULT.rates.getValue(VehicleClass.CAR).minimumFare
        assertTrue(result.perSeatFare >= minimum)
    }

    @Test
    fun nightRidesCostMoreThanAfternoonRides() {
        assertTrue(fare(hour = 2).grossFare > fare(hour = 14).grossFare)
    }

    @Test
    fun nightWindowWrapsCorrectlyAroundMidnight() {
        assertTrue(FareCalculator.isNightHour(23))
        assertTrue(FareCalculator.isNightHour(0))
        assertTrue(FareCalculator.isNightHour(4))
        assertTrue(!FareCalculator.isNightHour(5))
        assertTrue(!FareCalculator.isNightHour(14))
        assertTrue(!FareCalculator.isNightHour(22))
    }

    @Test
    fun negativeAndOutOfRangeHoursAreNormalised() {
        assertEquals(FareCalculator.isNightHour(2), FareCalculator.isNightHour(26))
        assertEquals(FareCalculator.isNightHour(2), FareCalculator.isNightHour(-22))
    }

    @Test
    fun driverEarningsPlusPlatformFeeEqualTheTotal() {
        val result = fare(seats = 2)
        assertEquals(result.totalFare, result.driverEarnings + result.platformFee)
    }

    @Test
    fun platformFeeMatchesTheConfiguredCommission() {
        val result = fare()
        val expected = (result.totalFare * PricingRules.DEFAULT.platformCommission).roundedToTaka()
        assertEquals(expected, result.platformFee)
    }

    @Test
    fun negativeDistanceIsTreatedAsZeroRatherThanCreditingThePassenger() {
        val result = fare(km = -50.0, minutes = 0, overlap = 1.0f)
        assertTrue(result.totalFare > Taka.ZERO)
        assertEquals(0.0, result.distanceKm, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroSeatsIsRejected() {
        fare(seats = 0)
    }

    @Test
    fun theBreakdownAddsUp() {
        val result = fare(hour = 14)
        // No night surcharge at 2pm, so gross is base + distance + time exactly.
        assertEquals(
            result.baseFare + result.distanceFare + result.timeFare,
            result.grossFare
        )
    }

    @Test
    fun anUnknownVehicleFallsBackToCarPricingRatherThanCrashing() {
        val emptyRules = PricingRules(rates = PricingRules.DEFAULT.rates.filterKeys { it == VehicleClass.CAR })
        val result = FareCalculator.calculate(VehicleClass.MICROBUS, 10.0, 30, 1, 0.8f, 14, emptyRules)
        assertTrue(result.totalFare > Taka.ZERO)
    }
}
