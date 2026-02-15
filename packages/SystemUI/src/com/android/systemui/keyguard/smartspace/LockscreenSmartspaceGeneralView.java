package com.android.systemui.keyguard.smartspace;

import android.annotation.StyleRes;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.uitemplatedata.BaseTemplateData;
import android.app.smartspace.uitemplatedata.CombinedCardsTemplateData;
import android.app.smartspace.uitemplatedata.Icon;
import android.app.smartspace.uitemplatedata.Text;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.graphics.ColorUtils;
import com.android.settingslib.Utils;
import com.android.systemui.res.R;
import com.android.systemui.smartspace.ui.DefaultSmartspaceView;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Smartspace view for displaying date, zen mode, upcoming alarms and media. This is a
 * reimplementation of the legacy KeyguardSliceView.
 */
public class LockscreenSmartspaceGeneralView extends LinearLayout implements DefaultSmartspaceView {
    private Rows mRows;
    private final int mIconSize;

    public LockscreenSmartspaceGeneralView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mIconSize = (int) mContext.getResources().getDimension(R.dimen.widget_icon_size);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mRows = findViewById(R.id.rows);
    }

    protected void showTarget(SmartspaceTarget target) {
        var template = (CombinedCardsTemplateData) requireNonNull(target.getTemplateData());
        List<BaseTemplateData> subTemplates = template.getCombinedCardDataList();

        if (subTemplates.isEmpty()) {
            mRows.setVisibility(View.GONE);
            return;
        }

        mRows.setVisibility(View.VISIBLE);

        int viewIdx = 0;

        final int textColor = ColorUtils.blendARGB(
                Utils.getColorAttrDefaultColor(mContext, R.attr.wallpaperTextColor),
                Color.WHITE, 0.0f);

        for (BaseTemplateData data : subTemplates) {
            BaseTemplateData.SubItemInfo primaryItem = data.getPrimaryItem();
            if (primaryItem != null) {
                addRow(primaryItem, textColor, viewIdx++);
                BaseTemplateData.SubItemInfo subTitleItem = data.getSubtitleItem();
                if (subTitleItem != null) {
                    addRow(subTitleItem, textColor, viewIdx++);
                }
            }
        }

        int rowCount = mRows.getChildCount();
        if (viewIdx != rowCount) {
            if (viewIdx > rowCount) {
                throw new IllegalStateException();
            }
            mRows.removeViews(viewIdx, rowCount - viewIdx);
        }
    }

    private void addRow(BaseTemplateData.SubItemInfo subItemInfo, int textColor, int viewIdx) {
        LockscreenSmartspaceGeneralTextView tv;
        boolean isCachedView = viewIdx < mRows.getChildCount();
        if (isCachedView) {
            tv = (LockscreenSmartspaceGeneralTextView) mRows.getChildAt(viewIdx);
        } else {
            tv = new LockscreenSmartspaceGeneralTextView(mContext);
        }
        tv.setTextColor(textColor);

        Text text = subItemInfo.getText();
        tv.setText(text == null ? null : text.getText());

        Drawable iconDrawable = null;
        Icon icon = subItemInfo.getIcon();
        if (icon != null) {
            iconDrawable = icon.getIcon().loadDrawable(mContext);
            if (iconDrawable != null) {
                if (iconDrawable instanceof InsetDrawable insetDrawable) {
                    // System icons (DnD) use insets which are fine for centered slice content
                    // but will cause a slight indent for left/right-aligned slice views.
                    iconDrawable = requireNonNull(insetDrawable.getDrawable());
                }
                final int width = (int) (iconDrawable.getIntrinsicWidth()
                        / (float) iconDrawable.getIntrinsicHeight() * mIconSize);
                iconDrawable.setBounds(0, 0, Math.max(width, 1), mIconSize);
            }
        }
        tv.setCompoundDrawablesRelative(iconDrawable, null, null, null);

        if (!isCachedView) {
            mRows.addView(tv);
        }
    }

    void onDensityOrFontScaleChanged() {
        for (int i = 0; i < mRows.getChildCount(); i++) {
            View child = mRows.getChildAt(i);
            if (child instanceof LockscreenSmartspaceGeneralTextView text) {
                text.onDensityOrFontScaleChanged();
            }
        }
    }

    void onOverlayChanged() {
        for (int i = 0; i < mRows.getChildCount(); i++) {
            View child = mRows.getChildAt(i);
            if (child instanceof LockscreenSmartspaceGeneralTextView text) {
                text.onOverlayChanged();
            }
        }
    }

    /** based on {@link com.android.keyguard.KeyguardSliceView.Row} */
    public static class Rows extends LinearLayout {

        public Rows(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public Rows(Context context, AttributeSet attrs, int defStyleAttr) {
            this(context, attrs, defStyleAttr, 0);
        }

        public Rows(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
            super(context, attrs, defStyleAttr, defStyleRes);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof LockscreenSmartspaceGeneralTextView text) {
                    text.setMaxWidth(Integer.MAX_VALUE);
                }
            }

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    /** copied from {@link com.android.keyguard.KeyguardSliceView.KeyguardSliceTextView} */
    static class LockscreenSmartspaceGeneralTextView extends TextView {

        @StyleRes
        private static final int sStyleId = R.style.TextAppearance_Keyguard_Secondary;

        LockscreenSmartspaceGeneralTextView(Context context) {
            super(context, null /* attrs */, 0 /* styleAttr */, sStyleId);
            onDensityOrFontScaleChanged();
            setEllipsize(TextUtils.TruncateAt.END);
        }

        public void onDensityOrFontScaleChanged() {
            updatePadding();
        }

        public void onOverlayChanged() {
            setTextAppearance(sStyleId);
        }

        @Override
        public void setText(CharSequence text, BufferType type) {
            super.setText(text, type);
            updatePadding();
        }

        private void updatePadding() {
            boolean hasText = !TextUtils.isEmpty(getText());
            int padding = (int) getContext().getResources()
                    .getDimension(R.dimen.widget_horizontal_padding) / 2;
            // Orientation is vertical, so add padding to top and bottom.
            setPadding(0, padding, 0, hasText ? padding : 0);

            setCompoundDrawablePadding((int) mContext.getResources()
                    .getDimension(R.dimen.widget_icon_padding));
        }

        @Override
        public void setTextColor(int color) {
            super.setTextColor(color);
            updateDrawableColors();
        }

        @Override
        public void setCompoundDrawablesRelative(Drawable start, Drawable top, Drawable end,
                Drawable bottom) {
            super.setCompoundDrawablesRelative(start, top, end, bottom);
            updateDrawableColors();
            updatePadding();
        }

        private void updateDrawableColors() {
            final int color = getCurrentTextColor();
            for (Drawable drawable : getCompoundDrawables()) {
                if (drawable != null) {
                    drawable.setTint(color);
                }
            }
        }
    }
}
