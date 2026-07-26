package ru.yavasilek.netpulse.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun comparesStableVersions() {
        assertTrue(VersionComparator.isNewer("1.1.0", "1.0.9"))
        assertTrue(VersionComparator.isNewer("v2.0.0", "1.99.99"))
        assertFalse(VersionComparator.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun stableVersionBeatsPreRelease() {
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.0-beta.2"))
        assertFalse(VersionComparator.isNewer("1.0.0-beta.2", "1.0.0"))
    }

    @Test
    fun comparesPreReleaseNumbers() {
        assertTrue(VersionComparator.isNewer("1.0.0-beta.10", "1.0.0-beta.2"))
    }
}
