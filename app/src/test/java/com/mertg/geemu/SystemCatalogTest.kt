package com.mertg.geemu

import com.mertg.geemu.model.SystemCatalog
import com.mertg.geemu.model.SystemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCatalogTest {
    @Test
    fun switchSystemsAreSeparateEntries() {
        assertTrue(SystemCatalog.systems.any { it.id == SystemId.SWITCH_EDEN })
        assertTrue(SystemCatalog.systems.any { it.id == SystemId.SWITCH_CITRON })
    }

    @Test
    fun vitaCanBindItsInstalledLibraryProvider() {
        val vita = SystemCatalog.systems.first { it.id == SystemId.VITA }
        assertTrue(vita.supportsRomFolder)
        assertEquals("org.vita3k.emulator", vita.packageCandidates.first())
    }

    @Test
    fun systemIdsAreUnique() {
        assertEquals(
            SystemCatalog.systems.size,
            SystemCatalog.systems.map { it.id }.distinct().size
        )
    }
}
