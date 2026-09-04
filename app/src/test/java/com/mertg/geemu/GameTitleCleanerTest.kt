package com.mertg.geemu

import com.mertg.geemu.data.GameTitleCleaner
import org.junit.Assert.assertEquals
import org.junit.Test

class GameTitleCleanerTest {
    @Test fun removesSwitchMetadata() {
        assertEquals("Cult of the Lamb", GameTitleCleaner.clean("Cult of the Lamb [01002E7016C46000][v0][Base].nsp"))
    }

    @Test fun removesRegionAndDiscMetadata() {
        assertEquals("God of War II", GameTitleCleaner.clean("God.of.War.II (USA) [SCUS-97481].iso"))
    }

    @Test fun removesDiscAndVoiceMetadataWithoutBrokenPunctuation() {
        assertEquals(
            "Sakura Wars - So Long, My Love",
            GameTitleCleaner.clean("Sakura Wars - So Long, My Love (Disc 2) (Japanese Voice Over).gz")
        )
    }
}
