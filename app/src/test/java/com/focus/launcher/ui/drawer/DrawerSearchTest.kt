package com.focus.launcher.ui.drawer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawerSearchTest {
    @Test fun `exact matches rank before one and two edits`() {
        assertEquals(0, searchRank("telegram", "telegram", typos = true))
        assertEquals(1, searchRank("maps", "google maps", typos = true))
        assertEquals(4, searchRank("telegrm", "telegram", typos = true))
        assertEquals(5, searchRank("telegarm", "telegram", typos = true))
    }

    @Test fun `one and two edits match app names`() {
        assertEquals(1, fuzzyDistance("telegrm", "telegram", listOf("telegram"))) // deletion
        assertEquals(1, fuzzyDistance("telegrax", "telegram", listOf("telegram"))) // substitution
        assertEquals(2, fuzzyDistance("telegarm", "telegram", listOf("telegram"))) // transposition
        assertEquals(3, fuzzyDistance("xyz123", "telegram", listOf("telegram")))
    }

    @Test fun `partial and multiword searches tolerate typos`() {
        assertEquals(1, fuzzyDistance("telgr", "telegram", listOf("telegram")))
        assertEquals(1, fuzzyDistance("google mps", "google maps", listOf("google", "maps")))
        assertEquals(3, fuzzyDistance("go", "google maps", listOf("google", "maps")))
    }

    @Test fun `without the setting a typo is found only as letters in order`() {
        assertEquals(0, searchRank("telegram", "telegram", typos = false))
        assertEquals(6, searchRank("telegrm", "telegram", typos = false))
        assertNull(searchRank("telegarm", "telegram", typos = false)) // two letters swapped
    }

    // Why auto-launch counts matches without typos: with them "map" would no longer narrow to Maps.
    @Test fun `a typo match is an extra result the plain search does not have`() {
        assertEquals(0, searchRank("map", "mapy", typos = true))
        assertEquals(4, searchRank("map", "mail", typos = true))
        assertNull(searchRank("map", "mail", typos = false))
    }
}
