// The MIT License (MIT)
//
// Copyright (c) 2016 PubNative GmbH
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

package net.pubnative.library.tracking;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class PubnativeVisibilityTracker {

    private static final String TAG                    = PubnativeVisibilityTracker.class.getSimpleName();
    private static final int    VISIBILITY_CHECK_DELAY = 100;

    protected WeakReference<View>                  mDeviceView                 = null;
    protected WeakReference<Listener>              mListener                   = null;
    protected List<PubnativeVisibilityTrackerItem> mTrackedViews               = new ArrayList<PubnativeVisibilityTrackerItem>();
    protected Handler                              mHandler                    = new Handler();
    protected boolean                              mIsVisibilityCheckScheduled = false;
    protected VisibilityRunnable                   mVisibilityRunnable         = new VisibilityRunnable();
    protected ViewTreeObserver.OnPreDrawListener   mOnPreDrawListener          = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {

            if (mListener.get() == null) {
                clear();
            } else {
                scheduleVisibilityCheck();
            }
            return true;
        }
    };

    //==============================================================================================
    // CALLBACK
    //==============================================================================================
    public interface Listener {

        void onVisibilityCheck(List<View> visibleViews, List<View> invisibleViews);
    }

    //==============================================================================================
    // PUBLIC
    //==============================================================================================

    /**
     * Sets listener for callbacks
     *
     * @param listener valid listener
     */
    public void setListener(Listener listener) {

        mListener = new WeakReference<Listener>(listener);
    }

    /**
     * Adds the view to the tracking view pool and starts tracking it
     *
     * @param view                 view that you want to start tracking
     * @param minVisibilityPercent min amount percent of the view shown to be considered visible
     *                             from 0 to 1
     */
    public void addView(View view, double minVisibilityPercent) {

        if (mDeviceView == null) {
            View decorView = ((Activity) view.getContext()).getWindow().getDecorView();
            mDeviceView = new WeakReference<View>(decorView);
            ViewTreeObserver observer = decorView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.addOnPreDrawListener(mOnPreDrawListener);
            } else {
                Log.d(TAG, "Unable to start tracking, Window ViewTreeObserver is not alive");
            }
        }

        if (containsTrackedView(view)) {
            // Already tracking this view, drop the call
            return;
        }

        PubnativeVisibilityTrackerItem item = new PubnativeVisibilityTrackerItem();
        item.mTrackingView = view;
        item.mMinVisibilityPercent = minVisibilityPercent;

        mTrackedViews.add(item);

        scheduleVisibilityCheck();
    }

    /**
     * Removes the view for visibility tracking
     *
     * @param view view that you want to stop tracking
     */
    public void removeView(View view) {

        mTrackedViews.remove(view);
    }

    /**
     * Stops tracking of all views and removes all callbacks
     */
    public void clear() {

        mHandler.removeMessages(0);
        mTrackedViews.clear();
        mIsVisibilityCheckScheduled = false;
        View decorView = mDeviceView.get();
        if (decorView != null && mOnPreDrawListener != null) {
            ViewTreeObserver observer = decorView.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(mOnPreDrawListener);
            }
            mOnPreDrawListener = null;
        }
        mListener = null;
    }

    //==============================================================================================
    // PRIVATE
    //==============================================================================================

    protected void scheduleVisibilityCheck() {

        if (!mIsVisibilityCheckScheduled) {
            mIsVisibilityCheckScheduled = true;
            mHandler.postDelayed(mVisibilityRunnable, VISIBILITY_CHECK_DELAY);
        }
    }

    // View search

    protected boolean containsTrackedView(View view) {

        return indexOfTrackedView(view) >= 0;
    }

    protected int indexOfTrackedView(View view) {

        int result = -1;
        for (int i = 0; i < mTrackedViews.size(); i++) {
            PubnativeVisibilityTrackerItem item = mTrackedViews.get(i);
            if(item.equals(view)) {
                result = i;
                break;
            }
        }
        return result;
    }

    //==============================================================================================
    // INNER CLASSES
    //==============================================================================================
    protected class PubnativeVisibilityTrackerItem {

        private final String TAG = PubnativeVisibilityTrackerItem.class.getSimpleName();
        public View   mTrackingView;
        public double mMinVisibilityPercent; // Expressed from 0 to 1

        @Override
        public boolean equals(Object o) {

            Log.v(TAG, "equals");

            if (o instanceof View) {
                return o.equals(mTrackingView);
            }
            return super.equals(o);
        }
    }

    protected class VisibilityRunnable implements Runnable {

        private ArrayList<View> mVisibleViews;
        private ArrayList<View> mInvisibleViews;
        private Rect            mVisibleRect;

        VisibilityRunnable() {
            mVisibleRect = new Rect();
            mInvisibleViews = new ArrayList<View>();
            mVisibleViews = new ArrayList<View>();
        }

        @Override
        public void run() {

            mIsVisibilityCheckScheduled = false;

            for (PubnativeVisibilityTrackerItem item : mTrackedViews) {

                if (isVisible(item)) {

                    mVisibleViews.add(item.mTrackingView);
                } else {

                    mInvisibleViews.add(item.mTrackingView);
                }
            }

            if (mListener.get() != null) {
                mListener.get().onVisibilityCheck(mVisibleViews, mInvisibleViews);
            }

            mInvisibleViews.clear();
            mVisibleViews.clear();
        }

        protected boolean isVisible(PubnativeVisibilityTrackerItem item) {

            boolean result = false;

            View view = item.mTrackingView;

            if (view != null
                && view.isShown() // This is specially useful to ensure visibility in lists
                && view.getParent() != null
                && view.getGlobalVisibleRect(mVisibleRect)) {

                Log.i(TAG, "tracking view at: " + System.currentTimeMillis());

                long visibleArea = (long) mVisibleRect.height() * mVisibleRect.width();
                long totalArea = (long) view.getHeight() * view.getWidth();
                double percentVisible = (double) visibleArea / (double) totalArea;

                result = percentVisible >= item.mMinVisibilityPercent;
            }

            return result;
        }
    }
}
