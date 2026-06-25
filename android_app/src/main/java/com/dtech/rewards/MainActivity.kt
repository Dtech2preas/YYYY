package com.dtech.rewards

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var mainWebView: WebView
    private lateinit var webViewContainer: FrameLayout
    private val popups = mutableListOf<WebView>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webViewContainer = findViewById(R.id.webViewContainer)
        mainWebView = findViewById(R.id.webView)

        setupWebView(mainWebView)

        mainWebView.loadUrl("https://revenue.dtech-services.co.za")
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

        webView.webViewClient = MyWebViewClient()
        webView.webChromeClient = MyWebChromeClient()
    }

    private inner class MyWebViewClient : WebViewClient() {
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
