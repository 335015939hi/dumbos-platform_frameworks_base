package com.android.server.policy;

import android.app.ActivityManager;

import com.android.internal.R;
import com.android.internal.globalactions.SinglePressAction;

public final class RestartSafeModeAction extends SinglePressAction {
    private final WindowManagerPolicy.WindowManagerFuncs mWindowManagerFuncs;

    public RestartSafeModeAction(WindowManagerPolicy.WindowManagerFuncs windowManagerFuncs) {
        super(R.drawable.ic_restart, R.string.reboot_safemode_title);
        mWindowManagerFuncs = windowManagerFuncs;
    }

    @Override
    public boolean showDuringKeyguard() {
        return true;
    }

    @Override
    public boolean showBeforeProvisioning() {
        return true;
    }

    @Override
    public void onPress() {
        if (ActivityManager.isUserAMonkey()) {
            return;
        }
        mWindowManagerFuncs.rebootSafeMode(true);
    }
}
