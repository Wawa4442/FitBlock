package com.fitblock.app

import org.junit.Assert.*
import org.junit.Test

class SportCatalogTest {
    @Test fun repertoireHasUniqueIdentifiersAndPreservesVerifiedExercises() {
        assertTrue(SportCatalog.all.size >= 100)
        assertEquals(SportCatalog.all.size, SportCatalog.all.map { it.id }.toSet().size)
        assertEquals(Exercise.entries.toSet(), SportCatalog.all.mapNotNull { it.exercise }.toSet())
        assertEquals(5, SportCatalog.all.count { it.exercise != null })
    }

    @Test fun searchingIsCaseAndAccentInsensitiveAndHonorsCategories() {
        assertTrue(SportCatalog.search("basquet").any { it.id == "basketball" })
        assertTrue(SportCatalog.search("NATACION").any { it.id == "pool_swimming" })
        assertTrue(SportCatalog.search("", SportCategory.WATER).all { it.category == SportCategory.WATER })
        assertTrue(SportCatalog.search("tenis", SportCategory.WATER).isEmpty())
        assertNotNull(SportCatalog.get("yoga"))
        assertNull(SportCatalog.get("unsupported"))
    }

    @Test fun pausedTimeIsExcludedAndBackgroundElapsedTimeSurvivesReconstruction() {
        val clock = SportClock("session", "cycling", 60_000, 0, 1000, true, 1)
        assertEquals(70_000L, clock.elapsed(71_000))
        val paused = clock.pause(31_000)
        assertEquals(30_000L, paused.elapsed(700_000))
        assertEquals(40_000L, paused.resume(700_000).elapsed(710_000))
        assertEquals(0L, clock.elapsed(500))
    }
}
