package com.android.systemui.keyguard.smartspace;

import android.app.smartspace.SmartspaceTarget;
import android.content.ComponentName;
import android.os.Parcelable;

import com.android.systemui.plugins.BcSmartspaceDataPlugin;
import com.android.systemui.statusbar.lockscreen.LockscreenSmartspaceController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.util.ViewController;

import java.util.List;

/**
 * Controller for LockscreenSmartspaceGeneralView. This is a reimplementation of the legacy
 * KeyguardSliceViewController.
 */
public class LockscreenSmartspaceGeneralViewController extends
        ViewController<LockscreenSmartspaceGeneralView> implements
        BcSmartspaceDataPlugin.SmartspaceTargetListener {

    private final LockscreenSmartspaceController mLockscreenSmartspaceController;
    private final ConfigurationController mConfigurationController;

    ConfigurationController.ConfigurationListener mConfigurationListener =
            new ConfigurationController.ConfigurationListener() {
                @Override
                public void onDensityOrFontScaleChanged() {
                    mView.onDensityOrFontScaleChanged();
                }
                @Override
                public void onThemeChanged() {
                    mView.onOverlayChanged();
                }
            };


    public LockscreenSmartspaceGeneralViewController(LockscreenSmartspaceGeneralView view,
            LockscreenSmartspaceController lockscreenSmartspaceController,
            ConfigurationController configurationController) {
        super(view);
        mLockscreenSmartspaceController = lockscreenSmartspaceController;
        mConfigurationController = configurationController;
    }

    @Override
    protected void onViewAttached() {
        mLockscreenSmartspaceController.addListener(this);
        mConfigurationController.addCallback(mConfigurationListener);
    }

    @Override
    protected void onViewDetached() {
        mLockscreenSmartspaceController.removeListener(this);
        mConfigurationController.removeCallback(mConfigurationListener);
    }

    @Override
    public void onSmartspaceTargetsUpdated(List<? extends Parcelable> targets) {
        var lsgv = new ComponentName(getContext(), LockscreenSmartspaceGeneralView.class);
        for (Parcelable parcelable : targets) {
            if (parcelable instanceof SmartspaceTarget target) {
                if (lsgv.equals(target.getComponentName())) {
                    mView.showTarget(target);
                }
            }
        }
    }
}
