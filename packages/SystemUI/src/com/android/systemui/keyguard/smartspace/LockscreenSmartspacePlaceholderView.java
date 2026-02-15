package com.android.systemui.keyguard.smartspace;

import android.annotation.IdRes;
import android.content.Context;
import android.widget.LinearLayout;

import com.android.systemui.smartspace.ui.DefaultSmartspaceView;

public class LockscreenSmartspacePlaceholderView extends LinearLayout
        implements DefaultSmartspaceView {

    /** ID must match the relevant ID from
     * @see com.android.systemui.keyguard.ui.view.layout.sections.SmartspaceSection */
    public LockscreenSmartspacePlaceholderView(Context context, @IdRes int id) {
        super(context);
        setId(id);
    }
}
