package com.pokedaisy.app.companion.data

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** EXP SHARE on the DS ([ExpShare.dsFind]): Gen 4's and Gen 5's US carts on melonDS (the native side's patch is theirs alone). */
class DsExpShareTest {
    @Test
    fun `the Gen 4 US carts on melonDS, nothing else`() {
        for ((code, rev) in listOf("CPUE" to 1, "IPKE" to 0, "IPGE" to 0, "IRBO" to 0, "IRAO" to 0, "IREO" to 0, "IRDO" to 0)) {
            assertTrue("$code rev $rev", ExpShare.dsFind(code, rev, dsperate = false, hack = false)?.ds == true)
            assertNull("$code on DSperate", ExpShare.dsFind(code, rev, dsperate = true, hack = false))
        }
        assertNull("Renegade Platinum", ExpShare.dsFind("CPUE", 1, dsperate = false, hack = true))
        assertNull("a Redux build", ExpShare.dsFind("IREO", 0, dsperate = false, hack = true))
        // Platinum rev 0, German Platinum and Gen 5's other languages (other addresses): not checked.
        for ((code, rev) in listOf("CPUE" to 0, "CPUD" to 0, "IRBF" to 0, "IREF" to 0))
            assertNull("$code rev $rev", ExpShare.dsFind(code, rev, dsperate = false, hack = false))
    }

    @Test
    fun `the other Gen 4 carts, found by their code`() {
        for ((code, rev) in listOf("CPUF" to 0, "CPUS" to 0, "IPKD" to 0, "IPGF" to 0, "IPKE" to 10, "IPGE" to 10, "IPKS" to 0,
            "IPGI" to 0, "IPKK" to 0, "IPGJ" to 0)) {
            assertTrue("$code rev $rev", ExpShare.dsFind(code, rev, dsperate = false, hack = false)?.ds == true)
            assertNull("$code on DSperate", ExpShare.dsFind(code, rev, dsperate = true, hack = false))
        }
    }
}
