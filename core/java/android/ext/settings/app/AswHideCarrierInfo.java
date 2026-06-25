package android.ext.settings.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateFlag;
import android.ext.settings.ExtSettings;

/** @hide */
public class AswHideCarrierInfo extends AppSwitch {
    public static final AswHideCarrierInfo I = new AswHideCarrierInfo();

    private AswHideCarrierInfo() {
        gosPsFlagNonDefault = GosPackageStateFlag.HIDE_CARRIER_INFO_NON_DEFAULT;
        gosPsFlag = GosPackageStateFlag.HIDE_CARRIER_INFO;
    }

    @Override
    public Boolean getImmutableValue(Context ctx, int userId, ApplicationInfo appInfo,
                                     GosPackageState ps, StateInfo si) {
        if (appInfo.isSystemApp()) {
            si.immutabilityReason = IR_IS_SYSTEM_APP;
            return false;
        }
        return null;
    }

    @Override
    protected boolean getDefaultValueInner(Context ctx, int userId, ApplicationInfo appInfo,
            GosPackageState ps, StateInfo si) {
        si.defaultValueReason = DVR_DEFAULT_SETTING;
        return ExtSettings.HIDE_CARRIER_INFO_BY_DEFAULT.get(ctx, userId);
    }
}
