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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.widget.CompoundButton;

import com.wnafee.vector.compat.AnimatedVectorDrawable;
import com.wnafee.vector.compat.VectorDrawable;


//TODO: Add tint support compatibility
//TODO: attempt reversing animation if no morphEndDrawable is provided
public class MorphButton extends CompoundButton {

    @SuppressWarnings("UnusedDeclaration")
    public static final String TAG = MorphButton.class.getSimpleName();
    public static final boolean LOLLIPOP = Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP;

    public static enum MorphState {
        START,
        END
    }

    MorphState mState = MorphState.START;

    Drawable mStartMorph = null;
    Drawable mEndMorph = null;

    boolean mStartCanMorph = false;
    boolean mEndCanMorph = false;

    public MorphButton(Context context) {
        this(context, null);
    }

    public MorphButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.morphButtonStyle);
    }

    @SuppressWarnings("deprecation")
    public MorphButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final Resources.Theme theme = context.getTheme();
        TypedArray a = theme.obtainStyledAttributes(attrs, R.styleable.MorphButton, defStyleAttr, 0);

        int startResId = a.getResourceId(R.styleable.MorphButton_morphStartDrawable, -1);
        int endResId = a.getResourceId(R.styleable.MorphButton_morphEndDrawable, -1);
        boolean autoStart = a.getBoolean(R.styleable.MorphButton_autoStartAnimation, false);
        a.recycle();

        //Setup default params
        setMinHeight(0);
        setMinWidth(0);
        setClickable(true);

        if (startResId > 0) {
            mStartMorph = getDrawable(context, startResId);
            mStartCanMorph = isMorphable(mStartMorph);
        }

        if (endResId > 0) {
            mEndMorph = getDrawable(context, endResId);
            mEndCanMorph = isMorphable(mEndMorph);
        }

        setState(mState);
        if (autoStart) {
            setState(MorphState.END, true);
        }
    }

    private boolean isMorphable(Drawable d) {
        return d != null && d instanceof Animatable;
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static Drawable getDrawable(Context c, int resId) {
        Drawable d;
        try {
            if (LOLLIPOP) {
                d = c.getResources().getDrawable(resId, null);
            } else {
                d = c.getResources().getDrawable(resId);
            }
        } catch (Resources.NotFoundException e) {

            try {
                d = VectorDrawable.getDrawable(c, resId);
            } catch (IllegalArgumentException e1) {

                //We're not a VectorDrawable, try AnimatedVectorDrawable
                try {
                    d = AnimatedVectorDrawable.getDrawable(c, resId);
                } catch (IllegalArgumentException e2) {
                    //Throw NotFoundException
                    throw e;
                }
            }
        }
        return d;
    }

    @Override
    public void toggle() {
        setState(mState == MorphState.START ? MorphState.END: MorphState.START, true);
        super.toggle();
    }

    public boolean beginStartAnimation() {
        if (mStartMorph != null && mStartCanMorph) {
            ((Animatable) mStartMorph).start();
            return true;
        }
        return false;
    }

    public boolean endStartAnimation() {
        if (mStartMorph != null && mStartCanMorph) {
            ((Animatable) mStartMorph).stop();
            return true;
        }
        return false;
    }

    public boolean beginEndAnimation() {
        if (mEndMorph != null && mEndCanMorph) {
            ((Animatable) mEndMorph).start();
            return true;
        }
        return false;
    }

    public boolean endEndAnimation() {
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

        mState = state;
    }

    @Override
    public void setChecked(boolean checked) {
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
