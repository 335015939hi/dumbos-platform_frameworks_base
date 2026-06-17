package grapheneos.vaspacetest;

import android.content.pm.GosPackageStateFlag;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import grapheneos.hardeningtest.TestUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class VaSpaceTest extends BaseHostJUnit4Test {

    private static final String TEST_PACKAGE = "app.grapheneos.vaspacetest";
    private static final String DEVICE_TEST_CLASS = TEST_PACKAGE + ".VaSpaceDeviceTest";

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

    @Test
    public void testExtendedVaSpaceEnabled() {
        TestUtils.setComplexFlagState(this, TEST_PACKAGE,
                GosPackageStateFlag.USE_EXTENDED_VA_SPACE,
                GosPackageStateFlag.USE_EXTENDED_VA_SPACE_NON_DEFAULT,
                true);
        runDeviceTest("testExtendedVaSpaceEnabled");
    }

    @Test
    public void testExtendedVaSpaceDisabled() {
        // hardened_malloc forces extended VA space on, disable for testing
        TestUtils.setComplexFlagState(this, TEST_PACKAGE,
                GosPackageStateFlag.USE_HARDENED_MALLOC,
                GosPackageStateFlag.USE_HARDENED_MALLOC_NON_DEFAULT,
                false);
        TestUtils.setComplexFlagState(this, TEST_PACKAGE,
                GosPackageStateFlag.USE_EXTENDED_VA_SPACE,
                GosPackageStateFlag.USE_EXTENDED_VA_SPACE_NON_DEFAULT,
                false);
        runDeviceTest("testExtendedVaSpaceDisabled");
    }
}
