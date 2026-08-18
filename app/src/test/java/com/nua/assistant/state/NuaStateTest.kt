package com.nua.assistant.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NuaStateTest {

    // -----------------------------------------------------------------------------------
    // Absent signals must stay absent. The whole point of the model is that the dashboard
    // never invents state to look alive.
    // -----------------------------------------------------------------------------------

    @Test
    fun `an empty input set produces a calm, honestly-empty state`() {
        val state = deriveNuaState(NuaStateInputs())
        assertEquals(NuaFocus.CALM, state.focus)
        assertNull("confidence must stay null, not default to 0", state.confidence)
        assertNull("no goals means no objective, not a placeholder", state.currentObjective)
        assertTrue(state.pendingTasks.isEmpty())
        assertEquals(0, state.workload.total)
    }

    @Test
    fun `no trust history reports null confidence rather than a zero score`() {
        // Zero would read as "NUA is untrustworthy"; null means "hasn't earned one yet".
        assertNull(deriveNuaState(NuaStateInputs(trustScore = null)).confidence)
        assertEquals(72, deriveNuaState(NuaStateInputs(trustScore = 72)).confidence)
    }

    @Test
    fun `an unreadable calendar is UNKNOWN availability, never FREE`() {
        val state = deriveNuaState(NuaStateInputs(currentlyInEvent = null))
        assertEquals(UserAvailability.UNKNOWN, state.availability)
        assertTrue(
            "context should admit the calendar is unreadable",
            state.currentContext.contains("isn't readable"),
        )
    }

    @Test
    fun `a readable, empty calendar is FREE and says so plainly`() {
        val state = deriveNuaState(NuaStateInputs(currentlyInEvent = false, eventsRemainingToday = 0))
        assertEquals(UserAvailability.FREE, state.availability)
        assertEquals("Nothing left on the calendar today.", state.currentContext)
    }

    @Test
    fun `being in an event is reported as in-event`() {
        val state = deriveNuaState(NuaStateInputs(currentlyInEvent = true))
        assertEquals(UserAvailability.IN_EVENT, state.availability)
    }

    // -----------------------------------------------------------------------------------
    // Focus is arithmetic over real counts, not a mood.
    // -----------------------------------------------------------------------------------

    @Test
    fun `focus escalates with real workload`() {
        fun focusWith(notifications: Int) = deriveNuaState(
            NuaStateInputs(notificationsNeedingAttention = List(notifications) { "n$it" }),
        ).focus

        assertEquals(NuaFocus.CALM, focusWith(0))
        assertEquals(NuaFocus.ATTENTIVE, focusWith(1))
        // Each notification counts once as attention and once as a pending item.
        assertEquals(NuaFocus.BUSY, focusWith(2))
        assertEquals(NuaFocus.OVERLOADED, focusWith(4))
    }

    @Test
    fun `calendar events count toward workload even with no notifications`() {
        val state = deriveNuaState(NuaStateInputs(currentlyInEvent = false, eventsRemainingToday = 5))
        assertEquals(5, state.workload.total)
        assertEquals(NuaFocus.BUSY, state.focus)
    }

    // -----------------------------------------------------------------------------------
    // Pending work is gathered from every real source and labelled by kind.
    // -----------------------------------------------------------------------------------

    @Test
    fun `pending tasks come from all four real sources and keep their kind`() {
        val state = deriveNuaState(
            NuaStateInputs(
                notificationsNeedingAttention = listOf("Message from Sam"),
                documentsExpiringSoon = listOf("lease.pdf is expiring"),
                visionMonitorsDue = listOf("Recheck the plant"),
                recentGoalObservations = listOf("You've run three times this week"),
            ),
        )
        assertEquals(4, state.pendingTasks.size)
        assertEquals(
            setOf(
                PendingKind.NOTIFICATION,
                PendingKind.DOCUMENT_EXPIRY,
                PendingKind.VISION_RECHECK,
                PendingKind.GOAL_OBSERVATION,
            ),
            state.pendingTasks.map { it.kind }.toSet(),
        )
    }

    @Test
    fun `the first active goal becomes the current objective`() {
        val state = deriveNuaState(NuaStateInputs(activeGoals = listOf("Run a half marathon", "Read more")))
        assertEquals("Run a half marathon", state.currentObjective)
    }

    // -----------------------------------------------------------------------------------
    // Owning up to mistakes, but not drowning the dashboard in them.
    // -----------------------------------------------------------------------------------

    @Test
    fun `recent mistakes are surfaced but capped`() {
        val state = deriveNuaState(NuaStateInputs(recentFailures = List(10) { "failure $it" }))
        assertEquals(3, state.recentMistakes.size)
        assertEquals("failure 0", state.recentMistakes.first())
    }

    @Test
    fun `no failures means nothing to own up to`() {
        assertTrue(deriveNuaState(NuaStateInputs()).recentMistakes.isEmpty())
    }

    // -----------------------------------------------------------------------------------
    // Offline is a first-class state, and it takes precedence in what NUA leads with.
    // -----------------------------------------------------------------------------------

    @Test
    fun `offline overrides the headline and the context line`() {
        val state = deriveNuaState(
            NuaStateInputs(isOffline = true, notificationsNeedingAttention = listOf("Message from Sam")),
        )
        assertTrue(state.isOffline)
        assertTrue(state.headline.contains("Offline"))
        assertTrue(state.currentContext.contains("No connection"))
    }

    @Test
    fun `the headline counts pending work and stays grammatical`() {
        assertEquals("Nothing needs you right now.", deriveNuaState(NuaStateInputs()).headline)
        assertEquals(
            "1 thing needs your attention.",
            deriveNuaState(NuaStateInputs(notificationsNeedingAttention = listOf("one"))).headline,
        )
        assertEquals(
            "2 things need your attention.",
            deriveNuaState(NuaStateInputs(notificationsNeedingAttention = listOf("one", "two"))).headline,
        )
    }

    @Test
    fun `a clear day with calendar events still reads as not-pending`() {
        val state = deriveNuaState(NuaStateInputs(currentlyInEvent = false, eventsRemainingToday = 2))
        assertTrue(state.pendingTasks.isEmpty())
        assertTrue("headline should describe the day, not claim pending work", state.headline.contains("today looks"))
    }

    @Test
    fun `derivation is deterministic for identical inputs`() {
        val inputs = NuaStateInputs(
            activeGoals = listOf("Ship NUA"),
            trustScore = 64,
            notificationsNeedingAttention = listOf("a"),
            eventsRemainingToday = 2,
            currentlyInEvent = false,
        )
        assertEquals(deriveNuaState(inputs), deriveNuaState(inputs))
    }
}
