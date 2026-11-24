package com.pokedaisy.app.companion.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which DS carts NO HMS NEEDED's hooks were proved on (DsHmFree.kt); the hooks themselves were checked headless. */
class DsHmFreeTest {
    @Test fun provedCartsOnly() {
        for ((code, rev) in listOf("CPUE" to 1, "IPKE" to 0, "IPGE" to 0, "IRBO" to 0, "IRAO" to 0, "IREO" to 0, "IRDO" to 0))
            assertTrue(code, DsHmFree.proved(code, rev))
        assertFalse(DsHmFree.proved("CPUE", 0))
        assertFalse(DsHmFree.proved("CPUF", 0))
        assertFalse(DsHmFree.proved("IRBF", 0))
        assertFalse(DsHmFree.proved("IREO", 1))
    }
}
