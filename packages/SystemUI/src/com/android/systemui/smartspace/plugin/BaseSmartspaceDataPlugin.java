package com.android.systemui.smartspace.plugin;

import android.app.smartspace.SmartspaceTarget;

import com.android.systemui.plugins.BcSmartspaceDataPlugin;
import com.android.systemui.util.Assert;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * Simple plugin that handles listener registration/deregistration and dispatching of target
 * updates.
 */
public class BaseSmartspaceDataPlugin implements BcSmartspaceDataPlugin  {
    private final Set<SmartspaceTargetListener> mTargetListeners;

    @Inject
    public BaseSmartspaceDataPlugin() {
        mTargetListeners = new HashSet<>();
    }

    @Override
    public void registerListener(SmartspaceTargetListener listener) {
        Assert.isMainThread();
        mTargetListeners.add(listener);
    }

    @Override
    public void unregisterListener(SmartspaceTargetListener listener) {
        Assert.isMainThread();
        mTargetListeners.remove(listener);
    }

    @Override
    public void setEventDispatcher(SmartspaceEventDispatcher eventDispatcher) {}

    @Override
    public void setIntentStarter(IntentStarter intentStarter) {}

    @Override
    public SmartspaceEventNotifier getEventNotifier() {
        return null;
    }

    @Override
    public void onTargetsAvailable(List<SmartspaceTarget> targets) {
        Assert.isMainThread();
        for (SmartspaceTargetListener listener : mTargetListeners) {
            listener.onSmartspaceTargetsUpdated(targets);
        }
    }
}
