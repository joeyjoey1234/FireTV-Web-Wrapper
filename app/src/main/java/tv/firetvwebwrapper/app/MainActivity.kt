package tv.firetvwebwrapper.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
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
      KeyEvent.KEYCODE_DPAD_CENTER,
      KeyEvent.KEYCODE_BUTTON_A -> {
        if ((event?.repeatCount ?: 0) == 0) {
          activateFocusedElementInWebView()
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

  private fun activateFocusedElementInWebView() {
    val script = """
      (function() {
        try {
          if (window.__jstvActivateFocused && window.__jstvActivateFocused()) {
            return;
          }
          var el = document.activeElement;
          if (!el || el === document.body) {
            el = document.querySelector('[role="checkbox"][tabindex], [role="switch"][tabindex], [role="slider"][tabindex], button, a[href], input, select, textarea, [tabindex]');
          }
          if (!el) return;
          if (typeof el.focus === 'function') {
            el.focus();
          }
          if (typeof PointerEvent !== 'undefined') {
            el.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, pointerId: 1, pointerType: 'mouse' }));
            el.dispatchEvent(new PointerEvent('pointerup', { bubbles: true, cancelable: true, pointerId: 1, pointerType: 'mouse' }));
          }
          el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true }));
          el.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true }));
          if (typeof el.click === 'function') {
            el.click();
          } else {
            el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, detail: 1 }));
          }
        } catch (e) {
          // no-op
        }
      })();
    """.trimIndent()
    webView.evaluateJavascript(script, null)
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
            if (typeof PointerEvent !== 'undefined') {
              var pointerDown = new PointerEvent('pointerdown', {
                view: window,
                bubbles: true,
                cancelable: true,
                pointerId: 1,
                pointerType: 'mouse'
              });
              var pointerUp = new PointerEvent('pointerup', {
                view: window,
                bubbles: true,
                cancelable: true,
                pointerId: 1,
                pointerType: 'mouse'
              });
              target.dispatchEvent(pointerDown);
              target.dispatchEvent(pointerUp);
            }

            var mousedownEvent = new MouseEvent('mousedown', {
              view: window,
              bubbles: true,
              cancelable: true
            });
            target.dispatchEvent(mousedownEvent);

            var mouseupEvent = new MouseEvent('mouseup', {
              view: window,
              bubbles: true,
              cancelable: true
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

          function dispatchKeyboardActivation(target) {
            if (!target) return;
            var keyDefs = [
              { key: ' ', code: 'Space', keyCode: 32, which: 32 },
              { key: 'Enter', code: 'Enter', keyCode: 13, which: 13 }
            ];
            for (var i = 0; i < keyDefs.length; i++) {
              var keyDef = keyDefs[i];
              var down = new KeyboardEvent('keydown', {
                key: keyDef.key,
                code: keyDef.code,
                keyCode: keyDef.keyCode,
                which: keyDef.which,
                bubbles: true,
                cancelable: true
              });
              var up = new KeyboardEvent('keyup', {
                key: keyDef.key,
                code: keyDef.code,
                keyCode: keyDef.keyCode,
                which: keyDef.which,
                bubbles: true,
                cancelable: true
              });
              target.dispatchEvent(down);
              target.dispatchEvent(up);
            }
          }

          function dispatchClick(el, clickCount) {
            var target = resolveClickTarget(el);
            if (!target || target.hasAttribute('disabled')) return;

            for (var i = 0; i < clickCount; i++) {
              var beforeState = readToggleState(target);
              if (beforeState !== null) {
                dispatchKeyboardActivation(target);
                var afterKeyState = readToggleState(target);
                if (afterKeyState !== null && afterKeyState !== beforeState) {
                  return;
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
                  detail: i + 1
                });
                target.dispatchEvent(clickEvent);
              }

              var afterClickState = readToggleState(target);
              if (beforeState !== null && afterClickState !== null && afterClickState !== beforeState) {
                return;
              }
            }
          }

          function getActivationTarget() {
            var active = document.activeElement;
            if (active && active !== document.body) {
              return active;
            }
            var topModal = getTopModal();
            var searchRoot = topModal || document;
            return searchRoot.querySelector('[role="checkbox"][tabindex], [role="switch"][tabindex], [role="slider"][tabindex], button, a[href], input, textarea, select, [tabindex]');
          }

          window.__jstvActivateFocused = function() {
            var target = getActivationTarget();
            if (!target) return false;
            if (target.focus) {
              target.focus();
            }
            dispatchClick(target, 1);
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
            
            dispatchClick(active, 1);
            e.preventDefault();
          }

          function onKeyDown(e) {
            if (isTextInput(document.activeElement) && 
                (e.key !== 'Enter' && e.key !== 'Escape')) {
              return;
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
            observer.observe(document.documentElement, { childList: true, subtree: true, attributes: true });
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
