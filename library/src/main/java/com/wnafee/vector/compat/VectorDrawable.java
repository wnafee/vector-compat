package com.wnafee.vector.compat;

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

import com.wnafee.vector.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Stack;

//TODO: Add theme support - canApplyTheme() and applyTheme()
public class VectorDrawable extends DrawableCompat implements Tintable{
    private static final String LOGTAG = VectorDrawable.class.getSimpleName();

    static final Mode DEFAULT_TINT_MODE = Mode.SRC_IN;

    private static final String SHAPE_CLIP_PATH = "clip-path";
    private static final String SHAPE_GROUP = "group";
    private static final String SHAPE_PATH = "path";
    private static final String SHAPE_VECTOR = "vector";

    private static final int LINECAP_BUTT = 0;
    private static final int LINECAP_ROUND = 1;
    private static final int LINECAP_SQUARE = 2;

    private static final int LINEJOIN_MITER = 0;
    private static final int LINEJOIN_ROUND = 1;
    private static final int LINEJOIN_BEVEL = 2;

    private static final boolean DBG_VECTOR_DRAWABLE = false;

    private VectorDrawableState mVectorState;

    private PorterDuffColorFilter mTintFilter;
    private ColorFilter mColorFilter;

    private boolean mMutated;

    // AnimatedVectorDrawable needs to turn off the cache all the time, otherwise,
    // caching the bitmap by default is allowed.
    private boolean mAllowCaching = true;

    public VectorDrawable() {
        mVectorState = new VectorDrawableState();
    }

    private VectorDrawable(VectorDrawableState state, Resources res, Theme theme) {
        if (theme != null && state.canApplyTheme()) {
            // If we need to apply a theme, implicitly mutate.
            mVectorState = new VectorDrawableState(state);
            applyTheme(theme);
        } else {
            mVectorState = state;
        }

        mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
    }

    @Override
    public Drawable mutate() {
        if (!mMutated && super.mutate() == this) {
            mVectorState = new VectorDrawableState(mVectorState);
            mMutated = true;
        }
        return this;
    }

    Object getTargetByName(String name) {
        return mVectorState.mVPathRenderer.mVGTargetsMap.get(name);
    }

    @Override
    public Drawable.ConstantState getConstantState() {
        mVectorState.mChangingConfigurations = getChangingConfigurations();
        return mVectorState;
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect bounds = getBounds();
        if (bounds.width() == 0 || bounds.height() == 0) {
            // too small to draw
            return;
        }

        final int saveCount = canvas.save();
        final boolean needMirroring = needMirroring();

        canvas.translate(bounds.left, bounds.top);
        if (needMirroring) {
            canvas.translate(bounds.width(), 0);
            canvas.scale(-1.0f, 1.0f);
        }

        // Color filters always override tint filters.
        final ColorFilter colorFilter = mColorFilter == null ? mTintFilter : mColorFilter;

        if (!mAllowCaching) {
            // AnimatedVectorDrawable
            if (!mVectorState.hasTranslucentRoot()) {
                mVectorState.mVPathRenderer.draw(
                        canvas, bounds.width(), bounds.height(), colorFilter);
            } else {
                mVectorState.createCachedBitmapIfNeeded(bounds);
                mVectorState.updateCachedBitmap(bounds);
                mVectorState.drawCachedBitmapWithRootAlpha(canvas, colorFilter);
            }
        } else {
            // Static Vector Drawable case.
            mVectorState.createCachedBitmapIfNeeded(bounds);
            if (!mVectorState.canReuseCache()) {
                mVectorState.updateCachedBitmap(bounds);
                mVectorState.updateCacheStates();
            }
            mVectorState.drawCachedBitmapWithRootAlpha(canvas, colorFilter);
        }

        canvas.restoreToCount(saveCount);
    }

    @Override
    public int getAlpha() {
        return mVectorState.mVPathRenderer.getRootAlpha();
    }

    @Override
    public void setAlpha(int alpha) {
        if (mVectorState.mVPathRenderer.getRootAlpha() != alpha) {
            mVectorState.mVPathRenderer.setRootAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mColorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public void setTintList(ColorStateList tint) {
        final VectorDrawableState state = mVectorState;
        if (state.mTint != tint) {
            state.mTint = tint;
            mTintFilter = updateTintFilter(mTintFilter, tint, state.mTintMode);
            invalidateSelf();
        }
    }

    @Override
    public void setTintMode(Mode tintMode) {
        final VectorDrawableState state = mVectorState;
        if (state.mTintMode != tintMode) {
            state.mTintMode = tintMode;
            mTintFilter = updateTintFilter(mTintFilter, state.mTint, tintMode);
            invalidateSelf();
        }
    }

    @Override
    public boolean isStateful() {
        return super.isStateful() || (mVectorState != null && mVectorState.mTint != null
                && mVectorState.mTint.isStateful());
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        final VectorDrawableState state = mVectorState;
        if (state.mTint != null && state.mTintMode != null) {
            mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) mVectorState.mVPathRenderer.mBaseWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) mVectorState.mVPathRenderer.mBaseHeight;
    }

    @Override
    public boolean canApplyTheme() {
        return super.canApplyTheme() || mVectorState != null && mVectorState.canApplyTheme();
    }

    @Override
    public void applyTheme(Theme t) {
        super.applyTheme(t);

        final VectorDrawableState state = mVectorState;
        if (state != null && state.mThemeAttrs != null) {
            //TODO
            final TypedArray a = null;//t.resolveAttributes(state.mThemeAttrs, R.styleable.VectorDrawable);
            try {
                state.mCacheDirty = true;
                updateStateFromTypedArray(a);
            } catch (XmlPullParserException e) {
                throw new RuntimeException(e);
            } finally {
                a.recycle();
            }

            mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
        }

        final VPathRenderer path = state.mVPathRenderer;
        if (path != null && path.canApplyTheme()) {
            path.applyTheme(t);
        }
    }

    /**
     * The size of a pixel when scaled from the intrinsic dimension to the viewport dimension.
     * This is used to calculate the path animation accuracy.
     */
    public float getPixelSize() {
        if (mVectorState == null && mVectorState.mVPathRenderer == null ||
                mVectorState.mVPathRenderer.mBaseWidth == 0 ||
                mVectorState.mVPathRenderer.mBaseHeight == 0 ||
                mVectorState.mVPathRenderer.mViewportHeight == 0 ||
                mVectorState.mVPathRenderer.mViewportWidth == 0) {
            return 1; // fall back to 1:1 pixel mapping.
        }
        float intrinsicWidth = mVectorState.mVPathRenderer.mBaseWidth;
        float intrinsicHeight = mVectorState.mVPathRenderer.mBaseHeight;
        float viewportWidth = mVectorState.mVPathRenderer.mViewportWidth;
        float viewportHeight = mVectorState.mVPathRenderer.mViewportHeight;
        float scaleX = viewportWidth / intrinsicWidth;
        float scaleY = viewportHeight / intrinsicHeight;
        return Math.min(scaleX, scaleY);
    }

    public static VectorDrawable  getDrawable(Context c, int resId) {
        return create(c.getResources(), resId);
    }

    public static VectorDrawable create(Resources resources, int rid) {
        try {
            final XmlPullParser parser = resources.getXml(rid);
            final AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG &&
                    type != XmlPullParser.END_DOCUMENT) {
                // Empty loop
            }
            if (type != XmlPullParser.START_TAG) {
                throw new XmlPullParserException("No start tag found");
            } else if (!SHAPE_VECTOR.equals(parser.getName())) {
                throw new IllegalArgumentException("root node must start with: " + SHAPE_VECTOR);
            }

            final VectorDrawable drawable = new VectorDrawable();
            drawable.inflate(resources, parser, attrs, null);

            return drawable;
        } catch (XmlPullParserException e) {
            Log.e(LOGTAG, "parser error", e);
        } catch (IOException e) {
            Log.e(LOGTAG, "parser error", e);
        }
        return null;
    }

    private static int applyAlpha(int color, float alpha) {
        int alphaBytes = Color.alpha(color);
        color &= 0x00FFFFFF;
        color |= ((int) (alphaBytes * alpha)) << 24;
        return color;
    }

    @Override
    public void inflate(Resources res, XmlPullParser parser, AttributeSet attrs, Theme theme)
            throws XmlPullParserException, IOException {
        final VectorDrawableState state = mVectorState;
        final VPathRenderer pathRenderer = new VPathRenderer();
        state.mVPathRenderer = pathRenderer;

        final TypedArray a = obtainAttributes(res, theme, attrs, R.styleable.VectorDrawable);
        updateStateFromTypedArray(a);
        a.recycle();

        state.mCacheDirty = true;
        inflateInternal(res, parser, attrs, theme);

        mTintFilter = updateTintFilter(mTintFilter, state.mTint, state.mTintMode);
    }

    private void updateStateFromTypedArray(TypedArray a) throws XmlPullParserException {
        final VectorDrawableState state = mVectorState;
        final VPathRenderer pathRenderer = state.mVPathRenderer;

        // Account for any configuration changes.
        state.mChangingConfigurations |= getChangingConfigurations(a);

        // Extract the theme attributes, if any.
        //TODO: will not support drawable theming yet (applies to tinting mainly)
        //state.mThemeAttrs = a.extractThemeAttrs();

        final int tintMode = a.getInt(R.styleable.VectorDrawable_vc_tintMode, -1);
        if (tintMode != -1) {
            state.mTintMode = parseTintMode(tintMode, Mode.SRC_IN);
        }

        final ColorStateList tint = a.getColorStateList(R.styleable.VectorDrawable_vc_tint);
        if (tint != null) {
            state.mTint = tint;
        }

        state.mAutoMirrored = a.getBoolean(
                R.styleable.VectorDrawable_vc_autoMirrored, state.mAutoMirrored);

        pathRenderer.mViewportWidth = a.getFloat(
                R.styleable.VectorDrawable_vc_viewportWidth, pathRenderer.mViewportWidth);
        pathRenderer.mViewportHeight = a.getFloat(
                R.styleable.VectorDrawable_vc_viewportHeight, pathRenderer.mViewportHeight);

        if (pathRenderer.mViewportWidth <= 0) {
            throw new XmlPullParserException(a.getPositionDescription() +
                    "<menu_vector> tag requires viewportWidth > 0");
        } else if (pathRenderer.mViewportHeight <= 0) {
            throw new XmlPullParserException(a.getPositionDescription() +
                    "<menu_vector> tag requires viewportHeight > 0");
        }

        pathRenderer.mBaseWidth = a.getDimension(
                R.styleable.VectorDrawable_android_width, pathRenderer.mBaseWidth);
        pathRenderer.mBaseHeight = a.getDimension(
                R.styleable.VectorDrawable_android_height, pathRenderer.mBaseHeight);

        if (pathRenderer.mBaseWidth <= 0) {
            throw new XmlPullParserException(a.getPositionDescription() +
                    "<menu_vector> tag requires width > 0");
        } else if (pathRenderer.mBaseHeight <= 0) {
            throw new XmlPullParserException(a.getPositionDescription() +
                    "<menu_vector> tag requires height > 0");
        }

        final float alphaInFloat = a.getFloat(R.styleable.VectorDrawable_android_alpha,
                pathRenderer.getAlpha());
        pathRenderer.setAlpha(alphaInFloat);

        final String name = a.getString(R.styleable.VectorDrawable_android_name);
        if (name != null) {
            pathRenderer.mRootName = name;
            pathRenderer.mVGTargetsMap.put(name, pathRenderer);
        }
    }

    private void inflateInternal(Resources res, XmlPullParser parser, AttributeSet attrs,
                                 Theme theme) throws XmlPullParserException, IOException {
        final VectorDrawableState state = mVectorState;
        final VPathRenderer pathRenderer = state.mVPathRenderer;
        boolean noPathTag = true;

        // Use a stack to help to build the group tree.
        // The top of the stack is always the current group.
        final Stack<VGroup> groupStack = new Stack<VGroup>();
        groupStack.push(pathRenderer.mRootGroup);

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                final String tagName = parser.getName();
                final VGroup currentGroup = groupStack.peek();

                if (SHAPE_PATH.equals(tagName)) {
                    final VFullPath path = new VFullPath();
                    path.inflate(res, attrs, theme);
                    currentGroup.mChildren.add(path);
                    if (path.getPathName() != null) {
                        pathRenderer.mVGTargetsMap.put(path.getPathName(), path);
                    }
                    noPathTag = false;
                    state.mChangingConfigurations |= path.mChangingConfigurations;
                } else if (SHAPE_CLIP_PATH.equals(tagName)) {
                    final VClipPath path = new VClipPath();
                    path.inflate(res, attrs, theme);
                    currentGroup.mChildren.add(path);
                    if (path.getPathName() != null) {
                        pathRenderer.mVGTargetsMap.put(path.getPathName(), path);
                    }
                    state.mChangingConfigurations |= path.mChangingConfigurations;
                } else if (SHAPE_GROUP.equals(tagName)) {
                    VGroup newChildGroup = new VGroup();
                    newChildGroup.inflate(res, attrs, theme);
                    currentGroup.mChildren.add(newChildGroup);
                    groupStack.push(newChildGroup);
                    if (newChildGroup.getGroupName() != null) {
                        pathRenderer.mVGTargetsMap.put(newChildGroup.getGroupName(),
                                newChildGroup);
                    }
                    state.mChangingConfigurations |= newChildGroup.mChangingConfigurations;
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                final String tagName = parser.getName();
                if (SHAPE_GROUP.equals(tagName)) {
                    groupStack.pop();
                }
            }
            eventType = parser.next();
        }

        // Print the tree out for debug.
        if (DBG_VECTOR_DRAWABLE) {
            printGroupTree(pathRenderer.mRootGroup, 0);
        }

        if (noPathTag) {
            final StringBuffer tag = new StringBuffer();

            if (tag.length() > 0) {
                tag.append(" or ");
            }
            tag.append(SHAPE_PATH);

            throw new XmlPullParserException("no " + tag + " defined");
        }
    }

    public static int getChangingConfigurations(TypedArray a) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
            return a.getChangingConfigurations();
        }
        return 0;
    }

    private void printGroupTree(VGroup currentGroup, int level) {
        String indent = "";
        for (int i = 0; i < level; i++) {
            indent += "    ";
        }
        // Print the current node
        Log.v(LOGTAG, indent + "current group is :" + currentGroup.getGroupName()
                + " rotation is " + currentGroup.mRotate);
        Log.v(LOGTAG, indent + "matrix is :" + currentGroup.getLocalMatrix().toString());
        // Then print all the children groups
        for (int i = 0; i < currentGroup.mChildren.size(); i++) {
            Object child = currentGroup.mChildren.get(i);
            if (child instanceof VGroup) {
                printGroupTree((VGroup) child, level + 1);
            }
        }
    }

    @Override
    public int getChangingConfigurations() {
        return super.getChangingConfigurations() | mVectorState.mChangingConfigurations;
    }

    void setAllowCaching(boolean allowCaching) {
        mAllowCaching = allowCaching;
    }

    private boolean needMirroring() {
        return isAutoMirrored() && getSupportLayoutDirection() == 1; // 1 is for LayoutDirection.RTL
    }

    @Override
    public void setAutoMirrored(boolean mirrored) {
        if (mVectorState.mAutoMirrored != mirrored) {
            mVectorState.mAutoMirrored = mirrored;
            invalidateSelf();
        }
    }

    @Override
    public boolean isAutoMirrored() {
        return mVectorState.mAutoMirrored;
    }

    private static class VectorDrawableState extends ConstantStateCompat {
        int[] mThemeAttrs;
        int mChangingConfigurations;
        VPathRenderer mVPathRenderer;
        ColorStateList mTint = null;
        Mode mTintMode = DEFAULT_TINT_MODE;
        boolean mAutoMirrored;

        Bitmap mCachedBitmap;
        int[] mCachedThemeAttrs;
        ColorStateList mCachedTint;
        Mode mCachedTintMode;
        int mCachedRootAlpha;
        boolean mCachedAutoMirrored;
        boolean mCacheDirty;

        /**
         * Temporary paint object used to draw cached bitmaps.
         */
        Paint mTempPaint;

        // Deep copy for mutate() or implicitly mutate.
        public VectorDrawableState(VectorDrawableState copy) {
            if (copy != null) {
                mThemeAttrs = copy.mThemeAttrs;
                mChangingConfigurations = copy.mChangingConfigurations;
                mVPathRenderer = new VPathRenderer(copy.mVPathRenderer);
                if (copy.mVPathRenderer.mFillPaint != null) {
                    mVPathRenderer.mFillPaint = new Paint(copy.mVPathRenderer.mFillPaint);
                }
                if (copy.mVPathRenderer.mStrokePaint != null) {
                    mVPathRenderer.mStrokePaint = new Paint(copy.mVPathRenderer.mStrokePaint);
                }
                mTint = copy.mTint;
                mTintMode = copy.mTintMode;
                mAutoMirrored = copy.mAutoMirrored;
            }
        }

        public void drawCachedBitmapWithRootAlpha(Canvas canvas, ColorFilter filter) {
            // The bitmap's size is the same as the bounds.
            final Paint p = getPaint(filter);
            canvas.drawBitmap(mCachedBitmap, 0, 0, p);
        }

        public boolean hasTranslucentRoot() {
            return mVPathRenderer.getRootAlpha() < 255;
        }

        /**
         * @return null when there is no need for alpha paint.
         */
        public Paint getPaint(ColorFilter filter) {
            if (!hasTranslucentRoot() && filter == null) {
                return null;
            }

            if (mTempPaint == null) {
                mTempPaint = new Paint();
                mTempPaint.setFilterBitmap(true);
            }
            mTempPaint.setAlpha(mVPathRenderer.getRootAlpha());
            mTempPaint.setColorFilter(filter);
            return mTempPaint;
        }

        public void updateCachedBitmap(Rect bounds) {
            mCachedBitmap.eraseColor(Color.TRANSPARENT);
            Canvas tmpCanvas = new Canvas(mCachedBitmap);
            mVPathRenderer.draw(tmpCanvas, bounds.width(), bounds.height(), null);
        }

        public void createCachedBitmapIfNeeded(Rect bounds) {
            if (mCachedBitmap == null || !canReuseBitmap(bounds.width(),
                    bounds.height())) {
                mCachedBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(),
                        Bitmap.Config.ARGB_8888);
                mCacheDirty = true;
            }

        }

        public boolean canReuseBitmap(int width, int height) {
            if (width == mCachedBitmap.getWidth()
                    && height == mCachedBitmap.getHeight()) {
                return true;
            }
            return false;
        }

        public boolean canReuseCache() {
            if (!mCacheDirty
                    && mCachedThemeAttrs == mThemeAttrs
                    && mCachedTint == mTint
                    && mCachedTintMode == mTintMode
                    && mCachedAutoMirrored == mAutoMirrored
                    && mCachedRootAlpha == mVPathRenderer.getRootAlpha()) {
                return true;
            }
            return false;
        }

        public void updateCacheStates() {
            // Use shallow copy here and shallow comparison in canReuseCache(),
            // likely hit cache miss more, but practically not much difference.
            mCachedThemeAttrs = mThemeAttrs;
            mCachedTint = mTint;
            mCachedTintMode = mTintMode;
            mCachedRootAlpha = mVPathRenderer.getRootAlpha();
            mCachedAutoMirrored = mAutoMirrored;
            mCacheDirty = false;
        }

        @Override
        public boolean canApplyTheme() {
            return super.canApplyTheme() || mThemeAttrs != null
                    || (mVPathRenderer != null && mVPathRenderer.canApplyTheme());
        }

        public VectorDrawableState() {
            mVPathRenderer = new VPathRenderer();
        }

        @Override
        public Drawable newDrawable() {
            return new VectorDrawable(this, null, null);
        }

        @Override
        public Drawable newDrawable(Resources res) {
            return new VectorDrawable(this, res, null);
        }

        @Override
        public Drawable newDrawable(Resources res, Theme theme) {
            return new VectorDrawable(this, res, theme);
        }

        @Override
        public int getChangingConfigurations() {
            return mChangingConfigurations;
        }
    }

    private static class VPathRenderer {
        /* Right now the internal data structure is organized as a tree.
         * Each node can be a group node, or a path.
         * A group node can have groups or paths as children, but a path node has
         * no children.
         * One example can be:
         *                 Root Group
         *                /    |     \
         *           Group    Path    Group
         *          /     \             |
         *         Path   Path         Path
         *
         */
        // Variables that only used temporarily inside the draw() call, so there
        // is no need for deep copying.
        private final Path mPath;
        private final Path mRenderPath;
        private static final Matrix IDENTITY_MATRIX = new Matrix();
        private final Matrix mFinalPathMatrix = new Matrix();

        private Paint mStrokePaint;
        private Paint mFillPaint;
        private PathMeasure mPathMeasure;

        /////////////////////////////////////////////////////
        // Variables below need to be copied (deep copy if applicable) for mutation.
        private int mChangingConfigurations;
        private final VGroup mRootGroup;
        float mBaseWidth = 0;
        float mBaseHeight = 0;
        float mViewportWidth = 0;
        float mViewportHeight = 0;
        int mRootAlpha = 0xFF;
        String mRootName = null;

        final ArrayMap<String, Object> mVGTargetsMap = new ArrayMap<String, Object>();

        public VPathRenderer() {
            mRootGroup = new VGroup();
            mPath = new Path();
            mRenderPath = new Path();
        }

        public void setRootAlpha(int alpha) {
            mRootAlpha = alpha;
        }

        public int getRootAlpha() {
            return mRootAlpha;
        }

        // setAlpha() and getAlpha() are used mostly for animation purpose, since
        // Animator like to use alpha from 0 to 1.
        public void setAlpha(float alpha) {
            setRootAlpha((int) (alpha * 255));
        }

        @SuppressWarnings("unused")
        public float getAlpha() {
            return getRootAlpha() / 255.0f;
        }

        public VPathRenderer(VPathRenderer copy) {
            mRootGroup = new VGroup(copy.mRootGroup, mVGTargetsMap);
            mPath = new Path(copy.mPath);
            mRenderPath = new Path(copy.mRenderPath);
            mBaseWidth = copy.mBaseWidth;
            mBaseHeight = copy.mBaseHeight;
            mViewportWidth = copy.mViewportWidth;
            mViewportHeight = copy.mViewportHeight;
            mChangingConfigurations = copy.mChangingConfigurations;
            mRootAlpha = copy.mRootAlpha;
            mRootName = copy.mRootName;
            if (copy.mRootName != null) {
                mVGTargetsMap.put(copy.mRootName, this);
            }
        }

        public boolean canApplyTheme() {
            // If one of the paths can apply theme, then return true;
            return recursiveCanApplyTheme(mRootGroup);
        }

        private boolean recursiveCanApplyTheme(VGroup currentGroup) {
            // We can do a tree traverse here, if there is one path return true,
            // then we return true for the whole tree.
            final ArrayList<Object> children = currentGroup.mChildren;

            for (int i = 0; i < children.size(); i++) {
                Object child = children.get(i);
                if (child instanceof VGroup) {
                    VGroup childGroup = (VGroup) child;
                    if (childGroup.canApplyTheme()
                            || recursiveCanApplyTheme(childGroup)) {
                        return true;
                    }
                } else if (child instanceof VPath) {
                    VPath childPath = (VPath) child;
                    if (childPath.canApplyTheme()) {
                        return true;
                    }
                }
            }
            return false;
        }

        public void applyTheme(Theme t) {
            // Apply theme to every path of the tree.
            recursiveApplyTheme(mRootGroup, t);
        }

        private void recursiveApplyTheme(VGroup currentGroup, Theme t) {
            // We can do a tree traverse here, apply theme to all paths which
            // can apply theme.
            final ArrayList<Object> children = currentGroup.mChildren;
            for (int i = 0; i < children.size(); i++) {
                Object child = children.get(i);
                if (child instanceof VGroup) {
                    VGroup childGroup = (VGroup) child;
                    if (childGroup.canApplyTheme()) {
                        childGroup.applyTheme(t);
                    }
                    recursiveApplyTheme(childGroup, t);
                } else if (child instanceof VPath) {
                    VPath childPath = (VPath) child;
                    if (childPath.canApplyTheme()) {
                        childPath.applyTheme(t);
                    }
                }
            }
        }

        private void drawGroupTree(VGroup currentGroup, Matrix currentMatrix,
                                   Canvas canvas, int w, int h, ColorFilter filter) {
            // Calculate current group's matrix by preConcat the parent's and
            // and the current one on the top of the stack.
            // Basically the Mfinal = Mviewport * M0 * M1 * M2;
            // Mi the local matrix at level i of the group tree.
            currentGroup.mStackedMatrix.set(currentMatrix);

            currentGroup.mStackedMatrix.preConcat(currentGroup.mLocalMatrix);

            // Draw the group tree in the same order as the XML file.
            for (int i = 0; i < currentGroup.mChildren.size(); i++) {
                Object child = currentGroup.mChildren.get(i);
                if (child instanceof VGroup) {
                    VGroup childGroup = (VGroup) child;
                    drawGroupTree(childGroup, currentGroup.mStackedMatrix,
                            canvas, w, h, filter);
                } else if (child instanceof VPath) {
                    VPath childPath = (VPath) child;
                    drawPath(currentGroup, childPath, canvas, w, h, filter);
                }
            }
        }

        public void draw(Canvas canvas, int w, int h, ColorFilter filter) {
            // Travese the tree in pre-order to draw.
            drawGroupTree(mRootGroup, IDENTITY_MATRIX, canvas, w, h, filter);
        }

        private void drawPath(VGroup vGroup, VPath vPath, Canvas canvas, int w, int h,
                              ColorFilter filter) {
            final float scaleX = w / mViewportWidth;
            final float scaleY = h / mViewportHeight;
            final float minScale = Math.min(scaleX, scaleY);

            mFinalPathMatrix.set(vGroup.mStackedMatrix);
            mFinalPathMatrix.postScale(scaleX, scaleY);

            vPath.toPath(mPath);
            final Path path = mPath;

            mRenderPath.reset();

            if (vPath.isClipPath()) {
                mRenderPath.addPath(path, mFinalPathMatrix);
                canvas.clipPath(mRenderPath, Region.Op.REPLACE);
            } else {
                VFullPath fullPath = (VFullPath) vPath;
                if (fullPath.mTrimPathStart != 0.0f || fullPath.mTrimPathEnd != 1.0f) {
                    float start = (fullPath.mTrimPathStart + fullPath.mTrimPathOffset) % 1.0f;
                    float end = (fullPath.mTrimPathEnd + fullPath.mTrimPathOffset) % 1.0f;

                    if (mPathMeasure == null) {
                        mPathMeasure = new PathMeasure();
                    }
                    mPathMeasure.setPath(mPath, false);

                    float len = mPathMeasure.getLength();
                    start = start * len;
                    end = end * len;
                    path.reset();
                    if (start > end) {
                        mPathMeasure.getSegment(start, len, path, true);
                        mPathMeasure.getSegment(0f, end, path, true);
                    } else {
                        mPathMeasure.getSegment(start, end, path, true);
                    }
                    path.rLineTo(0, 0); // fix bug in measure
                }
                mRenderPath.addPath(path, mFinalPathMatrix);

                if (fullPath.mFillColor != Color.TRANSPARENT) {
                    if (mFillPaint == null) {
                        mFillPaint = new Paint();
                        mFillPaint.setStyle(Paint.Style.FILL);
                        mFillPaint.setAntiAlias(true);
                    }

                    final Paint fillPaint = mFillPaint;
                    fillPaint.setColor(applyAlpha(fullPath.mFillColor, fullPath.mFillAlpha));
                    fillPaint.setColorFilter(filter);
                    canvas.drawPath(mRenderPath, fillPaint);
                }

                if (fullPath.mStrokeColor != Color.TRANSPARENT) {
                    if (mStrokePaint == null) {
                        mStrokePaint = new Paint();
                        mStrokePaint.setStyle(Paint.Style.STROKE);
                        mStrokePaint.setAntiAlias(true);
                    }

                    final Paint strokePaint = mStrokePaint;
                    if (fullPath.mStrokeLineJoin != null) {
                        strokePaint.setStrokeJoin(fullPath.mStrokeLineJoin);
                    }

                    if (fullPath.mStrokeLineCap != null) {
                        strokePaint.setStrokeCap(fullPath.mStrokeLineCap);
                    }

                    strokePaint.setStrokeMiter(fullPath.mStrokeMiterlimit);
                    strokePaint.setColor(applyAlpha(fullPath.mStrokeColor, fullPath.mStrokeAlpha));
                    strokePaint.setColorFilter(filter);
                    strokePaint.setStrokeWidth(fullPath.mStrokeWidth * minScale);
                    canvas.drawPath(mRenderPath, strokePaint);
                }
            }
        }
    }

    private static class VGroup {
        // mStackedMatrix is only used temporarily when drawing, it combines all
        // the parents' local matrices with the current one.
        private final Matrix mStackedMatrix = new Matrix();

        /////////////////////////////////////////////////////
        // Variables below need to be copied (deep copy if applicable) for mutation.
        final ArrayList<Object> mChildren = new ArrayList<Object>();

        private float mRotate = 0;
        private float mPivotX = 0;
        private float mPivotY = 0;
        private float mScaleX = 1;
        private float mScaleY = 1;
        private float mTranslateX = 0;
        private float mTranslateY = 0;

        // mLocalMatrix is updated based on the update of transformation information,
        // either parsed from the XML or by animation.
        private final Matrix mLocalMatrix = new Matrix();
        private int mChangingConfigurations;
        private int[] mThemeAttrs;
        private String mGroupName = null;

        public VGroup(VGroup copy, ArrayMap<String, Object> targetsMap) {
            mRotate = copy.mRotate;
            mPivotX = copy.mPivotX;
            mPivotY = copy.mPivotY;
            mScaleX = copy.mScaleX;
            mScaleY = copy.mScaleY;
            mTranslateX = copy.mTranslateX;
            mTranslateY = copy.mTranslateY;
            mThemeAttrs = copy.mThemeAttrs;
            mGroupName = copy.mGroupName;
            mChangingConfigurations = copy.mChangingConfigurations;
            if (mGroupName != null) {
                targetsMap.put(mGroupName, this);
            }

            mLocalMatrix.set(copy.mLocalMatrix);

            final ArrayList<Object> children = copy.mChildren;
            for (int i = 0; i < children.size(); i++) {
                Object copyChild = children.get(i);
                if (copyChild instanceof VGroup) {
                    VGroup copyGroup = (VGroup) copyChild;
                    mChildren.add(new VGroup(copyGroup, targetsMap));
                } else {
                    VPath newPath = null;
                    if (copyChild instanceof VFullPath) {
                        newPath = new VFullPath((VFullPath) copyChild);
                    } else if (copyChild instanceof VClipPath) {
                        newPath = new VClipPath((VClipPath) copyChild);
                    } else {
                        throw new IllegalStateException("Unknown object in the tree!");
                    }
                    mChildren.add(newPath);
                    if (newPath.mPathName != null) {
                        targetsMap.put(newPath.mPathName, newPath);
                    }
                }
            }
        }

        public VGroup() {
        }

        public String getGroupName() {
            return mGroupName;
        }

        public Matrix getLocalMatrix() {
            return mLocalMatrix;
        }

        public void inflate(Resources res, AttributeSet attrs, Theme theme) {
            final TypedArray a = obtainAttributes(res, theme, attrs,
                    R.styleable.VectorDrawableGroup);
            updateStateFromTypedArray(a);
            a.recycle();
        }

        private void updateStateFromTypedArray(TypedArray a) {
            // Account for any configuration changes.
            mChangingConfigurations |= getChangingConfigurations(a);

            // Extract the theme attributes, if any.
            //TODO
//            mThemeAttrs = a.extractThemeAttrs();

            mRotate = a.getFloat(R.styleable.VectorDrawableGroup_android_rotation, mRotate);
            mPivotX = a.getFloat(R.styleable.VectorDrawableGroup_android_pivotX, mPivotX);
            mPivotY = a.getFloat(R.styleable.VectorDrawableGroup_android_pivotY, mPivotY);
            mScaleX = a.getFloat(R.styleable.VectorDrawableGroup_android_scaleX, mScaleX);
            mScaleY = a.getFloat(R.styleable.VectorDrawableGroup_android_scaleY, mScaleY);
            mTranslateX = a.getFloat(R.styleable.VectorDrawableGroup_vc_translateX, mTranslateX);
            mTranslateY = a.getFloat(R.styleable.VectorDrawableGroup_vc_translateY, mTranslateY);

            final String groupName = a.getString(R.styleable.VectorDrawableGroup_android_name);
            if (groupName != null) {
                mGroupName = groupName;
            }

            updateLocalMatrix();
        }

        public boolean canApplyTheme() {
            return mThemeAttrs != null;
        }

        public void applyTheme(Theme t) {
            if (mThemeAttrs == null) {
                return;
            }

            //TODO: will not support drawable theming yet (applies to tinting mainly)
//            final TypedArray a = t.resolveAttributes(mThemeAttrs, R.styleable.VectorDrawableGroup);
//            updateStateFromTypedArray(a);
//            a.recycle();
        }

        private void updateLocalMatrix() {
            // The order we apply is the same as the
            // RenderNode.cpp::applyViewPropertyTransforms().
            mLocalMatrix.reset();
            mLocalMatrix.postTranslate(-mPivotX, -mPivotY);
            mLocalMatrix.postScale(mScaleX, mScaleY);
            mLocalMatrix.postRotate(mRotate, 0, 0);
            mLocalMatrix.postTranslate(mTranslateX + mPivotX, mTranslateY + mPivotY);
        }

        /* Setters and Getters, used by animator from AnimatedVectorDrawable. */
        @SuppressWarnings("unused")
        public float getRotation() {
            return mRotate;
        }

        @SuppressWarnings("unused")
        public void setRotation(float rotation) {
            if (rotation != mRotate) {
                mRotate = rotation;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getPivotX() {
            return mPivotX;
        }

        @SuppressWarnings("unused")
        public void setPivotX(float pivotX) {
            if (pivotX != mPivotX) {
                mPivotX = pivotX;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getPivotY() {
            return mPivotY;
        }

        @SuppressWarnings("unused")
        public void setPivotY(float pivotY) {
            if (pivotY != mPivotY) {
                mPivotY = pivotY;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getScaleX() {
            return mScaleX;
        }

        @SuppressWarnings("unused")
        public void setScaleX(float scaleX) {
            if (scaleX != mScaleX) {
                mScaleX = scaleX;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getScaleY() {
            return mScaleY;
        }

        @SuppressWarnings("unused")
        public void setScaleY(float scaleY) {
            if (scaleY != mScaleY) {
                mScaleY = scaleY;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getTranslateX() {
            return mTranslateX;
        }

        @SuppressWarnings("unused")
        public void setTranslateX(float translateX) {
            if (translateX != mTranslateX) {
                mTranslateX = translateX;
                updateLocalMatrix();
            }
        }

        @SuppressWarnings("unused")
        public float getTranslateY() {
            return mTranslateY;
        }

        @SuppressWarnings("unused")
        public void setTranslateY(float translateY) {
            if (translateY != mTranslateY) {
                mTranslateY = translateY;
                updateLocalMatrix();
            }
        }
    }

    /**
     * Common Path information for clip path and normal path.
     */
    private static class VPath {
        protected PathParser.PathDataNode[] mNodes = null;
        String mPathName;
        int mChangingConfigurations;

        public VPath() {
            // Empty constructor.
        }

        public VPath(VPath copy) {
            mPathName = copy.mPathName;
            mChangingConfigurations = copy.mChangingConfigurations;
            mNodes = PathParser.deepCopyNodes(copy.mNodes);
        }

        public void toPath(Path path) {
            path.reset();
            if (mNodes != null) {
                PathParser.PathDataNode.nodesToPath(mNodes, path);
            }
        }

        public String getPathName() {
            return mPathName;
        }

        public boolean canApplyTheme() {
            return false;
        }

        public void applyTheme(Theme t) {
        }

        public boolean isClipPath() {
            return false;
        }

        /* Setters and Getters, used by animator from AnimatedVectorDrawable. */
        @SuppressWarnings("unused")
        public PathParser.PathDataNode[] getPathData() {
            return mNodes;
        }

        @SuppressWarnings("unused")
        public void setPathData(PathParser.PathDataNode[] nodes) {
            if (!PathParser.canMorph(mNodes, nodes)) {
                // This should not happen in the middle of animation.
                mNodes = PathParser.deepCopyNodes(nodes);
            } else {
                PathParser.updateNodes(mNodes, nodes);
            }
        }
    }

    /**
     * Clip path, which only has name and pathData.
     */
    private static class VClipPath extends VPath {
        public VClipPath() {
            // Empty constructor.
        }

        public VClipPath(VClipPath copy) {
            super(copy);
        }

        public void inflate(Resources r, AttributeSet attrs, Theme theme) {
            final TypedArray a = obtainAttributes(r, theme, attrs,
                    R.styleable.VectorDrawableClipPath);
            updateStateFromTypedArray(a);
            a.recycle();
        }

        private void updateStateFromTypedArray(TypedArray a) {
            // Account for any configuration changes.
            mChangingConfigurations |= getChangingConfigurations(a);

            final String pathName = a.getString(R.styleable.VectorDrawableClipPath_android_name);
            if (pathName != null) {
                mPathName = pathName;
            }

            final String pathData = a.getString(R.styleable.VectorDrawableClipPath_vc_pathData);
            if (pathData != null) {
                mNodes = PathParser.createNodesFromPathData(pathData);
            }
        }

        @Override
        public boolean isClipPath() {
            return true;
        }
    }

    /**
     * Normal path, which contains all the fill / paint information.
     */
    protected static class VFullPath extends VPath {
        /////////////////////////////////////////////////////
        // Variables below need to be copied (deep copy if applicable) for mutation.
        private int[] mThemeAttrs;

        int mStrokeColor = Color.TRANSPARENT;
        float mStrokeWidth = 0;

        int mFillColor = Color.TRANSPARENT;
        float mStrokeAlpha = 1.0f;
        int mFillRule;
        float mFillAlpha = 1.0f;
        float mTrimPathStart = 0;
        float mTrimPathEnd = 1;
        float mTrimPathOffset = 0;

        Paint.Cap mStrokeLineCap = Paint.Cap.BUTT;
        Paint.Join mStrokeLineJoin = Paint.Join.MITER;
        float mStrokeMiterlimit = 4;

        public VFullPath() {
            // Empty constructor.
        }

        public VFullPath(VFullPath copy) {
            super(copy);
            mThemeAttrs = copy.mThemeAttrs;

            mStrokeColor = copy.mStrokeColor;
            mStrokeWidth = copy.mStrokeWidth;
            mStrokeAlpha = copy.mStrokeAlpha;
            mFillColor = copy.mFillColor;
            mFillRule = copy.mFillRule;
            mFillAlpha = copy.mFillAlpha;
            mTrimPathStart = copy.mTrimPathStart;
            mTrimPathEnd = copy.mTrimPathEnd;
            mTrimPathOffset = copy.mTrimPathOffset;

            mStrokeLineCap = copy.mStrokeLineCap;
            mStrokeLineJoin = copy.mStrokeLineJoin;
            mStrokeMiterlimit = copy.mStrokeMiterlimit;
        }

        private Paint.Cap getStrokeLineCap(int id, Paint.Cap defValue) {
            switch (id) {
                case LINECAP_BUTT:
                    return Paint.Cap.BUTT;
                case LINECAP_ROUND:
                    return Paint.Cap.ROUND;
                case LINECAP_SQUARE:
                    return Paint.Cap.SQUARE;
                default:
                    return defValue;
            }
        }

        private Paint.Join getStrokeLineJoin(int id, Paint.Join defValue) {
            switch (id) {
                case LINEJOIN_MITER:
                    return Paint.Join.MITER;
                case LINEJOIN_ROUND:
                    return Paint.Join.ROUND;
                case LINEJOIN_BEVEL:
                    return Paint.Join.BEVEL;
                default:
                    return defValue;
            }
        }

        @Override
        public boolean canApplyTheme() {
            return mThemeAttrs != null;
        }

        public void inflate(Resources r, AttributeSet attrs, Theme theme) {
            final TypedArray a = obtainAttributes(r, theme, attrs,
                    R.styleable.VectorDrawablePath);
            updateStateFromTypedArray(a);
            a.recycle();
        }

        private void updateStateFromTypedArray(TypedArray a) {
            // Account for any configuration changes.
            mChangingConfigurations |= getChangingConfigurations(a);

            // Extract the theme attributes, if any.
            //TODO: will not support drawable theming yet (applies to tinting mainly)
            //mThemeAttrs = a.extractThemeAttrs();

            final String pathName = a.getString(R.styleable.VectorDrawablePath_android_name);
            if (pathName != null) {
                mPathName = pathName;
            }

            final String pathData = a.getString(R.styleable.VectorDrawablePath_vc_pathData);
            if (pathData != null) {
                mNodes = PathParser.createNodesFromPathData(pathData);
            }

            mFillColor = a.getColor(R.styleable.VectorDrawablePath_vc_fillColor, mFillColor);
            mFillAlpha = a.getFloat(R.styleable.VectorDrawablePath_vc_fillAlpha, mFillAlpha);
            mStrokeLineCap = getStrokeLineCap(a.getInt(R.styleable.VectorDrawablePath_vc_strokeLineCap, -1), mStrokeLineCap);
            mStrokeLineJoin = getStrokeLineJoin(a.getInt(R.styleable.VectorDrawablePath_vc_strokeLineJoin, -1), mStrokeLineJoin);
            mStrokeMiterlimit = a.getFloat(R.styleable.VectorDrawablePath_vc_strokeMiterLimit, mStrokeMiterlimit);
            mStrokeColor = a.getColor(R.styleable.VectorDrawablePath_vc_strokeColor, mStrokeColor);
            mStrokeAlpha = a.getFloat(R.styleable.VectorDrawablePath_vc_strokeAlpha, mStrokeAlpha);
            mStrokeWidth = a.getFloat(R.styleable.VectorDrawablePath_vc_strokeWidth, mStrokeWidth);
            mTrimPathEnd = a.getFloat(R.styleable.VectorDrawablePath_vc_trimPathEnd, mTrimPathEnd);
            mTrimPathOffset = a.getFloat(R.styleable.VectorDrawablePath_vc_trimPathOffset, mTrimPathOffset);
            mTrimPathStart = a.getFloat(R.styleable.VectorDrawablePath_vc_trimPathStart, mTrimPathStart);
        }

        @Override
        public void applyTheme(Theme t) {
            if (mThemeAttrs == null) {
                return;
            }

            //TODO: will not support drawable theming yet (applies to tinting mainly)
            //final TypedArray a = t.resolveAttributes(mThemeAttrs, R.styleable.VectorDrawablePath);
            //updateStateFromTypedArray(a);
            //a.recycle();
        }

        /* Setters and Getters, used by animator from AnimatedVectorDrawable. */
        @SuppressWarnings("unused")
        int getStrokeColor() {
            return mStrokeColor;
        }

        @SuppressWarnings("unused")
        void setStrokeColor(int strokeColor) {
            mStrokeColor = strokeColor;
        }

        @SuppressWarnings("unused")
        float getStrokeWidth() {
            return mStrokeWidth;
        }

        @SuppressWarnings("unused")
        void setStrokeWidth(float strokeWidth) {
            mStrokeWidth = strokeWidth;
        }

        @SuppressWarnings("unused")
        float getStrokeAlpha() {
            return mStrokeAlpha;
        }

        @SuppressWarnings("unused")
        void setStrokeAlpha(float strokeAlpha) {
            mStrokeAlpha = strokeAlpha;
        }

        @SuppressWarnings("unused")
        int getFillColor() {
            return mFillColor;
        }

        @SuppressWarnings("unused")
        void setFillColor(int fillColor) {
            mFillColor = fillColor;
        }

        @SuppressWarnings("unused")
        float getFillAlpha() {
            return mFillAlpha;
        }

        @SuppressWarnings("unused")
        void setFillAlpha(float fillAlpha) {
            mFillAlpha = fillAlpha;
        }

        @SuppressWarnings("unused")
        float getTrimPathStart() {
            return mTrimPathStart;
        }

        @SuppressWarnings("unused")
        void setTrimPathStart(float trimPathStart) {
            mTrimPathStart = trimPathStart;
        }

        @SuppressWarnings("unused")
        float getTrimPathEnd() {
            return mTrimPathEnd;
        }

        @SuppressWarnings("unused")
        void setTrimPathEnd(float trimPathEnd) {
            mTrimPathEnd = trimPathEnd;
        }

        @SuppressWarnings("unused")
        float getTrimPathOffset() {
            return mTrimPathOffset;
        }

        @SuppressWarnings("unused")
        void setTrimPathOffset(float trimPathOffset) {
            mTrimPathOffset = trimPathOffset;
        }
    }
}

