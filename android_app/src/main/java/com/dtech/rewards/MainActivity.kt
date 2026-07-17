package com.dtech.rewards

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.content.Context
import android.content.SharedPreferences
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var mainWebView: WebView
    private lateinit var webViewContainer: FrameLayout
    private val popups = mutableListOf<WebView>()

    private lateinit var prefs: SharedPreferences
    private var isExternalSessionActive = false
    private var lastInternalPageWasBerserker = false

    private val PREFS_NAME = "DTechPrefs"
    private val KEY_PENDING_POINTS = "pendingNavigationPoints"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webViewContainer = findViewById(R.id.webViewContainer)
        mainWebView = findViewById(R.id.webView)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupWebView(mainWebView)
        updateCookie()

        mainWebView.loadUrl("https://revenue.dtech-services.co.za")
    }

    private fun addPendingPoint() {
        val currentPoints = prefs.getInt(KEY_PENDING_POINTS, 0)
        prefs.edit().putInt(KEY_PENDING_POINTS, currentPoints + 1).apply()
        updateCookie()
    }

    private fun updateCookie() {
        val currentPoints = prefs.getInt(KEY_PENDING_POINTS, 0)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie("https://revenue.dtech-services.co.za", "pending_navigation_points=$currentPoints; path=/; max-age=31536000")
        cookieManager.flush()
    }

    private fun syncPointsFromCookie() {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://revenue.dtech-services.co.za")
        if (cookies != null) {
            val parts = cookies.split(";")
            for (part in parts) {
                val keyValue = part.trim().split("=")
                if (keyValue.size == 2 && keyValue[0] == "pending_navigation_points") {
                    try {
                        val cookiePoints = keyValue[1].toInt()
                        val currentPrefsPoints = prefs.getInt(KEY_PENDING_POINTS, 0)
                        if (cookiePoints < currentPrefsPoints && cookiePoints >= 0) {
                            prefs.edit().putInt(KEY_PENDING_POINTS, cookiePoints).apply()
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error parsing cookie points", e)
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.setSupportMultipleWindows(true)
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.loadsImagesAutomatically = true
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webSettings.allowContentAccess = true
        webSettings.allowFileAccess = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // Add App Secret to User-Agent for backend verification
        val originalUserAgent = webSettings.userAgentString
        webSettings.userAgentString = "$originalUserAgent DTechApp-Secret-9f8d7b6a"

        webView.webViewClient = MyWebViewClient()
        webView.webChromeClient = MyWebChromeClient()
    }

    private fun isInternalDomain(url: String): Boolean {
        try {
            val uri = Uri.parse(url)
            val host = uri.host ?: return false
            return host == "revenue.dtech-services.co.za" || host.endsWith(".dtech-services.co.za")
        } catch (e: Exception) {
            return false
        }
    }

    private inner class MyWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            url?.let {
                Log.d("WebViewClient", "onPageStarted: $it")
                syncPointsFromCookie()

                if (isInternalDomain(it)) {
                    isExternalSessionActive = false
                    lastInternalPageWasBerserker = it.contains("berserker.html")
                } else {
                    if (lastInternalPageWasBerserker && !isExternalSessionActive) {
                        isExternalSessionActive = true
                        addPendingPoint()
                    }
                }
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            Log.d("WebViewClient", "Loading URL: $url")

            if (url.startsWith("http://") || url.startsWith("https://")) {
                // Return false to let the WebView handle the URL normally
                return false
            }

            try {
                // Try to parse as intent
                if (url.startsWith("intent://")) {
                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    if (intent != null) {
                        val fallbackUrl = intent.getStringExtra("browser_fallback_url")

                        try {
                            // Try to start the activity
                            val info = packageManager.resolveActivity(intent, 0)
                            if (info != null) {
                                startActivity(intent)
                                return true
                            }
                        } catch (e: ActivityNotFoundException) {
                            Log.e("WebViewClient", "Activity not found for intent: $url")
                        }

                        if (fallbackUrl != null) {
                            view?.loadUrl(fallbackUrl)
                            return true
                        }
                    }
                    return true // Handled intent or fallback
                }

                // Try to launch other custom schemes
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.e("WebViewClient", "Failed to handle custom scheme: $url", e)
                return true // Handled gracefully by catching
            }
        }
    }

    private inner class MyWebChromeClient : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message?
        ): Boolean {
            val newWebView = WebView(this@MainActivity)
            setupWebView(newWebView)
            newWebView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            webViewContainer.addView(newWebView)
            popups.add(newWebView)

            val transport = resultMsg?.obj as? WebView.WebViewTransport
            transport?.webView = newWebView
            resultMsg?.sendToTarget()
            return true
        }

        override fun onCloseWindow(window: WebView?) {
            super.onCloseWindow(window)
            if (window != null) {
                closePopup(window)
            }
        }
    }

    private fun closePopup(popup: WebView) {
        webViewContainer.removeView(popup)
        popups.remove(popup)
        popup.destroy()
        // When popup is closed, user returns to main webview, session is over.
        isExternalSessionActive = false
    }

    override fun onBackPressed() {
        if (popups.isNotEmpty()) {
            val popup = popups.last()
            if (popup.canGoBack()) {
                popup.goBack()
            } else {
                closePopup(popup)
            }
            return
        }

        if (mainWebView.canGoBack()) {
            mainWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
