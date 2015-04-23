package com.wnafee.vector;

/*
 * Copyright (C) 2015 Wael Nafee
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.CompoundButton;

import com.wnafee.vector.compat.DrawableCompat;
import com.wnafee.vector.compat.ResourcesCompat;
import com.wnafee.vector.compat.Tintable;


//TODO: Add tint support compatibility
//TODO: attempt reversing animation if no morphEndDrawable is provided
public class MorphButton extends CompoundButton {

    @SuppressWarnings("UnusedDeclaration")
    public static final String TAG = MorphButton.class.getSimpleName();

    public static enum MorphState {
        START,
        END
    }

    public interface OnStateChangedListener {
        public void onStateChanged(MorphState changedTo, boolean isAnimating);
    }

    private static class TintInfo {
        ColorStateList mTintList;
        PorterDuff.Mode mTintMode;
        boolean mHasTintMode;
        boolean mHasTintList;
    }

    TintInfo mBackgroundTint;

    MorphState mState = MorphState.START;

    Drawable mStartMorph = null;
    Drawable mEndMorph = null;

    boolean mStartCanMorph = false;
    boolean mEndCanMorph = false;

    boolean mIsToggling = false;
    boolean mHasStarted = false;

    private OnStateChangedListener mStateListener;

    public MorphButton(Context context) {
        this(context, null);
    }

    public MorphButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.morphButtonStyle);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    public MorphButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final Resources.Theme theme = context.getTheme();
        TypedArray a = theme.obtainStyledAttributes(attrs, R.styleable.MorphButton, defStyleAttr, 0);

        int startResId = a.getResourceId(R.styleable.MorphButton_vc_startDrawable, -1);
        int endResId = a.getResourceId(R.styleable.MorphButton_vc_endDrawable, -1);
        boolean autoStart = a.getBoolean(R.styleable.MorphButton_vc_autoStartAnimation, false);

        mBackgroundTint = new TintInfo();
        mBackgroundTint.mTintList = a.getColorStateList(R.styleable.MorphButton_vc_backgroundTint);
        mBackgroundTint.mHasTintList = mBackgroundTint.mTintList != null;


        mBackgroundTint.mTintMode = DrawableCompat.parseTintMode(a.getInt(
                R.styleable.MorphButton_vc_backgroundTintMode, -1), null);
        mBackgroundTint.mHasTintMode = mBackgroundTint.mTintMode != null;

        a.recycle();

        //Setup default params
        setMinHeight(0);
        setMinWidth(0);
        setClickable(true);

        if (startResId > 0) {
            mStartMorph = ResourcesCompat.getDrawable(context, startResId);
            mStartCanMorph = isMorphable(mStartMorph);
//            setDrawableCallback(mStartMorph);
        }

        if (endResId > 0) {
            mEndMorph = ResourcesCompat.getDrawable(context, endResId);
            mEndCanMorph = isMorphable(mEndMorph);
//            setDrawableCallback(mStartMorph);
        }

        setState(mState);
        if (autoStart) {
            mHasStarted = true;
            setState(MorphState.END, true);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @SuppressWarnings("deprecation")
    @Override
    public void setBackgroundDrawable(Drawable background) {
        if (ResourcesCompat.LOLLIPOP) {
            if (mBackgroundTint != null) {
                // Set tint parameters for superclass View to apply
                if (mBackgroundTint.mHasTintList)
                    super.setBackgroundTintList(mBackgroundTint.mTintList);
                if (mBackgroundTint.mHasTintMode)
                    super.setBackgroundTintMode(mBackgroundTint.mTintMode);
            }
            super.setBackgroundDrawable(background);
        } else {
            super.setBackgroundDrawable(background);

            // Need to apply tint ourselves
            applyBackgroundTint();
        }

    }

    public ColorStateList getBackgroundTintList() {
        if (ResourcesCompat.LOLLIPOP) {
            return getBackgroundTintList();
        }
        return mBackgroundTint != null ? mBackgroundTint.mTintList : null;
    }

    public void setBackgroundTintList(@Nullable ColorStateList tint) {
        if (ResourcesCompat.LOLLIPOP) {
            super.setBackgroundTintList(tint);
        }

        if (mBackgroundTint == null) {
            mBackgroundTint = new TintInfo();
        }
        mBackgroundTint.mTintList = tint;
        mBackgroundTint.mHasTintList = true;

        if (!ResourcesCompat.LOLLIPOP) {
            applyBackgroundTint();
        }
    }

    public PorterDuff.Mode getBackgroundTintMode() {
        if (ResourcesCompat.LOLLIPOP) {
            return getBackgroundTintMode();
        }
        return mBackgroundTint != null ? mBackgroundTint.mTintMode : null;
    }

    public void setBackgroundTintMode(@Nullable PorterDuff.Mode tintMode) {
        if (ResourcesCompat.LOLLIPOP) {
            super.setBackgroundTintMode(tintMode);
        }
        if (mBackgroundTint == null) {
            mBackgroundTint = new TintInfo();
        }
        mBackgroundTint.mTintMode = tintMode;
        mBackgroundTint.mHasTintMode = true;

        if (!ResourcesCompat.LOLLIPOP) {
            applyBackgroundTint();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void applyBackgroundTint() {
        Drawable d = getBackground();
        final TintInfo tintInfo = mBackgroundTint;
        if (d != null && mBackgroundTint != null) {
            if (ResourcesCompat.LOLLIPOP) {
                if (tintInfo.mHasTintList || tintInfo.mHasTintMode) {
                    d = d.mutate();
                    if (tintInfo.mHasTintList) {
                        d.setTintList(tintInfo.mTintList);
                    }
                    if (tintInfo.mHasTintMode) {
                        d.setTintMode(tintInfo.mTintMode);
                    }
                }
            } else if (d instanceof Tintable) {
                // Our VectorDrawable and AnimatedVectorDrawable implementation
                if (tintInfo.mHasTintList || tintInfo.mHasTintMode) {
                    d = d.mutate();
                    Tintable t = (Tintable) d;
                    if (tintInfo.mHasTintList) {
                        t.setTintList(tintInfo.mTintList);
                    }
                    if (tintInfo.mHasTintMode) {
                        t.setTintMode(tintInfo.mTintMode);
                    }
                }
            } else {
                //TODO: Should I attempt to make "stateful" ColorFilters from mBackgroundTint?
                int color = Color.TRANSPARENT;
                if (tintInfo.mHasTintList) {
                    color = tintInfo.mTintList.getColorForState(getDrawableState(), Color.TRANSPARENT);
                }
                setColorFilter(color, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    public void setColorFilter(int color, PorterDuff.Mode mode) {
        if (mStartMorph != null) {
            mStartMorph.setColorFilter(color, mode);
        }

        if (mEndMorph != null) {
            mEndMorph.setColorFilter(color, mode);
        }
    }

    private boolean isMorphable(Drawable d) {
        return d != null && d instanceof Animatable;
    }

    private void setDrawableCallback(Drawable d) {
        if (d != null) {
            d.setCallback(this);
        }
    }

    public void setOnStateChangedListener(OnStateChangedListener l) {

        //Should I invalidate something?
        if (l != null && l != mStateListener) {
            mStateListener = l;
        }
    }

    @Override
    public void toggle() {
        mHasStarted = true;
        mIsToggling = true;
        setState(mState == MorphState.START ? MorphState.END: MorphState.START, true);
        super.toggle();
        mIsToggling = false;
    }

    public void setStartDrawable(int rId) {
        if (rId > 0) {
            setStartDrawable(ResourcesCompat.getDrawable(getContext(), rId));
        }
    }

    public void setStartDrawable(Drawable d) {
        mStartMorph = d;
        mStartCanMorph = isMorphable(d);
//        setDrawableCallback(mStartMorph);
        setState(mState);
    }

    public void setEndDrawable(int rId) {
        if (rId > 0) {
            setEndDrawable(ResourcesCompat.getDrawable(getContext(), rId));
        }
    }

    public void setEndDrawable(Drawable d) {
        mEndMorph = d;
        mEndCanMorph = isMorphable(d);
//        setDrawableCallback(mStartMorph);
        setState(mState);
    }

    public void setAutoStart(boolean autoStart) {
        if (autoStart && !mHasStarted) {
            mHasStarted = true;
            setState(MorphState.END, true);
        }
    }

    private boolean beginStartAnimation() {
        if (mStartMorph != null && mStartCanMorph) {
            ((Animatable) mStartMorph).start();
            return true;
        }
        return false;
    }

    private boolean endStartAnimation() {
        if (mStartMorph != null && mStartCanMorph) {
            ((Animatable) mStartMorph).stop();
            return true;
        }
        return false;
    }

    private boolean beginEndAnimation() {
        if (mEndMorph != null && mEndCanMorph) {
            ((Animatable) mEndMorph).start();
            return true;
        }
        return false;
    }

    private boolean endEndAnimation() {
        if (mEndMorph != null && mEndCanMorph) {
            ((Animatable) mEndMorph).stop();
            return true;
        }
        return false;
    }

    public MorphState getState() {
        return mState;
    }

    /**
     * Same as {@link MorphButton#setState(MorphButton.MorphState, boolean)} with no animation
     *
     * @param state requested state
     */
    public void setState(MorphState state) {
        setState(state, false);
    }

    /**
     * Choose button state
     *
     * @param state   a {@link MorphButton.MorphState} to set button to
     * @param animate should we animated to get to this state or not
     */
    @SuppressWarnings("deprecation")
    public void setState(MorphState state, boolean animate) {
        if (state == MorphState.START) {
            setBackgroundDrawable(mEndCanMorph ? mEndMorph : mStartMorph);
            beginEndAnimation();
            if (!animate) {
                endEndAnimation();
            }
        } else {
            setBackgroundDrawable(mStartCanMorph ? mStartMorph : mEndMorph);
            beginStartAnimation();
            if (!animate) {
                endStartAnimation();
            }
        }

        // Only allow state listners to change if actually changing state
        if (mState == state && mHasStarted) {
            return;
        }

        mState = state;

        if (mStateListener != null) {
            mStateListener.onStateChanged(state, animate);
        }
    }

    @Override
    public void setChecked(boolean checked) {
        if (!mIsToggling) {
            setState(checked ? MorphState.END : MorphState.START);
        }
        super.setChecked(checked);
    }

    static class SavedState extends BaseSavedState {
        MorphState state;

        /**
         * Constructor called from {@link CompoundButton#onSaveInstanceState()}
         */
        SavedState(Parcelable superState) {
            super(superState);
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            state = (MorphState)in.readValue(null);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeValue(state);
        }

        @Override
        public String toString() {
            return "MorphButton.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " state=" + state + "}";
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.state = getState();
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setState(ss.state, false);
        requestLayout();
    }
}
