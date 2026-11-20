package com.filmemeu.app.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val TRAILER_ORIGIN = "https://com.filmemeu.app"

/**
 * Trailer do YouTube embutido com autoplay automatico e audio ligado.
 *
 * Usa iframe explicito com `allow="autoplay; encrypted-media"`, Referer HTTPS
 * valido (`https://com.filmemeu.app`) e User-Agent limpo para rodar liso no
 * WebView do Android (TV Box, Fire Stick, tablet e celular).
 *
 * Protegido contra falhas do processo de renderizacao do WebView
 * (`onRenderProcessGone`) e liberacao segura em `onRelease`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerEmbutido(
  videoKey: String,
  modifier: Modifier = Modifier,
  visivel: Boolean = true,
  mostrarControlesYoutube: Boolean = false,
  aoCriarWebView: (WebView?) -> Unit = {},
  aoMudarEstado: (pronto: Boolean, tocando: Boolean, mudo: Boolean) -> Unit = { _, _, _ -> }
) {
  val mainHandler = remember { Handler(Looper.getMainLooper()) }
  var renderFalhou by remember(videoKey) { mutableStateOf(false) }

  if (renderFalhou) return

  val html = remember(videoKey, mostrarControlesYoutube) {
    val seguro = videoKey.replace(Regex("[^a-zA-Z0-9_-]"), "")
    val controls = if (mostrarControlesYoutube) "1" else "0"
    val embedUrl = "https://www.youtube.com/embed/$seguro" +
      "?autoplay=1&mute=0&controls=$controls&rel=0&modestbranding=1&playsinline=1" +
      "&loop=1&playlist=$seguro&iv_load_policy=3&fs=0&disablekb=1&enablejsapi=1" +
      "&origin=https%3A%2F%2Fcom.filmemeu.app"

    """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
        <style>
          html, body {
            margin: 0; padding: 0; width: 100%; height: 100%;
            background: #070C17; overflow: hidden;
          }
          .palco {
            position: fixed; inset: 0; overflow: hidden;
          }
          #yt {
            position: absolute;
            top: 50%; left: 50%;
            width: 100vw; height: 56.25vw;
            min-height: 100vh; min-width: 177.78vh;
            transform: translate(-50%, -50%) scale(1.04);
            border: 0;
            pointer-events: none;
          }
        </style>
      </head>
      <body>
        <div class="palco">
          <iframe
            id="yt"
            src="$embedUrl"
            allow="autoplay; encrypted-media; fullscreen; picture-in-picture"
            referrerpolicy="strict-origin-when-cross-origin"
            frameborder="0">
          </iframe>
        </div>
        <script>
          var ytPlayer = null;
          var jaTocou = false;
          var teveErro = false;

          function notificar(pronto, tocando, mudo) {
            try {
              if (window.AndroidTrailer) {
                window.AndroidTrailer.onEstado(!!pronto, !!tocando, !!mudo);
              }
            } catch (_) {}
          }

          function tentarTocarComAudio() {
            if (!ytPlayer) return;
            try {
              ytPlayer.unMute();
              ytPlayer.setVolume(100);
              ytPlayer.playVideo();
            } catch (_) {}
          }

          // Assim que o iframe do YouTube carregar, revela o player (a menos que ocorra erro).
          var iframeEl = document.getElementById('yt');
          if (iframeEl) {
            iframeEl.addEventListener('load', function() {
              setTimeout(function() {
                if (!teveErro && !jaTocou) {
                  notificar(true, true, false);
                }
              }, 350);
            });
          }

          var tag = document.createElement('script');
          tag.src = 'https://www.youtube.com/iframe_api';
          document.head.appendChild(tag);

          function onYouTubeIframeAPIReady() {
            ytPlayer = new YT.Player('yt', {
              events: {
                onReady: function(e) {
                  if (teveErro) return;
                  tentarTocarComAudio();
                  notificar(true, true, false);
                  setTimeout(function() {
                    if (!ytPlayer || teveErro) return;
                    var st = ytPlayer.getPlayerState ? ytPlayer.getPlayerState() : -1;
                    if (st === 1) {
                      jaTocou = true;
                      var m = ytPlayer.isMuted ? ytPlayer.isMuted() : false;
                      notificar(true, true, m);
                    } else if (st !== 3) {
                      tentarTocarComAudio();
                      setTimeout(function() {
                        if (!ytPlayer || teveErro) return;
                        var st2 = ytPlayer.getPlayerState ? ytPlayer.getPlayerState() : -1;
                        if (st2 !== 1 && st2 !== 3) {
                          try {
                            ytPlayer.mute();
                            ytPlayer.playVideo();
                          } catch (_) {}
                        }
                      }, 1000);
                    }
                  }, 900);
                },
                onStateChange: function(e) {
                  if (teveErro) return;
                  var mudo = ytPlayer && ytPlayer.isMuted ? ytPlayer.isMuted() : false;
                  if (e.data === 1) {
                    jaTocou = true;
                    notificar(true, true, mudo);
                  } else if (e.data === 2) {
                    notificar(true, false, mudo);
                  } else if (e.data === 0) {
                    try { ytPlayer.playVideo(); } catch (_) {}
                  }
                },
                onError: function() {
                  teveErro = true;
                  notificar(false, false, false);
                }
              }
            });
          }

          window.tocarTrailer = function() {
            if (ytPlayer && ytPlayer.playVideo) ytPlayer.playVideo();
          };
          window.tocarComSom = function() {
            tentarTocarComAudio();
          };
          window.pausarTrailer = function() {
            if (ytPlayer && ytPlayer.pauseVideo) ytPlayer.pauseVideo();
          };
          window.mutarTrailer = function() {
            if (ytPlayer && ytPlayer.mute) {
              ytPlayer.mute();
              notificar(!teveErro, ytPlayer.getPlayerState && ytPlayer.getPlayerState() === 1, true);
            }
          };
          window.desmutarTrailer = function() {
            if (ytPlayer && ytPlayer.unMute) {
              ytPlayer.unMute();
              ytPlayer.setVolume(100);
              if (ytPlayer.getPlayerState && ytPlayer.getPlayerState() !== 1) {
                ytPlayer.playVideo();
              }
              notificar(!teveErro, true, false);
            }
          };
        </script>
      </body>
      </html>
    """.trimIndent()
  }

  AndroidView(
    factory = { ctx ->
      runCatching {
        WebView(ctx).apply {
          setBackgroundColor(android.graphics.Color.TRANSPARENT)
          alpha = if (visivel) 1f else 0f
          isFocusable = false
          isFocusableInTouchMode = false
          settings.javaScriptEnabled = true
          settings.domStorageEnabled = true
          settings.mediaPlaybackRequiresUserGesture = false
          settings.useWideViewPort = true
          settings.loadWithOverviewMode = true
          settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
          settings.userAgentString = settings.userAgentString.replace("; wv)", ")")
          runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(this, true) }
          webChromeClient = WebChromeClient()
          webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(
              view: WebView?,
              detail: RenderProcessGoneDetail?
            ): Boolean {
              mainHandler.post {
                aoCriarWebView(null)
                aoMudarEstado(false, false, false)
                renderFalhou = true
              }
              runCatching {
                (view?.parent as? ViewGroup)?.removeView(view)
                view?.destroy()
              }
              return true
            }
          }
          addJavascriptInterface(
            object {
              @JavascriptInterface
              fun onEstado(pronto: Boolean, tocando: Boolean, mudo: Boolean) {
                mainHandler.post {
                  aoMudarEstado(pronto, tocando, mudo)
                }
              }
            },
            "AndroidTrailer"
          )
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
          }
          loadDataWithBaseURL(
            TRAILER_ORIGIN,
            html,
            "text/html",
            "UTF-8",
            null
          )
          aoCriarWebView(this)
        }
      }.getOrElse {
        aoCriarWebView(null)
        View(ctx)
      }
    },
    update = { view ->
      if (view is WebView) {
        val alvoAlpha = if (visivel) 1f else 0f
        if (view.alpha != alvoAlpha) view.alpha = alvoAlpha
      }
    },
    onRelease = { view ->
      aoCriarWebView(null)
      if (view is WebView) {
        runCatching {
          view.removeJavascriptInterface("AndroidTrailer")
          view.stopLoading()
          view.loadUrl("about:blank")
          view.onPause()
          (view.parent as? ViewGroup)?.removeView(view)
          view.destroy()
        }
      }
    },
    modifier = modifier
  )
}
