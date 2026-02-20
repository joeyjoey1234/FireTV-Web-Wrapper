package tv.firetvwebwrapper.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
  private lateinit var webView: WebView
  private lateinit var progressBar: ProgressBar
  private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(this) }
  private var lastLoadedRoot: String? = null
  private var lastUserAgentMode: String? = null
  private val navigationHistory = mutableListOf<String>()
  private var remoteActivationInProgress = false

  private data class ActivationTargetInfo(
    val x: Double,
    val y: Double,
    val dpr: Double,
    val toggleState: String?
  )

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val serverUrl = prefs.getString(Prefs.KEY_SERVER_URL, "").orEmpty().trim()
    if (serverUrl.isBlank()) {
      startActivity(Intent(this, SetupActivity::class.java))
      finish()
      return
    }

    setContentView(R.layout.activity_main)
    webView = findViewById(R.id.web_view)
    progressBar = findViewById(R.id.loading_progress)

    if (BuildConfig.DEBUG) {
      WebView.setWebContentsDebuggingEnabled(true)
    }

    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    settings.allowFileAccess = false
    settings.allowContentAccess = false

    webView.isFocusable = true
    webView.isFocusableInTouchMode = true

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (url.startsWith("http://") || url.startsWith("https://")) {
          if (!isHttpAllowed() && url.startsWith("http://")) {
            Toast.makeText(this@MainActivity, R.string.http_blocked, Toast.LENGTH_LONG).show()
            return true
          }
          return false
        }
        return openExternal(request.url)
      }

      override fun onPageFinished(view: WebView, url: String) {
        trackNavigation(url)
        injectTvHelpers(view)
      }

      override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
        if (isInvalidSslAllowed()) {
          handler.proceed()
        } else {
          handler.cancel()
          Toast.makeText(this@MainActivity, R.string.ssl_blocked, Toast.LENGTH_LONG).show()
        }
      }
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView, newProgress: Int) {
        if (newProgress >= 100) {
          progressBar.visibility = View.GONE
        } else {
          if (progressBar.visibility != View.VISIBLE) {
            progressBar.visibility = View.VISIBLE
          }
          progressBar.progress = newProgress
        }
      }
    }

    applyUserAgent()
    loadRoot(serverUrl)
  }

  override fun onResume() {
    super.onResume()
    val currentUrl = prefs.getString(Prefs.KEY_SERVER_URL, "").orEmpty().trim()
    if (currentUrl.isBlank()) {
      startActivity(Intent(this, SetupActivity::class.java))
      finish()
      return
    }

    val currentUaMode = getUserAgentMode()
    if (lastUserAgentMode != currentUaMode) {
      applyUserAgent()
      if (lastLoadedRoot == currentUrl) {
        webView.reload()
      }
    }

    if (lastLoadedRoot != currentUrl) {
      loadRoot(currentUrl)
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
        openSettings()
        return true
      }
      KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
      KeyEvent.KEYCODE_MEDIA_PLAY,
      KeyEvent.KEYCODE_MEDIA_PAUSE -> {
        if ((event?.repeatCount ?: 0) == 0) {
          forceToggleFocusedControlInWebView()
        }
        return true
      }
      KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_BUTTON_R1 -> {
        if (webView.canGoForward()) {
          webView.goForward()
        } else {
          Toast.makeText(this, "No forward history", Toast.LENGTH_SHORT).show()
        }
        return true
      }
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (isSelectKey(event.keyCode)) {
      webView.requestFocus()
      webView.dispatchKeyEvent(KeyEvent(event))
      if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
        activateFocusedElementInWebView(event, skipDirectForward = true)
      }
      return true
    }
    return super.dispatchKeyEvent(event)
  }

  override fun onBackPressed() {
    if (webView.canGoBack() && !isCurrentPageLoginRelated()) {
      val previousUrl = getPreviousUrl()
      if (previousUrl == null || !isLoginUrl(previousUrl)) {
        webView.goBack()
      } else {
        showExitDialog()
      }
    } else {
      showExitDialog()
    }
  }

  private fun loadRoot(url: String) {
    if (!isHttpAllowed() && url.startsWith("http://")) {
      Toast.makeText(this, R.string.http_blocked, Toast.LENGTH_LONG).show()
      openSettings()
      return
    }
    lastLoadedRoot = url
    webView.loadUrl(url)
  }

  private fun applyUserAgent() {
    val mode = getUserAgentMode()
    val base = WebSettings.getDefaultUserAgent(this)
    val ua = when (mode) {
      Prefs.UA_MODE_MOBILE -> {
        if (!base.contains("Mobile", ignoreCase = true)) {
          "$base Mobile"
        } else {
          base
        }
      }
      Prefs.UA_MODE_DESKTOP -> {
        base.replace(" Mobile", "").replace("Mobile ", "")
      }
      else -> base
    }
    webView.settings.userAgentString = ua
    lastUserAgentMode = mode
  }

  private fun getUserAgentMode(): String {
    val mode = prefs.getString(Prefs.KEY_USER_AGENT_MODE, null)
    if (mode != null) return mode
    
    val legacyForceMobile = prefs.getBoolean(Prefs.KEY_FORCE_MOBILE_UA, true)
    val migratedMode = if (legacyForceMobile) Prefs.UA_MODE_MOBILE else Prefs.UA_MODE_AUTO
    prefs.edit().putString(Prefs.KEY_USER_AGENT_MODE, migratedMode).apply()
    return migratedMode
  }

  private fun isHttpAllowed(): Boolean = prefs.getBoolean(Prefs.KEY_ALLOW_HTTP, true)

  private fun isInvalidSslAllowed(): Boolean = prefs.getBoolean(Prefs.KEY_ALLOW_INVALID_SSL, false)

  private fun openSettings() {
    startActivity(Intent(this, SettingsActivity::class.java))
  }

  private fun openExternal(uri: Uri): Boolean {
    return try {
      startActivity(Intent(Intent.ACTION_VIEW, uri))
      true
    } catch (_: Exception) {
      true
    }
  }

  private fun showExitDialog() {
    AlertDialog.Builder(this)
      .setTitle("Exit App?")
      .setMessage("You can open Settings to change your server URL or options.")
      .setPositiveButton("Settings") { _, _ -> openSettings() }
      .setNegativeButton("Exit") { _, _ -> finish() }
      .setNeutralButton("Cancel", null)
      .show()
  }

  private fun trackNavigation(url: String) {
    if (navigationHistory.isEmpty() || navigationHistory.last() != url) {
      navigationHistory.add(url)
      if (navigationHistory.size > 50) {
        navigationHistory.removeAt(0)
      }
    }
  }

  private fun getPreviousUrl(): String? {
    return if (navigationHistory.size >= 2) {
      navigationHistory[navigationHistory.size - 2]
    } else null
  }

  private fun isCurrentPageLoginRelated(): Boolean {
    return isLoginUrl(webView.url ?: "")
  }

  private fun isLoginUrl(url: String): Boolean {
    val lowerUrl = url.lowercase()
    return lowerUrl.contains("/login") || 
           lowerUrl.contains("/auth") || 
           lowerUrl.contains("/signin") ||
           lowerUrl.contains("/sign-in")
  }

  private fun isSelectKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A
  }

  private fun activateFocusedElementInWebView(originalEvent: KeyEvent?, skipDirectForward: Boolean = false) {
    if (remoteActivationInProgress) return
    remoteActivationInProgress = true
    webView.requestFocus()

    readActivationTargetInfo beforeCb@{ beforeInfo ->
      if (skipDirectForward) {
        webView.postDelayed({
          readActivationTargetInfo postDirectCb@{ afterDirect ->
            if (beforeInfo != null && didToggleStateChange(beforeInfo, afterDirect)) {
              remoteActivationInProgress = false
              return@postDirectCb
            }
            // If the focused control is not a toggle, the real Select event already
            // went to WebView. Do not run synthetic fallbacks that can misfire.
            if (beforeInfo?.toggleState == null) {
              remoteActivationInProgress = false
              return@postDirectCb
            }
            runActivationFallback(beforeInfo)
          }
        }, 90)
        return@beforeCb
      }

      val handledByWebView = dispatchOriginalSelectToWebView(originalEvent)
      if (handledByWebView) {
        webView.postDelayed({
          readActivationTargetInfo afterForwardCb@{ afterForward ->
            if (beforeInfo != null && didToggleStateChange(beforeInfo, afterForward)) {
              remoteActivationInProgress = false
              return@afterForwardCb
            }
            runActivationFallback(beforeInfo)
          }
        }, 80)
      } else {
        runActivationFallback(beforeInfo)
      }
    }
  }

  private fun runActivationFallback(info: ActivationTargetInfo?) {
    if (info == null || info.toggleState == null) {
      runJsActivateFocused {
        remoteActivationInProgress = false
      }
      return
    }

    dispatchNativeKeyToWebView(KeyEvent.KEYCODE_SPACE)
    webView.postDelayed({
      readActivationTargetInfo afterSpaceCb@{ afterSpace ->
        if (didToggleStateChange(info, afterSpace)) {
          remoteActivationInProgress = false
          return@afterSpaceCb
        }

        dispatchNativeTapToWebView(info, useScaledCoordinates = false)
        webView.postDelayed({
          readActivationTargetInfo afterRawTapCb@{ afterRawTap ->
            if (didToggleStateChange(info, afterRawTap)) {
              remoteActivationInProgress = false
              return@afterRawTapCb
            }

            if (info.dpr > 1.01) {
              dispatchNativeTapToWebView(info, useScaledCoordinates = true)
              webView.postDelayed({
                readActivationTargetInfo afterScaledTapCb@{ afterScaledTap ->
                  if (didToggleStateChange(info, afterScaledTap)) {
                    remoteActivationInProgress = false
                    return@afterScaledTapCb
                  }

                  forceToggleFocusedControlInWebView {
                    remoteActivationInProgress = false
                  }
                }
              }, 80)
              return@afterRawTapCb
            }

            forceToggleFocusedControlInWebView {
              remoteActivationInProgress = false
            }
          }
        }, 90)
      }
    }, 80)
  }

  private fun dispatchOriginalSelectToWebView(originalEvent: KeyEvent?): Boolean {
    val normalizedKeyCode = when (originalEvent?.keyCode) {
      KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
      KeyEvent.KEYCODE_DPAD_CENTER -> KeyEvent.KEYCODE_DPAD_CENTER
      else -> KeyEvent.KEYCODE_DPAD_CENTER
    }
    val metaState = originalEvent?.metaState ?: 0
    val flags = (originalEvent?.flags ?: 0) or KeyEvent.FLAG_FROM_SYSTEM
    val source = originalEvent?.source ?: InputDevice.SOURCE_DPAD
    val deviceId = originalEvent?.deviceId?.takeIf { it >= 0 } ?: -1
    val scanCode = originalEvent?.scanCode ?: 0
    val candidateCodes = listOf(
      normalizedKeyCode,
      KeyEvent.KEYCODE_ENTER,
      KeyEvent.KEYCODE_NUMPAD_ENTER,
      KeyEvent.KEYCODE_SPACE
    ).distinct()

    var handled = false
    for (code in candidateCodes) {
      handled = dispatchNativeKeyToWebView(
        keyCode = code,
        metaState = metaState,
        flags = flags,
        source = source,
        deviceId = deviceId,
        scanCode = scanCode
      ) || handled
    }
    return handled
  }

  private fun runJsActivateFocused(onComplete: (() -> Unit)? = null) {
    val script = """
      (function() {
        try {
          if (window.__jstvActivateFocused && window.__jstvActivateFocused()) {
            return true;
          }
          return false;
        } catch (e) {
          return false;
        }
      })();
    """.trimIndent()
    webView.evaluateJavascript(script) {
      onComplete?.invoke()
    }
  }

  private fun forceToggleFocusedControlInWebView(onComplete: (() -> Unit)? = null) {
    val script = """
      (function() {
        try {
          if (window.__jstvForceToggleFocused) {
            window.__jstvForceToggleFocused();
            return;
          }
          if (window.__jstvActivateFocused) {
            window.__jstvActivateFocused();
          }
        } catch (e) {
          // no-op
        }
      })();
    """.trimIndent()
    webView.evaluateJavascript(script) {
      onComplete?.invoke()
    }
  }

  private fun readActivationTargetInfo(onResult: (ActivationTargetInfo?) -> Unit) {
    val script = """
      (function() {
        try {
          if (!window.__jstvGetActivationInfo) return null;
          return window.__jstvGetActivationInfo();
        } catch (e) {
          return null;
        }
      })();
    """.trimIndent()

    webView.evaluateJavascript(script) { raw ->
      val value = raw?.trim()
      if (value.isNullOrEmpty() || value == "null" || value == "\"null\"") {
        onResult(null)
        return@evaluateJavascript
      }

      try {
        val obj = JSONObject(value)
        val x = obj.optDouble("x", Double.NaN)
        val y = obj.optDouble("y", Double.NaN)
        if (!x.isFinite() || !y.isFinite()) {
          onResult(null)
          return@evaluateJavascript
        }
        val dpr = obj.optDouble("dpr", 1.0).let { if (it.isFinite() && it > 0) it else 1.0 }
        val state = obj.optString("state", "").takeIf { it == "true" || it == "false" }
        onResult(ActivationTargetInfo(x = x, y = y, dpr = dpr, toggleState = state))
      } catch (_: Exception) {
        onResult(null)
      }
    }
  }

  private fun dispatchNativeKeyToWebView(
    keyCode: Int,
    metaState: Int = 0,
    flags: Int = KeyEvent.FLAG_FROM_SYSTEM,
    source: Int = InputDevice.SOURCE_DPAD,
    deviceId: Int = -1,
    scanCode: Int = 0
  ): Boolean {
    val downTime = SystemClock.uptimeMillis()
    val down = KeyEvent(
      downTime,
      downTime,
      KeyEvent.ACTION_DOWN,
      keyCode,
      0,
      metaState,
      deviceId,
      scanCode,
      flags,
      source
    )
    val up = KeyEvent(
      downTime,
      SystemClock.uptimeMillis(),
      KeyEvent.ACTION_UP,
      keyCode,
      0,
      metaState,
      deviceId,
      scanCode,
      flags,
      source
    )
    val handledDown = webView.dispatchKeyEvent(down)
    val handledUp = webView.dispatchKeyEvent(up)
    return handledDown || handledUp
  }

  private fun dispatchNativeTapToWebView(info: ActivationTargetInfo, useScaledCoordinates: Boolean) {
    if (webView.width <= 0 || webView.height <= 0) return

    val maxX = (webView.width - 1).coerceAtLeast(1).toFloat()
    val maxY = (webView.height - 1).coerceAtLeast(1).toFloat()
    val rawX = info.x.toFloat()
    val rawY = info.y.toFloat()
    val scaledX = (info.x * info.dpr).toFloat()
    val scaledY = (info.y * info.dpr).toFloat()
    val x = if (useScaledCoordinates) scaledX else rawX
    val y = if (useScaledCoordinates) scaledY else rawY
    val clampedX = x.coerceIn(1f, maxX)
    val clampedY = y.coerceIn(1f, maxY)

    val downTime = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, clampedX, clampedY, 0)
    val up = MotionEvent.obtain(downTime, downTime + 40L, MotionEvent.ACTION_UP, clampedX, clampedY, 0)
    down.source = InputDevice.SOURCE_TOUCHSCREEN
    up.source = InputDevice.SOURCE_TOUCHSCREEN

    webView.dispatchTouchEvent(down)
    webView.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
  }

  private fun didToggleStateChange(before: ActivationTargetInfo, after: ActivationTargetInfo?): Boolean {
    val beforeState = before.toggleState ?: return false
    val afterState = after?.toggleState ?: return false
    return beforeState != afterState
  }

  private fun injectTvHelpers(view: WebView) {
    val css = """
      :focus {
        outline: 3px solid #00b3ff !important;
        outline-offset: 2px !important;
      }
      :focus-visible {
        outline: 3px solid #00b3ff !important;
        outline-offset: 2px !important;
      }
      a:focus, button:focus, input:focus, select:focus, textarea:focus, [role=button]:focus, [tabindex]:focus {
        box-shadow: 0 0 0 3px rgba(0, 179, 255, 0.7) !important;
        border-radius: 6px !important;
      }
    """.trimIndent()

    val script = """
      (function() {
        try {
          if (window.__jstvInjected) return;
          window.__jstvInjected = true;

          var cssText = ${JSONObject.quote(css)};
          if (!document.getElementById('jstv-focus-style')) {
            var style = document.createElement('style');
            style.id = 'jstv-focus-style';
            style.type = 'text/css';
            style.appendChild(document.createTextNode(cssText));
            document.head.appendChild(style);
          }

          var clickTracker = { element: null, count: 0, timer: null };
          var suppressSyntheticKeyHandling = false;
          var focusSelector = '[tabindex], a, button, input, textarea, select, [role="button"], [role="link"], [role="checkbox"], [role="switch"], [role="slider"], [aria-checked], [onclick]';

          function isVisible(el) {
            if (!el) return false;
            var rect = el.getBoundingClientRect();
            return rect.width > 0 && rect.height > 0;
          }

          function ensureFocusable() {
            var nodes = document.querySelectorAll(focusSelector);
            for (var i = 0; i < nodes.length; i++) {
              var el = nodes[i];
              if (el.getAttribute('tabindex') === null && !el.hasAttribute('disabled')) {
                el.setAttribute('tabindex', '0');
              }
            }
          }

          function isInModal(el) {
            if (!el) return false;
            var current = el;
            while (current && current !== document.body) {
              var styles = window.getComputedStyle(current);
              var zIndex = parseInt(styles.zIndex, 10);
              if (zIndex > 100) return true;
              
              var role = current.getAttribute('role');
              if (role === 'dialog' || role === 'alertdialog' || role === 'modal') return true;
              
              var ariaModal = current.getAttribute('aria-modal');
              if (ariaModal === 'true') return true;
              
              var classes = current.className || '';
              if (typeof classes === 'string' && 
                  (classes.includes('modal') || classes.includes('dialog') || classes.includes('popup'))) {
                return true;
              }
              
              current = current.parentElement;
            }
            return false;
          }

          function getTopModal() {
            var modals = document.querySelectorAll('[role="dialog"], [role="alertdialog"], [aria-modal="true"], .modal, .dialog');
            var topModal = null;
            var highestZ = -1;
            
            for (var i = 0; i < modals.length; i++) {
              var modal = modals[i];
              if (!isVisible(modal)) continue;
              var zIndex = parseInt(window.getComputedStyle(modal).zIndex, 10) || 0;
              if (zIndex > highestZ) {
                highestZ = zIndex;
                topModal = modal;
              }
            }
            return topModal;
          }

          function getFocusable() {
            var topModal = getTopModal();
            var searchRoot = topModal || document;
            
            var nodes = searchRoot.querySelectorAll(focusSelector);
            var out = [];
            
            for (var i = 0; i < nodes.length; i++) {
              var el = nodes[i];
              if (el.hasAttribute('disabled')) continue;
              if (el.getAttribute('aria-hidden') === 'true') continue;
              if (!isVisible(el)) continue;
              
              if (topModal && !topModal.contains(el)) continue;
              
              out.push(el);
            }
            return out;
          }

          function center(rect) {
            return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
          }

          function isTextInput(el) {
            if (!el) return false;
            var tag = (el.tagName || '').toLowerCase();
            return tag === 'input' || tag === 'textarea' || el.isContentEditable;
          }

          function handlesArrowKeys(el) {
            if (!el) return false;
            var tag = (el.tagName || '').toLowerCase();
            if (tag === 'input') {
              var inputType = (el.type || '').toLowerCase();
              return inputType === 'range' || inputType === 'number' || inputType === 'date' || inputType === 'time';
            }
            var role = ((el.getAttribute && el.getAttribute('role')) || '').toLowerCase();
            return role === 'slider' || role === 'spinbutton';
          }

          function isActivationKey(e) {
            if (!e) return false;
            if (e.key === 'Enter' || e.key === 'NumpadEnter' || e.key === ' ' || e.code === 'Space') return true;
            if (e.keyCode === 13 || e.keyCode === 32 || e.keyCode === 23 || e.keyCode === 66) return true;
            return false;
          }

          function findNext(direction) {
            var focusable = getFocusable();
            if (!focusable.length) return null;
            var active = document.activeElement;
            if (!active || active === document.body || focusable.indexOf(active) === -1) {
              return focusable[0];
            }

            var activeRect = active.getBoundingClientRect();
            var activeCenter = center(activeRect);
            var best = null;
            var bestScore = Number.POSITIVE_INFINITY;

            for (var i = 0; i < focusable.length; i++) {
              var el = focusable[i];
              if (el === active) continue;
              var rect = el.getBoundingClientRect();
              var c = center(rect);
              var dx = c.x - activeCenter.x;
              var dy = c.y - activeCenter.y;

              if (direction === 'left' && dx >= -4) continue;
              if (direction === 'right' && dx <= 4) continue;
              if (direction === 'up' && dy >= -4) continue;
              if (direction === 'down' && dy <= 4) continue;

              var primary = direction === 'left' || direction === 'right' ? Math.abs(dx) : Math.abs(dy);
              var secondary = direction === 'left' || direction === 'right' ? Math.abs(dy) : Math.abs(dx);
              var score = primary * primary + secondary * secondary * 2;

              if (score < bestScore) {
                bestScore = score;
                best = el;
              }
            }

            return best;
          }

          function moveFocus(direction) {
            var next = findNext(direction);
            if (next) {
              next.focus();
              if (next.scrollIntoView) {
                next.scrollIntoView({ block: 'center', inline: 'center' });
              }
            }
          }

          function resolveClickTarget(el) {
            if (!el) return null;
            var targetSelector = 'input[type="checkbox"], input[type="radio"], input[type="range"], button, a[href], [role="switch"], [role="checkbox"], [role="button"], [role="slider"], label';
            if (el.matches && el.matches(targetSelector)) return el;
            if (el.hasAttribute && el.getAttribute('role') === 'link') {
              var nestedLink = el.querySelector('a[href]');
              if (nestedLink) return nestedLink;
            }
            var descendant = el.querySelector ? el.querySelector(targetSelector) : null;
            if (descendant) return descendant;
            var ancestor = el.closest ? el.closest(targetSelector) : null;
            return ancestor || el;
          }

          function dispatchPointerSequence(target) {
            var rect = target.getBoundingClientRect();
            var cx = Math.round(rect.left + rect.width / 2);
            var cy = Math.round(rect.top + rect.height / 2);

            if (typeof PointerEvent !== 'undefined') {
              var pointerDown = new PointerEvent('pointerdown', {
                view: window,
                bubbles: true,
                cancelable: true,
                pointerId: 1,
                pointerType: 'mouse',
                clientX: cx,
                clientY: cy,
                button: 0,
                buttons: 1
              });
              var pointerUp = new PointerEvent('pointerup', {
                view: window,
                bubbles: true,
                cancelable: true,
                pointerId: 1,
                pointerType: 'mouse',
                clientX: cx,
                clientY: cy,
                button: 0,
                buttons: 0
              });
              target.dispatchEvent(pointerDown);
              target.dispatchEvent(pointerUp);
            }

            var mousedownEvent = new MouseEvent('mousedown', {
              view: window,
              bubbles: true,
              cancelable: true,
              clientX: cx,
              clientY: cy,
              button: 0,
              buttons: 1
            });
            target.dispatchEvent(mousedownEvent);

            var mouseupEvent = new MouseEvent('mouseup', {
              view: window,
              bubbles: true,
              cancelable: true,
              clientX: cx,
              clientY: cy,
              button: 0,
              buttons: 0
            });
            target.dispatchEvent(mouseupEvent);
          }

          function readToggleState(target) {
            if (!target) return null;
            if (target.matches && target.matches('input[type="checkbox"], input[type="radio"]')) {
              return target.checked ? 'true' : 'false';
            }
            var aria = target.getAttribute ? target.getAttribute('aria-checked') : null;
            if (aria === 'true' || aria === 'false') {
              return aria;
            }
            return null;
          }

          function dispatchKeySequence(target, keyDef) {
            if (!target) return;
            var dispatchTargets = [];
            dispatchTargets.push(target);
            if (document.activeElement && dispatchTargets.indexOf(document.activeElement) === -1) {
              dispatchTargets.push(document.activeElement);
            }
            if (dispatchTargets.indexOf(document) === -1) dispatchTargets.push(document);
            if (dispatchTargets.indexOf(window) === -1) dispatchTargets.push(window);

            for (var i = 0; i < dispatchTargets.length; i++) {
              var keyTarget = dispatchTargets[i];
              try {
                keyTarget.dispatchEvent(new KeyboardEvent('keydown', {
                  key: keyDef.key,
                  code: keyDef.code,
                  keyCode: keyDef.keyCode,
                  which: keyDef.which,
                  bubbles: true,
                  cancelable: true
                }));
                keyTarget.dispatchEvent(new KeyboardEvent('keypress', {
                  key: keyDef.key,
                  code: keyDef.code,
                  keyCode: keyDef.keyCode,
                  which: keyDef.which,
                  bubbles: true,
                  cancelable: true
                }));
                keyTarget.dispatchEvent(new KeyboardEvent('keyup', {
                  key: keyDef.key,
                  code: keyDef.code,
                  keyCode: keyDef.keyCode,
                  which: keyDef.which,
                  bubbles: true,
                  cancelable: true
                }));
              } catch (_err) {
                // ignore dispatch failures for non-DOM targets
              }
            }
          }

          function dispatchKeyboardActivation(target) {
            if (!target) return;
            var keyDefs = [
              { key: ' ', code: 'Space', keyCode: 32, which: 32 },
              { key: 'Enter', code: 'Enter', keyCode: 13, which: 13 }
            ];
            var role = ((target.getAttribute && target.getAttribute('role')) || '').toLowerCase();
            var tag = (target.tagName || '').toLowerCase();
            var buttonLike = role === 'button' || tag === 'button' || tag === 'a';
            if (buttonLike) {
              keyDefs.push({ key: 'NumpadEnter', code: 'NumpadEnter', keyCode: 13, which: 13 });
            }
            suppressSyntheticKeyHandling = true;
            try {
              for (var i = 0; i < keyDefs.length; i++) {
                dispatchKeySequence(target, keyDefs[i]);
              }
            } finally {
              suppressSyntheticKeyHandling = false;
            }
          }

          function associatedLabelFor(target) {
            if (!target) return null;

            // Web form APIs first: control.labels is the canonical association list.
            if (target.labels && target.labels.length) {
              return target.labels[0];
            }

            if ((target.tagName || '').toLowerCase() === 'label') {
              return target;
            }

            if (target.closest) {
              var parentLabel = target.closest('label');
              if (parentLabel) return parentLabel;
            }

            var ariaLabelledBy = target.getAttribute ? target.getAttribute('aria-labelledby') : null;
            if (ariaLabelledBy) {
              var ids = ariaLabelledBy.trim().split(/\s+/);
              for (var i = 0; i < ids.length; i++) {
                var labelById = document.getElementById(ids[i]);
                if (labelById && (labelById.tagName || '').toLowerCase() === 'label') {
                  return labelById;
                }
              }
            }

            var id = target.id;
            if (!id) return null;
            try {
              if (window.CSS && typeof CSS.escape === 'function') {
                return document.querySelector('label[for="' + CSS.escape(id) + '"]');
              }
            } catch (_err) {
              // fallback below
            }
            var safeId = id.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
            return document.querySelector('label[for="' + safeId + '"]');
          }

          function findNativeFormControl(target) {
            if (!target) return null;
            if ((target.tagName || '').toLowerCase() === 'label' && target.control) {
              var labelledControl = target.control;
              if (labelledControl.matches && labelledControl.matches('input[type="checkbox"], input[type="radio"]')) {
                return labelledControl;
              }
            }
            if (target.matches && target.matches('input[type="checkbox"], input[type="radio"]')) {
              return target;
            }
            var nested = target.querySelector ? target.querySelector('input[type="checkbox"], input[type="radio"]') : null;
            if (nested) return nested;
            var label = associatedLabelFor(target);
            if (label && label.querySelector) {
              var inLabel = label.querySelector('input[type="checkbox"], input[type="radio"]');
              if (inLabel) return inLabel;
            }
            return null;
          }

          function tryNativeFormActivation(target) {
            var input = findNativeFormControl(target);
            if (input) {
              if (input.focus) input.focus();
              var before = readToggleState(input);
              if (typeof input.click === 'function') {
                input.click();
              }
              if (before !== null && readToggleState(input) !== before) {
                return true;
              }
              if (before !== null && typeof input.checked === 'boolean' && !input.disabled) {
                input.checked = !input.checked;
                input.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
                input.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
                if (readToggleState(input) !== before) {
                  return true;
                }
              }
              var label = associatedLabelFor(input);
              if (label && typeof label.click === 'function') {
                label.click();
                if (before !== null && readToggleState(input) !== before) {
                  return true;
                }
              }
            }
            return false;
          }

          function dispatchDragToggle(target) {
            if (!target) return;
            var rect = target.getBoundingClientRect();
            if (rect.width < 8 || rect.height < 8) return;
            var startX = Math.round(rect.left + Math.max(2, rect.width * 0.15));
            var endX = Math.round(rect.right - Math.max(2, rect.width * 0.15));
            var y = Math.round(rect.top + rect.height / 2);

            if (typeof PointerEvent !== 'undefined') {
              target.dispatchEvent(new PointerEvent('pointerdown', {
                view: window, bubbles: true, cancelable: true, pointerId: 1, pointerType: 'mouse',
                clientX: startX, clientY: y, button: 0, buttons: 1
              }));
              target.dispatchEvent(new PointerEvent('pointermove', {
                view: window, bubbles: true, cancelable: true, pointerId: 1, pointerType: 'mouse',
                clientX: endX, clientY: y, button: 0, buttons: 1
              }));
              target.dispatchEvent(new PointerEvent('pointerup', {
                view: window, bubbles: true, cancelable: true, pointerId: 1, pointerType: 'mouse',
                clientX: endX, clientY: y, button: 0, buttons: 0
              }));
            }

            target.dispatchEvent(new MouseEvent('mousedown', {
              view: window, bubbles: true, cancelable: true, clientX: startX, clientY: y, button: 0, buttons: 1
            }));
            target.dispatchEvent(new MouseEvent('mousemove', {
              view: window, bubbles: true, cancelable: true, clientX: endX, clientY: y, button: 0, buttons: 1
            }));
            target.dispatchEvent(new MouseEvent('mouseup', {
              view: window, bubbles: true, cancelable: true, clientX: endX, clientY: y, button: 0, buttons: 0
            }));
          }

          function pushUnique(list, el) {
            if (!el) return;
            if (list.indexOf(el) === -1) {
              list.push(el);
            }
          }

          function buildActivationTargets(seed) {
            var targets = [];
            if (!seed) return targets;

            var resolved = resolveClickTarget(seed) || seed;
            var semantic = getSemanticToggleTarget(resolved);
            pushUnique(targets, semantic);
            pushUnique(targets, resolved);
            pushUnique(targets, seed);

            var cell = semantic && semantic.closest ? semantic.closest('td,th') : null;
            if (cell) {
              pushUnique(targets, cell.querySelector ? cell.querySelector('[role="checkbox"], [role="switch"], [aria-checked], button, [onclick]') : null);
              pushUnique(targets, cell);
            }

            var row = semantic && semantic.closest ? semantic.closest('tr') : null;
            if (row) {
              pushUnique(targets, row.querySelector ? row.querySelector('[role="checkbox"], [role="switch"], [aria-checked], button, [onclick]') : null);
              pushUnique(targets, row);
            }

            var topModal = getTopModal();
            if (topModal) {
              pushUnique(targets, topModal.querySelector('tbody [role="checkbox"][tabindex], tbody [role="switch"][tabindex], tbody [aria-checked][tabindex]'));
            }

            var hitRectEl = semantic || resolved;
            if (hitRectEl && hitRectEl.getBoundingClientRect) {
              var rect = hitRectEl.getBoundingClientRect();
              var cx = rect.left + rect.width / 2;
              var cy = rect.top + rect.height / 2;
              var pointEl = document.elementFromPoint(cx, cy);
              pushUnique(targets, pointEl);
              if (pointEl && pointEl.closest) {
                pushUnique(targets, pointEl.closest('[role="checkbox"], [role="switch"], [aria-checked], td, th, tr, button'));
              }
            }

            var filtered = [];
            for (var i = 0; i < targets.length; i++) {
              var target = targets[i];
              if (!target) continue;
              if (target.hasAttribute && target.hasAttribute('disabled')) continue;
              if (target.getAttribute && target.getAttribute('aria-hidden') === 'true') continue;
              if (!isVisible(target)) continue;
              filtered.push(target);
            }
            return filtered;
          }

          function tryActivateTarget(target, clickDetail, rootBeforeState, rootSemanticTarget) {
            var semanticTarget = getSemanticToggleTarget(target);
            var localBeforeState = readToggleState(semanticTarget);
            var beforeState = localBeforeState !== null ? localBeforeState : rootBeforeState;

            if (tryNativeFormActivation(target)) {
              var afterNativeState = readToggleState(semanticTarget);
              if (beforeState !== null && afterNativeState !== null && afterNativeState !== beforeState) {
                return true;
              }
            }

            if (beforeState !== null) {
              dispatchKeyboardActivation(target);
              var afterKeyState = readToggleState(semanticTarget);
              if (afterKeyState !== null && afterKeyState !== beforeState) {
                return true;
              }
            }

            dispatchPointerSequence(target);
            if (typeof target.click === 'function') {
              target.click();
            } else {
              var clickEvent = new MouseEvent('click', {
                view: window,
                bubbles: true,
                cancelable: true,
                detail: clickDetail
              });
              target.dispatchEvent(clickEvent);
            }
            
            // Dispatch dblclick event for second click (common pattern for cards/tiles)
            if (clickDetail === 2) {
              var dblClickEvent = new MouseEvent('dblclick', {
                view: window,
                bubbles: true,
                cancelable: true,
                detail: 2
              });
              target.dispatchEvent(dblClickEvent);
            }

            var afterClickState = readToggleState(semanticTarget);
            if (beforeState !== null && afterClickState !== null && afterClickState !== beforeState) {
              return true;
            }

            if (beforeState !== null && afterClickState !== null && afterClickState === beforeState) {
              dispatchDragToggle(target);
              var afterDragState = readToggleState(semanticTarget);
              if (afterDragState !== null && afterDragState !== beforeState) {
                return true;
              }
            }

            if (rootBeforeState !== null && rootSemanticTarget) {
              var rootAfterState = readToggleState(rootSemanticTarget);
              if (rootAfterState !== null && rootAfterState !== rootBeforeState) {
                return true;
              }
            }
            return false;
          }

          function dispatchClick(el, clickCount) {
            var seed = resolveClickTarget(el) || el;
            if (!seed || (seed.hasAttribute && seed.hasAttribute('disabled'))) return;
            var rootSemanticTarget = getSemanticToggleTarget(seed);
            var rootBeforeState = readToggleState(rootSemanticTarget);

            for (var i = 0; i < clickCount; i++) {
              var targets = buildActivationTargets(seed);
              if (!targets.length) {
                targets = [seed];
              }

              for (var j = 0; j < targets.length; j++) {
                var target = targets[j];
                if (target.focus) {
                  target.focus();
                }
                if (tryActivateTarget(target, i + 1, rootBeforeState, rootSemanticTarget)) {
                  return;
                }
              }
            }
          }

          function getActivationTarget() {
            var active = document.activeElement;
            if (active && active !== document.body) {
              var activeRowCheckbox = active.closest ? active.closest('tr') : null;
              if (activeRowCheckbox && activeRowCheckbox.querySelector) {
                var rowCheckbox = activeRowCheckbox.querySelector('[role="checkbox"][tabindex], [role="switch"][tabindex], [aria-checked][tabindex]');
                if (rowCheckbox) return rowCheckbox;
              }
              var activeCell = active.closest ? active.closest('td,th') : null;
              if (activeCell && activeCell.querySelector) {
                var cellCheckbox = activeCell.querySelector('[role="checkbox"][tabindex], [role="switch"][tabindex], [aria-checked][tabindex]');
                if (cellCheckbox) return cellCheckbox;
              }
              return active;
            }
            var topModal = getTopModal();
            var searchRoot = topModal || document;
            if (topModal) {
              var seasonCheckbox = topModal.querySelector('tbody [role="checkbox"][tabindex], tbody [role="switch"][tabindex], tbody [aria-checked][tabindex]');
              if (seasonCheckbox) return seasonCheckbox;
            }
            return searchRoot.querySelector('[role="checkbox"][tabindex], [role="switch"][tabindex], [role="slider"][tabindex], button, a[href], input, textarea, select, [tabindex]');
          }

          function getSemanticToggleTarget(target) {
            var semanticTarget = resolveClickTarget(target) || target;
            if (!(semanticTarget.getAttribute && semanticTarget.getAttribute('aria-checked') !== null)) {
              var candidate = (semanticTarget.closest && semanticTarget.closest('[role="checkbox"], [role="switch"], [aria-checked]')) ||
                (semanticTarget.querySelector && semanticTarget.querySelector('[role="checkbox"], [role="switch"], [aria-checked]'));
              if (candidate) {
                semanticTarget = candidate;
              }
            }
            return semanticTarget;
          }

          window.__jstvGetActivationInfo = function() {
            var target = getActivationTarget();
            if (!target) return null;
            if (target.focus) target.focus();

            var clickTarget = resolveClickTarget(target) || target;
            var rect = clickTarget.getBoundingClientRect ? clickTarget.getBoundingClientRect() : null;
            if (!rect || rect.width <= 0 || rect.height <= 0) return null;

            var semanticTarget = getSemanticToggleTarget(target);
            var state = readToggleState(semanticTarget);
            return {
              x: Math.round(rect.left + rect.width / 2),
              y: Math.round(rect.top + rect.height / 2),
              dpr: window.devicePixelRatio || 1,
              state: state
            };
          };

          window.__jstvActivateFocused = function() {
            var target = getActivationTarget();
            if (!target) return false;
            if (target.focus) {
              target.focus();
            }
            dispatchClick(target, 1);
            return true;
          };

          window.__jstvForceToggleFocused = function() {
            var target = getActivationTarget();
            if (!target) return false;
            if (target.focus) {
              target.focus();
            }

            var semanticTarget = getSemanticToggleTarget(target);
            var beforeState = readToggleState(semanticTarget);
            dispatchClick(target, 1);
            var afterState = readToggleState(semanticTarget);

            if (beforeState !== null && afterState === beforeState &&
                semanticTarget.getAttribute && semanticTarget.getAttribute('aria-checked') !== null) {
              semanticTarget.setAttribute('aria-checked', beforeState === 'true' ? 'false' : 'true');
              semanticTarget.dispatchEvent(new Event('input', { bubbles: true, cancelable: true }));
              semanticTarget.dispatchEvent(new Event('change', { bubbles: true, cancelable: true }));
            }
            return true;
          };

          function handleEnter(e) {
            var active = document.activeElement;
            if (!active || active === document.body) return;
            
            if (clickTracker.element === active) {
              clickTracker.count++;
            } else {
              clickTracker.element = active;
              clickTracker.count = 1;
            }
            
            if (clickTracker.timer) {
              clearTimeout(clickTracker.timer);
            }
            
            clickTracker.timer = setTimeout(function() {
              clickTracker.element = null;
              clickTracker.count = 0;
            }, 500);
            
            dispatchClick(active, clickTracker.count);
            e.preventDefault();
          }

          function onKeyDown(e) {
            if (suppressSyntheticKeyHandling || e.isTrusted === false) {
              return;
            }

            // Let the browser handle all keys when typing in text inputs
            if (isTextInput(document.activeElement)) {
              // Only intercept Enter (submit) and Escape (close modal)
              if (e.key !== 'Enter' && e.key !== 'Escape') {
                return;
              }
            }

            if (handlesArrowKeys(document.activeElement) &&
                (e.key === 'ArrowUp' || e.key === 'ArrowDown' || e.key === 'ArrowLeft' || e.key === 'ArrowRight')) {
              return;
            }
            
            switch (e.key) {
              case 'Enter':
                handleEnter(e);
                break;
              case 'NumpadEnter':
                handleEnter(e);
                break;
              case ' ':
                handleEnter(e);
                break;
              case 'ArrowUp':
                e.preventDefault();
                moveFocus('up');
                break;
              case 'ArrowDown':
                e.preventDefault();
                moveFocus('down');
                break;
              case 'ArrowLeft':
                e.preventDefault();
                moveFocus('left');
                break;
              case 'ArrowRight':
                e.preventDefault();
                moveFocus('right');
                break;
              case 'Escape':
                var topModal = getTopModal();
                if (topModal) {
                  var closeBtn = topModal.querySelector('[aria-label*="close" i], [title*="close" i], .close, button[class*="close" i]');
                  if (closeBtn) {
                    closeBtn.click();
                  }
                }
                break;
              default:
                if (isActivationKey(e)) {
                  handleEnter(e);
                }
                break;
            }
          }

          document.addEventListener('keydown', onKeyDown, true);

          if (typeof MutationObserver !== 'undefined') {
            var observer = new MutationObserver(function(mutations) {
              ensureFocusable();
              
              // Only auto-focus into new modals if user is not actively typing
              if (isTextInput(document.activeElement)) return;
              
              for (var i = 0; i < mutations.length; i++) {
                for (var j = 0; j < mutations[i].addedNodes.length; j++) {
                  var node = mutations[i].addedNodes[j];
                  if (node.nodeType === 1) {
                    var topModal = getTopModal();
                    if (topModal) {
                      var focusable = topModal.querySelectorAll(focusSelector);
                      if (focusable.length > 0) {
                        focusable[0].focus();
                      }
                    }
                  }
                }
              }
            });
            observer.observe(document.documentElement, { childList: true, subtree: true });
          }

          ensureFocusable();
        } catch (e) {
          // no-op
        }
      })();
    """.trimIndent()

    view.evaluateJavascript(script, null)
  }
}
