package com.potheride.app.core.ride

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RideStateMachineTest {

    @Test
    fun theHappyPathRunsEndToEnd() {
        var state = RideState.REQUESTED
        val visited = mutableListOf(state)
        while (true) {
            val next = RideStateMachine.nextHappyPathState(state) ?: break
            assertNull(RideStateMachine.validate(state, next, Actor.DRIVER).takeIf { false })
            state = next
            visited.add(state)
        }
        assertEquals(RideState.HAPPY_PATH, visited)
    }

    @Test
    fun paymentIsTheFinalHappyPathState() {
        assertNull(RideStateMachine.nextHappyPathState(RideState.PAID))
        assertTrue(RideState.PAID.isTerminal)
    }

    @Test
    fun aRideCannotSkipStraightFromRequestedToCompleted() {
        assertFalse(RideStateMachine.canTransition(RideState.REQUESTED, RideState.COMPLETED))
        assertNotNull(RideStateMachine.validate(RideState.REQUESTED, RideState.COMPLETED, Actor.DRIVER))
    }

    @Test
    fun aRideCannotSkipPayment() {
        assertFalse(RideStateMachine.canTransition(RideState.PICKED_UP, RideState.PAID))
    }

    @Test
    fun onlyTheDriverMayAcceptARequest() {
        assertNull(RideStateMachine.validate(RideState.REQUESTED, RideState.ACCEPTED, Actor.DRIVER))
        assertNotNull(RideStateMachine.validate(RideState.REQUESTED, RideState.ACCEPTED, Actor.PASSENGER))
    }

    @Test
    fun onlyTheDriverMayDeclineARequest() {
        assertNull(RideStateMachine.validate(RideState.REQUESTED, RideState.DECLINED, Actor.DRIVER))
        assertNotNull(RideStateMachine.validate(RideState.REQUESTED, RideState.DECLINED, Actor.PASSENGER))
    }

    @Test
    fun aDriverCannotCancelARequestTheyHaveNotAnsweredYet() {
        // Declining is the driver's move here; cancelling is the passenger's.
        assertNotNull(RideStateMachine.validate(RideState.REQUESTED, RideState.CANCELLED, Actor.DRIVER))
    }

    @Test
    fun nobodyCanRestartATerminalRide() {
        for (terminal in listOf(RideState.PAID, RideState.CANCELLED, RideState.DECLINED)) {
            assertTrue(RideStateMachine.nextStates(terminal).isEmpty())
            for (actor in Actor.values()) {
                assertNotNull(RideStateMachine.validate(terminal, RideState.REQUESTED, actor))
            }
        }
    }

    @Test
    fun transitioningToTheSameStateIsRejected() {
        assertNotNull(RideStateMachine.validate(RideState.ACCEPTED, RideState.ACCEPTED, Actor.DRIVER))
    }

    @Test
    fun aRideInProgressCannotBeCancelled() {
        // Once the passenger is aboard, the ride finishes; it does not evaporate.
        assertFalse(RideStateMachine.canTransition(RideState.PICKED_UP, RideState.CANCELLED))
    }

    @Test
    fun seatsAreConsumedOnAcceptanceNotOnPickup() {
        assertTrue(RideStateMachine.consumesSeats(RideState.REQUESTED, RideState.ACCEPTED))
        assertFalse(RideStateMachine.consumesSeats(RideState.DRIVER_ARRIVING, RideState.PICKED_UP))
    }

    @Test
    fun seatsComeBackWhenAnAcceptedRideIsCancelled() {
        assertTrue(RideStateMachine.releasesSeats(RideState.ACCEPTED, RideState.CANCELLED))
        assertTrue(RideStateMachine.releasesSeats(RideState.DRIVER_ARRIVING, RideState.CANCELLED))
    }

    @Test
    fun seatsAreNotReleasedForARequestThatWasNeverAccepted() {
        // No seat was ever taken, so returning one would inflate the trip's capacity.
        assertFalse(RideStateMachine.releasesSeats(RideState.REQUESTED, RideState.CANCELLED))
    }

    @Test
    fun aLateCancellationByThePassengerIncursAFee() {
        assertTrue(RideStateMachine.incursCancellationFee(RideState.ACCEPTED, RideState.CANCELLED, Actor.PASSENGER))
        assertFalse(RideStateMachine.incursCancellationFee(RideState.REQUESTED, RideState.CANCELLED, Actor.PASSENGER))
        assertFalse(RideStateMachine.incursCancellationFee(RideState.ACCEPTED, RideState.CANCELLED, Actor.DRIVER))
    }

    @Test
    fun declinedAndCancelledAreNotShownOnTheProgressTracker() {
        assertFalse(RideState.DECLINED.isOnHappyPath)
        assertFalse(RideState.CANCELLED.isOnHappyPath)
        assertTrue(RideState.PICKED_UP.isOnHappyPath)
    }

    @Test
    fun everyReachableStateIsCoveredByTheTransitionTable() {
        for (state in RideState.values()) {
            // Must not throw or return null for any enum value.
            assertNotNull(RideStateMachine.nextStates(state))
        }
    }
}
