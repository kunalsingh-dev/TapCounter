package com.example.tapcounter.counter

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherAliasesTest {
    @Test fun freshInstallUsesTheCalculatorAlias() {
        assertEquals(LauncherAliases.CALCULATOR, LauncherAliases.currentIndex(listOf(0, 0, 0)))
    }

    @Test fun enabledAliasWins() {
        assertEquals(LauncherAliases.CLOCK, LauncherAliases.currentIndex(listOf(2, 1, 2)))
        assertEquals(LauncherAliases.TAP_COUNTER, LauncherAliases.currentIndex(listOf(2, 2, 1)))
    }

    @Test fun noneEnabledFallsBackToCalculator() {
        assertEquals(LauncherAliases.CALCULATOR, LauncherAliases.currentIndex(listOf(2, 2, 2)))
    }

    @Test fun labelsAndNamesLineUp() {
        assertEquals(LauncherAliases.labels.size, LauncherAliases.componentNames.size)
    }
}
