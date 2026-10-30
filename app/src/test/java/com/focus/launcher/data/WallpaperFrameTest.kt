package com.focus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** A picked picture is scaled to just cover the portrait screen and its centre kept, as the home screen shows it. */
class WallpaperFrameTest {

    @Test fun `a landscape photo is scaled to the screen height and its middle kept`() {
        assertEquals(WallpaperFrame(3200, 2400, 1060, 0, 1080, 2400), wallpaperFrame(4000, 3000, 1080, 2400))
    }

    @Test fun `a portrait photo is scaled to the screen width where that covers the height`() {
        assertEquals(WallpaperFrame(1800, 2400, 360, 0, 1080, 2400), wallpaperFrame(3000, 4000, 1080, 2400))
    }

    @Test fun `a small picture is never enlarged, only cut to the screen's proportions`() {
        assertEquals(WallpaperFrame(800, 600, 265, 0, 270, 600), wallpaperFrame(800, 600, 1080, 2400))
    }

    @Test fun `a tall strip keeps its full width and the middle of its height`() {
        assertEquals(WallpaperFrame(1000, 10000, 0, 3889, 1000, 2222), wallpaperFrame(1000, 10000, 1080, 2400))
    }
}
