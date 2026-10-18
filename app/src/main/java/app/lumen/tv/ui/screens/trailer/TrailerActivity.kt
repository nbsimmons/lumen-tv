package app.lumen.tv.ui.screens.trailer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.lumen.tv.R

class TrailerActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var hint: View
    private val hideHint = Runnable { hint.animate().alpha(0f).setDuration(HINT_FADE_MS).start() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val key = intent.getStringExtra(EXTRA_KEY)?.takeIf { VIDEO_ID.matches(it) } ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()

        webView = WebView(this).apply {
            isFocusable = false
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            addJavascriptInterface(Bridge(), "Lumen")
            loadDataWithBaseURL(EMBED_ORIGIN, embedHtml(key), "text/html", "utf-8", null)
        }
        hint = buildHint(title)
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(webView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                addView(hint, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
            },
        )
        showHint()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> js("togglePlay()")
            KeyEvent.KEYCODE_MEDIA_PLAY -> js("player.playVideo()")
            KeyEvent.KEYCODE_MEDIA_PAUSE -> js("player.pauseVideo()")
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> js("seekBy(-$SEEK_SECONDS)")
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> js("seekBy($SEEK_SECONDS)")
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> Unit
            else -> return super.dispatchKeyEvent(event)
        }
        showHint()
        return true
    }

    override fun onPause() {
        super.onPause()
        js("player && player.pauseVideo()")
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun js(script: String) {
        if (::webView.isInitialized) webView.evaluateJavascript(script, null)
    }

    private fun showHint() {
        hint.removeCallbacks(hideHint)
        hint.animate().cancel()
        hint.alpha = 1f
        hint.postDelayed(hideHint, HINT_TIMEOUT_MS)
    }

    private fun buildHint(title: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val density = resources.displayMetrics.density
        setPadding((48 * density).toInt(), (32 * density).toInt(), 0, 0)
        addView(text(getString(R.string.trailer_label), 15f, 0xFFA1A1AA.toInt()))
        addView(text(title, 28f, 0xFFF5F5F7.toInt()))
        addView(text(getString(R.string.trailer_controls), 13f, 0xFF6B6B75.toInt()))
    }

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun onState(state: Int) {
            if (state == 0) runOnUiThread { finish() }
        }

        @JavascriptInterface
        fun onError() {
            runOnUiThread {
                android.widget.Toast.makeText(this@TrailerActivity, R.string.trailer_error, android.widget.Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_KEY = "key"
        private const val EXTRA_TITLE = "title"
        private const val SEEK_SECONDS = 10
        private const val HINT_TIMEOUT_MS = 4_000L
        private const val HINT_FADE_MS = 400L
        private const val EMBED_ORIGIN = "https://lumen.local"
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

        fun intent(context: Context, key: String, title: String): Intent =
            Intent(context, TrailerActivity::class.java).putExtra(EXTRA_KEY, key).putExtra(EXTRA_TITLE, title)

        private fun embedHtml(videoKey: String): String = """
            <!doctype html>
            <html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{position:absolute;inset:0;width:100%;height:100%}</style>
            </head><body>
            <div id="p"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
            var player;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('p', {
                videoId: '$videoKey',
                host: 'https://www.youtube-nocookie.com',
                playerVars: { autoplay: 1, controls: 0, rel: 0, playsinline: 1, iv_load_policy: 3, fs: 0, disablekb: 1, origin: '$EMBED_ORIGIN' },
                events: {
                  onReady: function(e) { e.target.playVideo(); },
                  onStateChange: function(e) { Lumen.onState(e.data); },
                  onError: function() { Lumen.onError(); }
                }
              });
            }
            function togglePlay() { if (!player) return; player.getPlayerState() === 1 ? player.pauseVideo() : player.playVideo(); }
            function seekBy(s) { if (!player) return; player.seekTo(Math.max(0, player.getCurrentTime() + s), true); }
            </script>
            </body></html>
        """.trimIndent()
    }
}
