package com.ninja.vectorcompat;

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
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;

import com.ninja.vectorcompat.v14.AnimatedVectorDrawable;
import com.ninja.vectorcompat.v14.VectorDrawable;


//TODO: Add tint support compatibility
//TODO: Handling onSaveInstanceState() for MorphState
//TODO: Add methods for choosing which state to be in (with or without animation)
//TODO:
public class MorphButton extends ImageButton implements View.OnClickListener{

    @SuppressWarnings("UnusedDeclaration")
    public static final String TAG = MorphButton.class.getSimpleName();
    public static final boolean LOLLIPOP = Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP;


    private static enum MorphState {
        START,
        END
    }

    MorphState mState = MorphState.END;

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

        //Add dummy onClick listener to intercept clicks for handling animations
        setOnClickListener(this);

        final Resources.Theme theme = context.getTheme();
        TypedArray a = theme.obtainStyledAttributes(attrs, R.styleable.MorphButton, defStyleAttr, 0);

        int startResId = a.getResourceId(R.styleable.MorphButton_morphStartDrawableBackground, -1);
        int endResId = a.getResourceId(R.styleable.MorphButton_morphEndDrawableBackground, -1);
        boolean autoStart = a.getBoolean(R.styleable.MorphButton_autoStartAnimation, false);
        a.recycle();

        if (startResId > 0) {
            mStartMorph = getDrawable(context, startResId);
            mStartCanMorph = isMorphable(mStartMorph);
        }

        if (endResId > 0) {
            mEndMorph = getDrawable(context, endResId);
            mEndCanMorph = isMorphable(mEndMorph);
        }

        setBackgroundDrawable(mStartMorph);
        if (autoStart) {
            beginStartAnimation();
            mState = MorphState.START;
        }
    }

    private boolean isMorphable(Drawable d) {
        return d != null && d instanceof Animatable;
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public Drawable getDrawable(Context c, int resId) {
        Drawable d;
        try {
            if (LOLLIPOP) {
                d = c.getResources().getDrawable(resId, null);
            } else {
                d = c.getResources().getDrawable(resId);
            }
        } catch (Resources.NotFoundException e) {

            try {
                d = VectorDrawable.create(c.getResources(), resId);
            } catch (IllegalArgumentException e1) {

                //We're not a VectorDrawable, try AnimatedVectorDrawable
                try {
                    d = AnimatedVectorDrawable.create(c, c.getResources(), resId);
                } catch (IllegalArgumentException e2) {
                    //Throw NotFoundException
                    throw e;
                }
            }
        }
        return d;
    }

    @Override
    public void setOnClickListener(OnClickListener l) {
        //Make sure we always have an onClick listener to handle animations
        super.setOnClickListener(l==null?this:l);
    }

    @Override
    public void onClick(View view) {
        // dummy
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean performClick() {
        if (mState == MorphState.START) {
            setBackgroundDrawable(mEndMorph);
            endStartAnimation();
            beginEndAnimation();
            mState = MorphState.END;
        } else {
            setBackgroundDrawable(mStartMorph);
            endEndAnimation();
            beginStartAnimation();
            mState = MorphState.START;
        }

        return super.performClick();
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

}
