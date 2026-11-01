package com.example.tapcounter.headset

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HeadsetSettingsRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var repo: HeadsetSettingsRepository

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "t.preferences_pb") }
        repo = HeadsetSettingsRepository(store)
    }

    @After fun tearDown() = scope.cancel()

    private fun current() = runBlocking { repo.settings.first() }

    @Test fun firstRunGivesDefaults() = assertEquals(HeadsetSettings(), current())

    @Test fun bindingIsPersisted() = runBlocking {
        repo.setBinding(HeadsetKeys.MEDIA_NEXT, Gesture.DOUBLE, Action.STEALTH_TOGGLE)
        assertEquals(Action.STEALTH_TOGGLE, current().actionFor(HeadsetKeys.MEDIA_NEXT, Gesture.DOUBLE))
        // defaults were kept when the first custom binding was written
        assertEquals(Action.INCREMENT, current().actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.SINGLE))
    }

    @Test fun assigningNoneRemovesBinding() = runBlocking {
        repo.setBinding(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.SINGLE, Action.NONE)
        assertEquals(Action.NONE, current().actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.SINGLE))
    }

    @Test fun resetCannotBeAssignedAndStoreIsUntouched() = runBlocking {
        val before = current()
        try {
            repo.setBinding(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.LONG_PRESS, Action.RESET)
            fail("RESET accepted")
        } catch (_: ForbiddenActionException) {
        }
        assertEquals(before, current())
    }

    @Test fun tamperedStoreWithResetIsNeverExecuted() = runBlocking {
        store.edit { it[HeadsetSettingsRepository.Keys.BINDINGS] = setOf("85:SINGLE=RESET", "85:DOUBLE=INCREMENT") }
        val s = current()
        assertEquals(Action.NONE, s.actionFor(85, Gesture.SINGLE))
        assertEquals(Action.INCREMENT, s.actionFor(85, Gesture.DOUBLE))
        assertTrue(s.bindings.values.none { it == Action.RESET })
    }

    @Test fun hardwareLockToggle() = runBlocking {
        repo.setHardwareLock(true)
        assertTrue(current().hardwareLock)
        repo.setHardwareLock(false)
        assertFalse(current().hardwareLock)
    }

    @Test fun timingIsClamped() = runBlocking {
        repo.setTiming(debounceMs = 5000, multiTapMs = 10, longPressMs = 800)
        val s = current()
        assertEquals(300L, s.debounceMs)
        assertEquals(200L, s.multiTapWindowMs)
        assertEquals(800L, s.longPressMs)
    }

    @Test fun clearKeyAndResetToDefaults() = runBlocking {
        repo.clearBindingsForKey(HeadsetKeys.MEDIA_PLAY_PAUSE)
        assertEquals(Action.NONE, current().actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.SINGLE))
        repo.resetBindingsToDefaults()
        assertEquals(Action.INCREMENT, current().actionFor(HeadsetKeys.MEDIA_PLAY_PAUSE, Gesture.SINGLE))
    }
}
