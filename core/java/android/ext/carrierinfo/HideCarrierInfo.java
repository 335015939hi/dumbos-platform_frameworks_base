package android.ext.carrierinfo;

import android.app.AppGlobals;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.ext.settings.app.AswHideCarrierInfo;
import android.os.UserHandle;

/** @hide */
public class HideCarrierInfo {
    /** @hide */
    public static final int FLAG_HIDE_CARRIER_INFO = 1;

    private static volatile int flags;

    /** @hide */
    public static int getAppBindFlags(Context ctx, int userId, ApplicationInfo appInfo,
                                      GosPackageState gosPs) {
        if (AswHideCarrierInfo.I.get(ctx, userId, appInfo, gosPs)) {
            return FLAG_HIDE_CARRIER_INFO;
        }
        return 0;
    }

    /** @hide */
    public static void handleAppBindFlags(int v) {
        flags = v;
    }

    /** @hide */
    public static void onGosPackageStateChanged(Context ctx, GosPackageState gosPs) {
        Application app = AppGlobals.getInitialApplication();
        if (app == null) {
            return;
        }
        flags = getAppBindFlags(ctx, UserHandle.myUserId(), app.getApplicationInfo(), gosPs);
    }

    /** @hide */
    public static boolean isEnabled() {
        return (flags & FLAG_HIDE_CARRIER_INFO) != 0;
    }

    private HideCarrierInfo() {}
}
