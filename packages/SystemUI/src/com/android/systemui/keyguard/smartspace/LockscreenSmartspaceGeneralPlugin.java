package com.android.systemui.keyguard.smartspace;

import android.content.Context;
import android.view.LayoutInflater;

import com.android.systemui.res.R;
import com.android.systemui.smartspace.plugin.BaseSmartspaceDataPlugin;

import javax.inject.Inject;

/**
 * Plugin that provides the lockscreen smartspace general view.
 */
public class LockscreenSmartspaceGeneralPlugin extends BaseSmartspaceDataPlugin {
    private final LayoutInflater mLayoutInflater;

    @Inject
    public LockscreenSmartspaceGeneralPlugin(LayoutInflater layoutInflater) {
        mLayoutInflater = layoutInflater;
    }

    @Override
    public SmartspaceView getView(Context context) {
        return (LockscreenSmartspaceGeneralView) mLayoutInflater.inflate(
                R.layout.lockscreen_smartspace_general_view, null, false);
    }
}
