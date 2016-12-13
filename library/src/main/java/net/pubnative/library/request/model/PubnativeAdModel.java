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

package net.pubnative.library.request.model;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import net.pubnative.URLDriller;
import net.pubnative.library.request.PubnativeAsset;
import net.pubnative.library.request.PubnativeMeta;
import net.pubnative.library.request.model.api.PubnativeAPIV3AdModel;
import net.pubnative.library.request.model.api.PubnativeAPIV3DataModel;
import net.pubnative.library.tracking.PubnativeImpressionManager;
import net.pubnative.library.tracking.PubnativeImpressionTracker;
import net.pubnative.library.tracking.PubnativeTrackingManager;
import net.pubnative.library.utils.SystemUtils;
import net.pubnative.library.widget.PubnativeContentInfoWidget;
import net.pubnative.library.widget.PubnativeWebView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PubnativeAdModel implements PubnativeImpressionTracker.Listener,
                                         URLDriller.Listener,
                                         Serializable {

    private static String TAG = PubnativeAdModel.class.getSimpleName();

    private static final String                DATA_CONTENTINFO_LINK_KEY   = "link";
    private static final String                DATA_CONTENTINFO_ICON_KEY   = "icon";
    private static final String                DATA_TRACKING_KEY           = "tracking";
    //Generic Fields
    protected transient  Listener              mListener                   = null;
    protected            Context               mContext                    = null;
    protected            PubnativeAPIV3AdModel mData                       = null;
    protected            List<String>          mUsedAssets                 = null;
    protected            UUID                  mUUID                       = null;
    // Click
    protected            boolean               mIsWaitingForClickCache     = false;
    protected            boolean               mIsClickLoaderEnabled       = true;
    protected            boolean               mIsClickInBackgroundEnabled = true;
    protected            boolean               mIsClickCachingEnabled      = false;
    protected            boolean               mIsClickPreparing           = false;
    protected            String                mClickFinalURL              = null;
    //Tracking
    private transient    boolean               mIsImpressionConfirmed      = false;
    private transient    View                  mClickableView              = null;
    private transient    View                  mAdView                     = null;
    //Loading View
    private transient    RelativeLayout        loadingView                 = null;

    //==============================================================================================
    // Listener
    //==============================================================================================

    /**
     * Interface definition for callbacks to be invoked when impression confirmed/failed, ad clicked/clickfailed
     */
    public interface Listener {

        /**
         * Called when impression is confirmed
         *
         * @param pubnativeAdModel PubnativeAdModel impression that was confirmed
         * @param view             The view where impression confirmed
         */
        void onPubnativeAdModelImpression(PubnativeAdModel pubnativeAdModel, View view);

        /**
         * Called when click is confirmed
         *
         * @param pubnativeAdModel PubnativeAdModel that detected the click
         * @param view             The view that was clicked
         */
        void onPubnativeAdModelClick(PubnativeAdModel pubnativeAdModel, View view);

        /**
         * Called before the model opens the offer
         *
         * @param pubnativeAdModel PubnativeAdModel which's offer will be opened
         */
        void onPubnativeAdModelOpenOffer(PubnativeAdModel pubnativeAdModel);
    }

    public static PubnativeAdModel create(Context context, PubnativeAPIV3AdModel data) {

        PubnativeAdModel model = new PubnativeAdModel();
        model.mData = data;
        model.mContext = context;
        return model;
    }

    //==============================================================================================
    // Generic Fields
    //==============================================================================================

    /**
     * Gets the specified meta field raw data
     *
     * @param meta meta field type name
     * @return valid PubnativeAPIV3DataModel if present, null if not
     */
    public PubnativeAPIV3DataModel getMeta(String meta) {

        Log.v(TAG, "getMeta");
        PubnativeAPIV3DataModel result = null;
        if (mData == null) {
            Log.w(TAG, "getMeta - Error: ad data not present");
        } else {
            result = mData.getMeta(meta);
        }
        return result;
    }

    /**
     * Gets the specified asset field raw data
     *
     * @param asset asset field type name
     * @return valid PubnativeAPIV3DataModel if present, null if not
     */
    public PubnativeAPIV3DataModel getAsset(String asset) {

        return getAsset(asset, true);
    }

    protected PubnativeAPIV3DataModel getAsset(String asset, boolean trackAsset) {

        Log.v(TAG, "getAsset");
        PubnativeAPIV3DataModel result = null;
        if (mData == null) {
            Log.w(TAG, "getAsset - Error: ad data not present");
        } else {
            result = mData.getAsset(asset);
            if (result != null) {
                recordAsset(result.getStringField(DATA_TRACKING_KEY));
            }
        }
        return result;
    }

    protected void recordAsset(String url) {

        Log.v(TAG, "recordAsset");
        if (!TextUtils.isEmpty(url)) {
            if (mUsedAssets == null) {
                mUsedAssets = new ArrayList<String>();
            }
            if (!mUsedAssets.contains(url)) {
                mUsedAssets.add(url);
            }
        }
    }

    //==============================================================================================
    // Fields
    //==============================================================================================

    /**
     * Gets the title string of the ad
     *
     * @return String representation of the ad title, null if not present
     */
    public String getTitle() {

        Log.v(TAG, "getTitle");
        String result = null;
        PubnativeAPIV3DataModel data = getAsset(PubnativeAsset.TITLE);
        if (data != null) {
            result = data.getText();
        }
        return result;
    }

    /**
     * Gets the description string of the ad
     *
     * @return String representation of the ad Description, null if not present
     */
    public String getDescription() {

        Log.v(TAG, "getDescription");
        String result = null;
        PubnativeAPIV3DataModel data = getAsset(PubnativeAsset.DESCRIPTION);
        if (data != null) {
            result = data.getText();
        }
        return result;
    }

    /**
     * Gets the call to action string of the ad
     *
     * @return String representation of the call to action value, null if not present
     */
    public String getCtaText() {

        Log.v(TAG, "getCtaText");
        String result = null;
        PubnativeAPIV3DataModel data = getAsset(PubnativeAsset.CALL_TO_ACTION);
        if (data != null) {
            result = data.getText();
        }
        return result;
    }

    /**
     * Gets the icon image url of the ad
     *
     * @return valid String with the url value, null if not present
     */
    public String getIconUrl() {

        Log.v(TAG, "getIconUrl");
        String result = null;
        PubnativeAPIV3DataModel data = getAsset(PubnativeAsset.ICON);
        if (data != null) {
            result = data.getURL();
        }
        return result;
    }

    public String getVast() {

        Log.v(TAG, "getVast");
        String result = null;
        PubnativeAPIV3DataModel data = getAsset(PubnativeAsset.VAST);
        if (data != null) {
            result = data.getStringField("vast2");
        }
        return result;
    }

    /**
     * Gets the banner image url of the ad
     *
     * @return valid String with the url value, null if not present
     */
    public String getBannerUrl() {

        Log.v(TAG, "getBannerUrl");
        String result = null;
        PubnativeAPIV3DataModel data = getAsset(PubnativeAsset.BANNER);
        if (data != null) {
            result = data.getURL();
        }
        return result;
    }

    /**
     * Gets url of the assets (html banner page, standard banner etc.)
     *
     * @param asset asset name for which url requested.
     * @return valid String with the url value, null if not present.
     */
    public String getAssetUrl(String asset) {

        Log.v(TAG, "getAssetUrl");
        String result = null;
        PubnativeAPIV3DataModel data = getAsset(asset);
        if (data != null) {
            result = data.getURL();
        }
        return result;
    }

    /**
     * Gets the asset group id of the ad
     *
     * @return int value with the id of the asset group ad, 0 if not present
     */
    public int getAssetGroupId() {

        Log.v(TAG, "getAssetGroupId");
        int result = 0;
        if (mData == null) {
            Log.w(TAG, "getAssetGroupId - Error: ad data not present");
        } else {
            result = mData.assetgroupid;
        }
        return result;
    }

    /**
     * Gets the click url of the ad
     *
     * @return String value with the url of the click, null if not present
     */
    public String getClickUrl() {

        Log.v(TAG, "getClickUrl");
        String result = null;
        if (mData == null) {
            Log.w(TAG, "getClickUrl - Error: ad data not present");
        } else {
            result = mData.link;
        }
        return result;
    }

    /**
     * Gets rating of the app in a value from 0 to 5
     *
     * @return int value, 0 if not present
     */
    public int getRating() {

        Log.v(TAG, "getRating");
        int result = 0;
        PubnativeAPIV3DataModel data = getAsset(PubnativeAsset.RATING);
        if (data != null) {
            Double rating = data.getNumber();
            if (rating != null) {
                result = rating.intValue();
            }
        }
        return result;
    }

    /**
     * Gets content info view
     *
     * @param context Valid context
     * @return View containing content info
     */
    public View getContentInfo(Context context) {
        View result = null;
        PubnativeAPIV3DataModel data = getMeta(PubnativeMeta.CONTENT_INFO);
        if (context == null) {
            Log.e(TAG, "getContentInfo - not a valid context");
        } else if (data == null) {
            Log.e(TAG, "getContentInfo - contentInfo data not found");
        } else if (TextUtils.isEmpty(data.getStringField(DATA_CONTENTINFO_ICON_KEY))) {
            Log.e(TAG, "getContentInfo - contentInfo icon not found");
        } else if (TextUtils.isEmpty(data.getStringField(DATA_CONTENTINFO_LINK_KEY))) {
            Log.e(TAG, "getContentInfo - contentInfo link not found");
        } else if (TextUtils.isEmpty(data.getText())) {
            Log.e(TAG, "getContentInfo - contentInfo text not found");
        } else {
            final PubnativeContentInfoWidget widget = new PubnativeContentInfoWidget(context);
            widget.setIconUrl(data.getStringField(DATA_CONTENTINFO_ICON_KEY));
            widget.setIconClickUrl(data.getStringField(DATA_CONTENTINFO_LINK_KEY));
            widget.setContextText(data.getText());
            widget.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    widget.openLayout();
                }
            });
            result = widget;
        }
        return result;
    }

    /**
     * Gets the type of the ad "native" or "video"
     *
     * @return valid String native/video depending on the ad type
     * @deprecated There are no longer differencies about video/native ad, it's implicit to the request
     * so you should stop using this method
     */
    @Deprecated
    public String getType() {

        Log.v(TAG, "getType");
        String result = "native";
        if (getAsset(PubnativeAsset.VAST, false) != null) {
            result = "video";
        }
        return result;
    }

    /**
     * Gets the portrait banner asset of the ad
     *
     * @return String with the url value
     * @deprecated This resource is no longer served, so it will always return null
     */
    @Deprecated
    public String getPortraitBannerUrl() {

        Log.v(TAG, "getPortraitBannerUrl");
        return null;
    }

    /**
     * Gets all the present beacons in the ad
     *
     * @return Returns the list of all present beacons of the ad
     * @deprecated Beacons returned by this method won't contain all the data, and will
     * return null in the future, direct access to beacons will be stopped.
     */
    @Deprecated
    public List<PubnativeBeacon> getBeacons() {

        Log.v(TAG, "getBeacons");
        List<PubnativeBeacon> result = new ArrayList<PubnativeBeacon>();
        if (mData == null) {
            Log.w(TAG, "getBeacons - Error: ad data not present");
        } else {
            result.addAll(createBeacons(PubnativeAPIV3AdModel.Beacon.IMPRESSION));
            result.addAll(createBeacons(PubnativeAPIV3AdModel.Beacon.CLICK));
        }
        return result;
    }

    protected List<PubnativeBeacon> createBeacons(String beaconType) {

        List<PubnativeBeacon> result = null;
        if (mData == null) {
            Log.w(TAG, "getBeacons - Error: ad data not present");
        } else {
            List<PubnativeAPIV3DataModel> beacons = mData.getBeacons(beaconType);
            if (beacons != null && beacons.size() > 0) {
                result = new ArrayList<PubnativeBeacon>();
                for (PubnativeAPIV3DataModel data : beacons) {
                    PubnativeBeacon beacon = new PubnativeBeacon();
                    beacon.js = data.getStringField("js");
                    beacon.type = beaconType;
                    beacon.url = data.getURL();
                }
            }
        }
        return result;
    }

    public boolean isRevenueModelCPA() {
        PubnativeAPIV3DataModel model = getMeta("revenuemodel");
        if (model != null) {
            return model.getText().equalsIgnoreCase("cpa");
        } else {
            return false;
        }
    }

    //==============================================================================================
    // Helpers
    //==============================================================================================

    /**
     * This will enable / disable the spin that takes the screen on click. Default behaviour is enabled
     *
     * @param enabled true will show a spinner on top of the screen, false will disable the click spin view
     */
    public void setUseClickLoader(boolean enabled) {

        Log.v(TAG, "setUseClickLoader");
        mIsClickLoaderEnabled = enabled;
    }

    public void setUseClickInBackground(boolean enabled) {

        Log.v(TAG, "setUseClickInBackground");
        mIsClickInBackgroundEnabled = enabled;
    }

    public void setUseClickCaching(boolean enabled) {

        Log.v(TAG, "setUseClickCaching");
        mIsClickCachingEnabled = enabled;
    }

    /**
     * This function will return the first present beacon URL of the specified type
     *
     * @param beaconType type of beacon
     * @return return Beacon URL or null if not present.
     * @deprecated Beacons are multiple now, so this method will not cover all options,
     * IT could even return a beacon with null url, since there are other type of beacons supported.
     */
    @Deprecated
    public String getBeacon(String beaconType) {

        Log.v(TAG, "getBeacon");
        String beaconUrl = null;
        if (TextUtils.isEmpty(beaconType)) {
            Log.e(TAG, "getBeacon - Error: beacon type is null or empty");
        } else {
            for (PubnativeBeacon beacon : getBeacons()) {
                if (beaconType.equalsIgnoreCase(beacon.type)) {
                    beaconUrl = beacon.url;
                    break;
                }
            }
        }
        return beaconUrl;
    }

    /**
     * This method prepares all possible resources
     */
    public void fetch() {

        prepareClickURL();
    }

    protected void prepareClickURL() {

        if (isRevenueModelCPA() && mIsClickCachingEnabled && mClickFinalURL == null && !mIsClickPreparing) {

            mIsClickPreparing = true;
            mUUID = UUID.randomUUID();
            String firstReqUrl = getClickUrl() + "&uxc=true&uuid=" + mUUID.toString();
            URLDriller driller = new URLDriller();
            driller.setListener(new URLDriller.Listener() {

                @Override
                public void onURLDrillerStart(String url) {
                    //Do nothing
                }

                @Override
                public void onURLDrillerRedirect(String url) {
                    //Do nothing
                }

                @Override
                public void onURLDrillerFinish(String url) {
                    onPrepareClickURLFinish(url);
                }

                @Override
                public void onURLDrillerFail(String url, Exception exception) {
                    onPrepareClickURLFinish(url);
                }
            });
            driller.drill(firstReqUrl);
        }

    }

    protected void onPrepareClickURLFinish(String url) {

        mClickFinalURL = url;
        mIsClickPreparing = false;
        if (mIsWaitingForClickCache) {

            mIsWaitingForClickCache = false;
            openCachedClick(mContext);
            hideLoadingView();
        }
    }


    protected void openCachedClick(Context context) {

        URLDriller driller = new URLDriller();
        driller.setUserAgent(SystemUtils.getWebViewUserAgent(context));
        driller.setListener(PubnativeAdModel.this);
        driller.drill(getClickUrl() + "&cached=true&uuid=" + mUUID.toString());
        openURL(mClickFinalURL);
    }

    //==============================================================================================
    // Tracking
    //==============================================================================================

    /**
     * Start tracking of ad view to auto confirm impressions and handle clicks
     *
     * @param view     ad view
     * @param listener listener for callbacks
     */
    public void startTracking(View view, Listener listener) {

        Log.v(TAG, "startTracking: both ad view & clickable view are same");
        startTracking(view, view, listener);
    }

    /**
     * Start tracking of ad view to auto confirm impressions and handle clicks
     *
     * @param view          ad view
     * @param clickableView clickable view
     * @param listener      listener for callbacks
     */
    public void startTracking(View view, View clickableView, Listener listener) {

        Log.v(TAG, "startTracking");

        if (listener == null) {
            Log.w(TAG, "startTracking - listener is null, start tracking without callbacks");
        }

        mListener = listener;

        stopTracking();

        startTrackingImpression(view);
        startTrackingClicks(clickableView);
    }

    protected void startTrackingImpression(View view) {

        Log.d(TAG, "startTrackingImpression");
        if (view == null) {
            Log.w(TAG, "startTrackingImpression - ad view is null, cannot start tracking");
        } else if (mIsImpressionConfirmed) {
            Log.v(TAG, "startTrackingImpression - impression is already confirmed, dropping impression tracking");
        } else {
            mAdView = view;
            PubnativeImpressionManager.startTrackingView(view, this);
        }
    }

    protected void startTrackingClicks(View clickableView) {

        Log.d(TAG, "startTrackingClicks");
        if (TextUtils.isEmpty(getClickUrl())) {
            Log.w(TAG, "startTrackingClicks - Error: click url is empty, clicks won't be tracked");
        } else if (clickableView == null) {
            Log.w(TAG, "startTrackingClicks - Error: click view is null, clicks won't be tracked");
        } else {

            prepareClickURL();

            mClickableView = clickableView;
            mClickableView.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {

                    Log.v(TAG, "onClick detected");
                    if (mIsClickLoaderEnabled) {
                        showLoadingView();
                    }
                    invokeOnClick(view);
                    confirmClickBeacons(view.getContext());

                    if (mIsClickInBackgroundEnabled) {

                        if (mIsClickCachingEnabled) {

                            if (mClickFinalURL == null) {
                                mIsWaitingForClickCache = true;
                            } else {
                                openCachedClick(view.getContext());
                            }
                        } else {
                            // No CPI offer, so we simply follow redirection and open at the end
                            URLDriller driller = new URLDriller();
                            driller.setUserAgent(SystemUtils.getWebViewUserAgent(view.getContext()));
                            driller.setListener(PubnativeAdModel.this);
                            driller.drill(getClickUrl());
                        }
                    } else {
                        openURL(getClickUrl());
                    }
                }
            });
        }
    }

    /**
     * stop tracking of ad view
     */
    public void stopTracking() {

        Log.v(TAG, "stopTracking");
        stopTrackingImpression();
        stopTrackingClicks();
    }

    protected void stopTrackingImpression() {

        Log.v(TAG, "stopTrackingImpression");
        PubnativeImpressionManager.stopTrackingAll(this);
    }

    protected void stopTrackingClicks() {

        Log.v(TAG, "stopTrackingClicks");
        if (mClickableView != null) {
            mClickableView.setOnClickListener(null);
        }
    }

    protected void openURL(String urlString) {

        Log.v(TAG, "openURL: " + urlString);
        if (TextUtils.isEmpty(urlString)) {
            Log.w(TAG, "Error: ending URL cannot be opened - " + urlString);
        } else if (mClickableView == null) {
            Log.w(TAG, "Error: clickable view not set");
        } else {
            try {
                Uri uri = Uri.parse(urlString);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mClickableView.getContext().startActivity(intent);
                invokeOnOpenOffer();
            } catch (Exception ex) {
                Log.w(TAG, "openURL: Error - " + ex.getMessage());
            }
        }
    }

    protected void confirmImpressionBeacons(Context context) {

        Log.v(TAG, "confirmImpressionBeacons");
        // 1. Track assets
        if (mUsedAssets != null) {
            for (String asset : mUsedAssets) {
                PubnativeTrackingManager.track(context, asset);
            }
        }
        // 2. Track impressions
        confirmBeacons(PubnativeAPIV3AdModel.Beacon.IMPRESSION, context);
    }

    protected void confirmClickBeacons(Context context) {

        Log.v(TAG, "confirmClickBeacons");
        confirmBeacons(PubnativeAPIV3AdModel.Beacon.CLICK, context);
    }

    protected void confirmBeacons(String beaconType, Context context) {

        Log.v(TAG, "confirmBeacons: " + beaconType);
        if (mData == null) {
            Log.w(TAG, "confirmBeacons - Error: ad data not present");
            return;
        }

        List<PubnativeAPIV3DataModel> beacons = mData.getBeacons(beaconType);
        if (beacons == null) {
            return;
        }

        for (PubnativeAPIV3DataModel beaconData : beacons) {
            String beaconURL = beaconData.getURL();
            String beaconJS = beaconData.getStringField("js");
            if (!TextUtils.isEmpty(beaconURL)) {
                // URL
                PubnativeTrackingManager.track(context, beaconURL);
            } else if (!TextUtils.isEmpty(beaconJS)) {
                try {
                    new PubnativeWebView(context).loadBeacon(beaconJS);
                } catch (Exception e) {
                    Log.e(TAG, "confirmImpressionBeacons - JS Error: " + e);
                }
            }
        }

    }

    //==============================================================================================
    // LoadingView
    //==============================================================================================

    protected void showLoadingView() {

        Log.v(TAG, "showLoadingView");
        if (getRootView() == null) {
            Log.w(TAG, "showLoadingView - Error: impossible to retrieve root view");
        } else {
            hideLoadingView();
            getRootView().addView(getLoadingView(),
                                  new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                             ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    protected void hideLoadingView() {

        Log.v(TAG, "hideLoadingView");
        if (getRootView() == null) {
            Log.w(TAG, "hideLoadingView - Error: impossible to retrieve root view");
        }
        if (getLoadingView().getParent()!=null){
            ((ViewGroup)getLoadingView().getParent()).removeView(getLoadingView());
        }
    }

    protected ViewGroup getRootView() {

        Log.v(TAG, "getRootView");
        ViewGroup result = null;
        if (mAdView == null) {
            Log.w(TAG, "getRootView - Error: not assigned ad view, cannot retrieve root view");
        } else {
            result = (ViewGroup) mAdView.getRootView();
        }
        return result;
    }

    protected RelativeLayout getLoadingView() {

        Log.v(TAG, "getLoadingView");
        if (loadingView == null) {
            loadingView = new RelativeLayout(mAdView.getContext());
            loadingView.setGravity(Gravity.CENTER);
            loadingView.setBackgroundColor(Color.argb(77, 0, 0, 0));
            loadingView.setClickable(true);
            loadingView.addView(new ProgressBar(mAdView.getContext()));
        }
        return loadingView;
    }

    //==============================================================================================
    // Listener helpers
    //==============================================================================================

    protected void invokeOnImpression(View view) {

        Log.v(TAG, "invokeOnImpression");
        mIsImpressionConfirmed = true;
        if (mListener != null) {
            mListener.onPubnativeAdModelImpression(PubnativeAdModel.this, view);
        }
    }

    protected void invokeOnClick(View view) {

        Log.v(TAG, "invokeOnClick");
        if (mListener != null) {
            mListener.onPubnativeAdModelClick(PubnativeAdModel.this, view);
        }
    }

    protected void invokeOnOpenOffer() {

        Log.v(TAG, "invokeOnOpenOffer");
        if (mListener != null) {
            mListener.onPubnativeAdModelOpenOffer(this);
        }
    }

    //==============================================================================================
    // CALLBACKS
    //==============================================================================================
    // PubnativeImpressionTracker.Listener
    //----------------------------------------------------------------------------------------------

    @Override
    public void onImpression(View visibleView) {

        Log.v(TAG, "onImpressionDetected");
        confirmImpressionBeacons(visibleView.getContext());
        invokeOnImpression(visibleView);
    }

    //----------------------------------------------------------------------------------------------
    // URLDriller.Listener
    //----------------------------------------------------------------------------------------------

    @Override
    public void onURLDrillerStart(String url) {

        Log.v(TAG, "onURLDrillerStart: " + url);
    }

    @Override
    public void onURLDrillerRedirect(String url) {

        Log.v(TAG, "onURLDrillerRedirect: " + url);
    }

    @Override
    public void onURLDrillerFinish(String url) {

        Log.v(TAG, "onURLDrillerFinish: " + url);

        if (mClickFinalURL == null) {
            openURL(url);
        }

        hideLoadingView();
    }

    @Override
    public void onURLDrillerFail(String url, Exception exception) {

        Log.v(TAG, "onURLDrillerFail: " + exception);
        if (mClickFinalURL == null) {
            openURL(url);
        }
        hideLoadingView();
    }
}
