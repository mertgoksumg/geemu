package com.mertg.geemu

import com.mertg.geemu.data.SwitchContentFilter
import com.mertg.geemu.model.RomEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchContentFilterTest {
    private fun rom(name: String) = RomEntry(name.substringBeforeLast('.'), "content://test/$name", name)

    @Test
    fun baseApplicationNspIsVisible() {
        assertTrue(SwitchContentFilter.isBootableGame(rom("Cult of the Lamb [01002E7016C46000][v0].nsp")))
    }

    @Test
    fun updateAndDlcTitleIdsAreHidden() {
        assertFalse(SwitchContentFilter.isBootableGame(rom("Cult of the Lamb Update [01002E7016C46800][v65536].nsp")))
        assertFalse(SwitchContentFilter.isBootableGame(rom("Cult of the Lamb DLC [01002E7016C47001][v0].nsp")))
    }

    @Test
    fun unpackedNcaIsNeverShownAsAGame() {
        assertFalse(SwitchContentFilter.isBootableGame(rom("01002E7016C46000.nca")))
    }

    @Test
    fun cartridgeAndHomebrewFilesRemainVisible() {
        assertTrue(SwitchContentFilter.isBootableGame(rom("Game.xci")))
        assertTrue(SwitchContentFilter.isBootableGame(rom("Homebrew.nro")))
    }
}
