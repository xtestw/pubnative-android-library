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

package net.pubnative.library.utils;

import android.content.Context;
import android.util.Log;
import android.webkit.WebView;

public class SystemUtils {

    private static final String TAG               = SystemUtils.class.getSimpleName();
    private static       String sWebViewUserAgent = null;

    /**
     * Returns the default user agent
     *
     * @param context valid Context
     * @return the user agentof the web view
     */
    public static String getWebViewUserAgent(Context context) {

        Log.v(TAG, "getWebViewUserAgent");
        if (sWebViewUserAgent == null) {
            try {
                sWebViewUserAgent = new WebView(context).getSettings().getUserAgentString();
            } catch (Exception e) {
                Log.w(TAG, "getWebViewUserAgent - Error: cannot inject user agent");
            }
        }
        return sWebViewUserAgent;
    }
}
