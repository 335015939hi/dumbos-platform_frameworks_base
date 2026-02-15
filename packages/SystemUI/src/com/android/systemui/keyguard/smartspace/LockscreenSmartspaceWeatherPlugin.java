package com.android.systemui.keyguard.smartspace;

import android.content.Context;

import com.android.systemui.shared.R;
import com.android.systemui.smartspace.plugin.BaseSmartspaceDataPlugin;

import javax.inject.Inject;

/**
 * Weather plugin that provides placeholder views to satisfy lockscreen smartspace constraints.
 */
public class LockscreenSmartspaceWeatherPlugin extends BaseSmartspaceDataPlugin {
    @Inject
    public LockscreenSmartspaceWeatherPlugin() {}

    @Override
    public SmartspaceView getView(Context context) {
        return new LockscreenSmartspacePlaceholderView(context, R.id.weather_smartspace_view);
    }

    @Override
    public SmartspaceView getLargeClockView(Context context) {
        return new LockscreenSmartspacePlaceholderView(context, R.id.weather_smartspace_view_large);
    }
}
