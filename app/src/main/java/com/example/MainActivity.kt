package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {

  private var webView: WebView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    onBackPressedDispatcher.addCallback(
      this,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (webView?.canGoBack() == true) {
            webView?.goBack()
          } else {
            isEnabled = false
            onBackPressedDispatcher.onBackPressed()
          }
        }
      }
    )

    setContent {
      Surface(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFFE65100))
      ) {
        MantraSadhanaWebView(
          onWebViewCreated = { wv ->
            webView = wv
          }
        )
      }
    }
  }

  override fun onDestroy() {
    webView?.destroy()
    webView = null
    super.onDestroy()
  }
}

/**
 * Native bridge exposed to the web application as `window.AndroidBridge`.
 * Provides native hardware vibration and system integrations for APK wrapping.
 */
class WebAppInterface(private val context: Context) {

  @JavascriptInterface
  fun vibrate(durationMs: Long) {
    try {
      val duration = if (durationMs in 1..2000) durationMs else 40L
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager =
          context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        val vibrator = vibratorManager?.defaultVibrator
        vibrator?.vibrate(
          VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
        )
      } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator?.vibrate(
            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
          )
        } else {
          @Suppress("DEPRECATION")
          vibrator?.vibrate(duration)
        }
      }
    } catch (_: Exception) {
      // Graceful fallback
    }
  }

  @JavascriptInterface
  fun showToast(message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MantraSadhanaWebView(
  onWebViewCreated: (WebView) -> Unit,
  modifier: Modifier = Modifier
) {
  AndroidView(
    modifier = modifier.fillMaxSize(),
    factory = { ctx ->
      WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT
        )

        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          databaseEnabled = true
          allowFileAccess = true
          allowContentAccess = true
          loadWithOverviewMode = true
          useWideViewPort = true
          builtInZoomControls = false
          displayZoomControls = false
          cacheMode = WebSettings.LOAD_DEFAULT
          mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // Bridge for native vibration
        addJavascriptInterface(WebAppInterface(ctx), "AndroidBridge")

        webChromeClient = object : WebChromeClient() {
          override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
          ): Boolean {
            return super.onJsAlert(view, url, message, result)
          }

          override fun onJsConfirm(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
          ): Boolean {
            return super.onJsConfirm(view, url, message, result)
          }
        }

        webViewClient = object : WebViewClient() {
          override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: android.webkit.WebResourceRequest?
          ): Boolean {
            val url = request?.url?.toString() ?: return false
            if (url.startsWith("https://wa.me") || url.startsWith("whatsapp://")) {
              try {
                val intent = android.content.Intent(
                  android.content.Intent.ACTION_VIEW,
                  android.net.Uri.parse(url)
                )
                ctx.startActivity(intent)
                return true
              } catch (_: Exception) {
                Toast.makeText(ctx, "व्हॉट्सॲप उघडता आले नाही", Toast.LENGTH_SHORT).show()
              }
            }
            return false
          }
        }

        loadUrl("file:///android_asset/index.html")
        onWebViewCreated(this)
      }
    }
  )
}
