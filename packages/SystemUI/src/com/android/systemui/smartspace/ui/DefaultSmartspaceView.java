package com.android.systemui.smartspace.ui;

import android.os.Handler;

import com.android.systemui.plugins.BcSmartspaceConfigPlugin;
import com.android.systemui.plugins.BcSmartspaceDataPlugin;
import com.android.systemui.plugins.FalsingManager;

/**
 * An extension of SmartspaceView that provides default empty methods to satisfy the interface.
 */
public interface DefaultSmartspaceView extends BcSmartspaceDataPlugin.SmartspaceView {
    @Override
    default void registerDataProvider(BcSmartspaceDataPlugin plugin) {}

    @Override
    default void registerConfigProvider(BcSmartspaceConfigPlugin configProvider) {}

    @Override
    default void setPrimaryTextColor(int color) {}

    @Override
    default void setUiSurface(String uiSurface) {}

    @Override
    default void setBgHandler(Handler bgHandler) {}

    @Override
    default void setDozeAmount(float amount) {}

    @Override
    default void setFalsingManager(FalsingManager falsingManager) {}
}
