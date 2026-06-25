package grapheneos.hidecarrierinfotest;

import android.content.pm.GosPackageStateFlag;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import grapheneos.hardeningtest.TestUtils;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class HideCarrierInfoTest extends BaseHostJUnit4Test {

    private static final String TEST_PACKAGE = "app.grapheneos.hidecarrierinfotest";
    private static final String DEVICE_TEST_CLASS = TEST_PACKAGE + ".HideCarrierInfoDeviceTest";

    private static final String BIONIC_TEST_BINARY =
            "/data/nativetest64/bionic-sysprop-tests/bionic-sysprop-tests";
    private static final String BIONIC_HCI_FILTER =
            "properties.__system_property_add_extended_override:" +
            "properties.__system_property_update_extended_override_denylist";

    private void runDeviceTest(String methodName) {
        var opts = new DeviceTestRunOptions(TEST_PACKAGE);
        opts.setTestClassName(DEVICE_TEST_CLASS);
        opts.setTestMethodName(methodName);
        try {
            runDeviceTests(opts);
        } catch (DeviceNotAvailableException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setHideCarrierInfo(boolean isSet) {
        TestUtils.setComplexFlagState(this, TEST_PACKAGE,
                GosPackageStateFlag.HIDE_CARRIER_INFO,
                GosPackageStateFlag.HIDE_CARRIER_INFO_NON_DEFAULT,
                isSet);
    }

    // covers new extended sysprop overrides area
    // + inheritance of ro.appcompat
    // see bionic/tests/system_properties_test.cpp
    // __system_property_add_extended_override()
    // __system_property_update_extended_override_denylist()
    @Test
    public void testBionicProperties() throws DeviceNotAvailableException {
        if (!getDevice().doesFileExist(BIONIC_TEST_BINARY)) {
            return;
        }
        var result = getDevice().executeShellV2Command(
                BIONIC_TEST_BINARY + " --gtest_filter=" + BIONIC_HCI_FILTER);
        Assert.assertEquals(result.getStdout() + result.getStderr(),
                0L, (long) result.getExitCode());
    }

    @Test
    public void testCarrierInfoHidden() {
        setHideCarrierInfo(true);
        runDeviceTest("testCarrierInfoHidden");
    }

    @Test
    public void testCarrierInfoVisible() {
        setHideCarrierInfo(false);
        runDeviceTest("testCarrierInfoVisible");
    }
}
