package dev.nerdytech.jellyglance

import dev.nerdytech.jellyglance.data.Updater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun newerVersions() {
        assertTrue(Updater.isNewer("1.16.0", "1.15.1"))
        assertTrue(Updater.isNewer("v2.0", "1.99.9"))
        assertTrue(Updater.isNewer("1.15.10", "1.15.9"))
        assertTrue(Updater.isNewer("1.16.0", "1.15.1-debug"))
    }

    @Test fun sameOrOlder() {
        assertFalse(Updater.isNewer("1.15.1", "1.15.1"))
        assertFalse(Updater.isNewer("1.15.0", "1.15.1"))
        assertFalse(Updater.isNewer("1.15", "1.15.0"))
        assertFalse(Updater.isNewer("v1.9.9", "1.10.0"))
    }
}
