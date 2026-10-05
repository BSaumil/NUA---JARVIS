package com.nua.assistant.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Needs Robolectric: calls the real `Context.getSharedPreferences`, same reason
 *  `PresenceRepositoryTest`/`DecisionDaoRobolectricTest` do. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PersonalityPreferenceStoreTest {

    private fun store() = PersonalityPreferenceStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `with nothing saved yet, getAxes returns the all-default axes`() {
        assertEquals(PersonalityAxes(), store().getAxes())
    }

    @Test
    fun `a saved non-default axes round-trips through a fresh store instance`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val axes = PersonalityAxes(humor = HumorLevel.HIGH, directness = DirectnessLevel.GENTLE)
        PersonalityPreferenceStore(context).setAxes(axes)
        assertEquals(axes, PersonalityPreferenceStore(context).getAxes())
    }
}
