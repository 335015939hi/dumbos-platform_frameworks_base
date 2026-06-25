package app.grapheneos.hidecarrierinfotest

import android.app.Application
import android.ext.carrierinfo.HideCarrierInfo
import android.location.Country
import android.location.CountryDetector
import android.os.SystemProperties
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.runner.AndroidJUnit4
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HideCarrierInfoDeviceTest {
    private val ctx = ApplicationProvider.getApplicationContext<Application>()
    private val tm = ctx.getSystemService(TelephonyManager::class.java)!!

    @Test
    fun testCarrierInfoHidden() {
        Assert.assertTrue("HideCarrierInfo.isEnabled()", HideCarrierInfo.isEnabled())

        // TelephonyManager
        Assert.assertEquals("", tm.networkOperatorName)
        Assert.assertEquals("", tm.networkOperator)
        Assert.assertEquals("", tm.networkCountryIso)
        Assert.assertEquals("", tm.simOperator)
        Assert.assertEquals("", tm.simOperatorName)
        Assert.assertEquals("", tm.simCountryIso)
        Assert.assertFalse(tm.isNetworkRoaming)
        Assert.assertEquals(TelephonyManager.SIM_STATE_ABSENT, tm.simState)
        Assert.assertEquals(TelephonyManager.SIM_STATE_ABSENT, tm.getSimState(0))

        // double check sysprops overrides
        Assert.assertEquals("", SystemProperties.get("gsm.sim.operator.alpha"))
        Assert.assertEquals("", SystemProperties.get("gsm.sim.operator.numeric"))
        Assert.assertEquals("", SystemProperties.get("gsm.operator.alpha"))
        Assert.assertEquals("", SystemProperties.get("gsm.operator.numeric"))
        Assert.assertEquals("def", SystemProperties.get("gsm.sim.operator.alpha", "def"))
        Assert.assertNull(SystemProperties.find("gsm.sim.operator.alpha"))

        // non-replacements
        Assert.assertNotEquals("", SystemProperties.get("ro.product.cpu.abi"))

        // CountryDetector
        val cd = ctx.getSystemService(CountryDetector::class.java)
        if (cd != null) {
            val country = cd.detectCountry()
            if (country != null) {
                val src = country.source
                Assert.assertTrue(
                    "Country.getSource()=$src must be LOCATION or LOCALE when hidden",
                    src == Country.COUNTRY_SOURCE_LOCATION ||
                        src == Country.COUNTRY_SOURCE_LOCALE,
                )
            }

            val latch = CountDownLatch(1)
            var listenerCountry: Country? = null
            val callback: (Country) -> Unit = { c ->
                listenerCountry = c
                latch.countDown()
            }
            cd.registerCountryDetectorCallback({ it.run() }, callback)
            cd.unregisterCountryDetectorCallback(callback)
            Assert.assertTrue("CountryDetectorCallback was not called on registration",
                latch.await(5, TimeUnit.SECONDS))
            val listenerSrc = listenerCountry!!.source
            Assert.assertTrue(
                "CountryDetectorCallback source=$listenerSrc must be LOCATION or LOCALE when hidden",
                listenerSrc == Country.COUNTRY_SOURCE_LOCATION ||
                    listenerSrc == Country.COUNTRY_SOURCE_LOCALE,
            )
        }
    }

    @Test
    fun testCarrierInfoVisible() {
        Assert.assertFalse("HideCarrierInfo.isEnabled()", HideCarrierInfo.isEnabled())

        // non-carrier sysprop still readable
        Assert.assertNotEquals("", SystemProperties.get("ro.product.cpu.abi"))

        // gsm sysprops are not filtered
        val alpha = SystemProperties.get("gsm.sim.operator.alpha")
        if (alpha.isNotEmpty()) {
            Assert.assertNotNull(SystemProperties.find("gsm.sim.operator.alpha"))
            Assert.assertNotEquals("def", SystemProperties.get("gsm.sim.operator.alpha", "def"))
        }

        // CountryDetector
        val cd = ctx.getSystemService(CountryDetector::class.java)
        if (cd != null) {
            val latch = CountDownLatch(1)
            var listenerCountry: Country? = null
            val callback: (Country) -> Unit = { c ->
                listenerCountry = c
                latch.countDown()
            }
            cd.registerCountryDetectorCallback({ it.run() }, callback)
            cd.unregisterCountryDetectorCallback(callback)
            Assert.assertTrue("CountryDetectorCallback was not called on registration",
                latch.await(5, TimeUnit.SECONDS))
            val validSources = setOf(
                Country.COUNTRY_SOURCE_NETWORK,
                Country.COUNTRY_SOURCE_LOCATION,
                Country.COUNTRY_SOURCE_SIM,
                Country.COUNTRY_SOURCE_LOCALE,
            )
            Assert.assertTrue("CountryDetectorCallback source=${listenerCountry!!.source} is not a valid source",
                listenerCountry!!.source in validSources)
        }
    }
}
