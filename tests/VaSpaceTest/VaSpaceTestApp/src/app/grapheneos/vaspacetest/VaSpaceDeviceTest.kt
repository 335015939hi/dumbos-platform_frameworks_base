package app.grapheneos.vaspacetest

import androidx.test.runner.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VaSpaceDeviceTest {

    @Test
    fun testExtendedVaSpaceEnabled() = assertExtendedVaSpace(enabled = true)

    @Test
    fun testExtendedVaSpaceDisabled() = assertExtendedVaSpace(enabled = false)

    private fun assertExtendedVaSpace(enabled: Boolean) {
        // 39-bit VA limit: 2^39 = 0x80_0000_0000
        val limit = 1L shl 39
        val maps = File("/proc/self/maps").readLines()
        if (enabled) {
            val hasHighMapping = maps.any { line ->
                line.substringBefore(' ').substringAfter('-').toLong(16) > limit
            }
            Assert.assertTrue("no mapping above 39-bit; extended VA space may not be active", hasHighMapping)
        } else {
            for (line in maps) {
                val end = line.substringBefore(' ').substringAfter('-')
                Assert.assertTrue(
                    "mapping end 0x$end exceeds 39-bit VA limit (0x${limit.toString(16)})",
                    end.toLong(16) <= limit,
                )
            }
        }
    }
}
