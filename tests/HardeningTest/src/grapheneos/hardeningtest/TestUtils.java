package grapheneos.hardeningtest;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import static org.junit.Assert.assertEquals;

public class TestUtils {
    private TestUtils() {}

    public static void editGosPackageState(BaseHostJUnit4Test host, String pkgName,
                                           int[] addFlags, int[] clearFlags) {
        try {
            var device = host.getDevice();
            var cmd = new StringBuilder("pm edit-gos-package-state " + pkgName
                    + " " + device.getCurrentUser());
            for (int flag : addFlags) {
                cmd.append(" add-flag ").append(flag);
            }
            for (int flag : clearFlags) {
                cmd.append(" clear-flag ").append(flag);
            }
            var edRes = device.executeShellV2Command(cmd.toString());
            assertEquals(edRes.toString(), 0L, (long) edRes.getExitCode());
        } catch (DeviceNotAvailableException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void setComplexFlagState(BaseHostJUnit4Test host, String pkgName,
                                           int flag, int nonDefaultFlag, boolean isSet) {
        int[] addFlags = isSet ? new int[] { nonDefaultFlag, flag } : new int[] { nonDefaultFlag };
        int[] clearFlags = isSet ? new int[0] : new int[] { flag };
        editGosPackageState(host, pkgName, addFlags, clearFlags);
    }
}
