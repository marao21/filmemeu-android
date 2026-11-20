package com.filmemeu.app.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.filmemeu.app.data.Fontes
import com.filmemeu.app.data.Guardados
import com.filmemeu.app.data.Marca
import com.filmemeu.app.data.Progresso
import com.filmemeu.app.data.Tipo
import com.filmemeu.app.net.Http
import com.filmemeu.app.ui.Cor
import com.filmemeu.app.ui.Rodinha
import com.filmemeu.app.ui.Vao
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Player fullscreen do MovFlix, alinhado visualmente ao player da versao web:
 * - Tela cheia imersiva com barra inferior minimalista (titulo, play/pause,
 *   tempo 00:01 / 1:40:15, resolucao discreta, trilho com bolinha branca
 *   arrastavel/focavel e botao de expandir/ajustar tela).
 * - Funciona com toque no celular/tablet e com controle remoto em TV Box/Stick.
 */
class PlayerActivity : ComponentActivity() {

  companion object {
    private const val URL = "url"
    private const val REFERER = "referer"
    private const val TITULO = "titulo"
    private const val CARTAZ = "cartaz"
    private const val TIPO = "tipo"
    private const val ID = "id"
    private const val TEMPORADA = "temporada"
    private const val EPISODIO = "episodio"
    private const val POSICAO = "posicao"

    fun abrir(
      ctx: Context,
      url: String,
      referer: String?,
      titulo: String,
      cartaz: String?,
      tipo: Tipo,
      id: Int,
      temporada: Int,
      episodio: Int,
      posicao: Long
    ) {
      val i = Intent(ctx, PlayerActivity::class.java)
      i.putExtra(URL, url)
      i.putExtra(REFERER, referer)
      i.putExtra(TITULO, titulo)
      i.putExtra(CARTAZ, cartaz)
      i.putExtra(TIPO, tipo.chave)
      i.putExtra(ID, id)
      i.putExtra(TEMPORADA, temporada)
      i.putExtra(EPISODIO, episodio)
      i.putExtra(POSICAO, posicao)
      ctx.startActivity(i)
    }
  }

  override fun onCreate(estado: Bundle?) {
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )
    super.onCreate(estado)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    ativarTelaCheiaImersiva()

    val url = intent.getStringExtra(URL) ?: run { finish(); return }
    val referer = intent.getStringExtra(REFERER)
    val titulo = intent.getStringExtra(TITULO) ?: ""
    val cartaz = intent.getStringExtra(CARTAZ)
    val tipo = Tipo.de(intent.getStringExtra(TIPO))
    val id = intent.getIntExtra(ID, 0)
    val temporada = intent.getIntExtra(TEMPORADA, 1)
    val episodio = intent.getIntExtra(EPISODIO, 1)
    val posicao = intent.getLongExtra(POSICAO, 0L)

    setContent {
      Palco(url, referer, titulo, cartaz, tipo, id, temporada, episodio, posicao) { finish() }
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) ativarTelaCheiaImersiva()
  }

  /**
   * Inicia direto em tela cheia sem acionar o popup "Viewing full screen / Got it"
   * do Android (que aparece quando BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE oculta
   * todas as barras de uma vez).
   */
  private fun ativarTelaCheiaImersiva() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    controller.hide(WindowInsetsCompat.Type.statusBars())
  }
}

@OptIn(UnstableApi::class)
@Composable
private fun Palco(
  url: String,
  referer: String?,
  titulo: String,
  cartaz: String?,
  tipo: Tipo,
  id: Int,
  temporada: Int,
  episodio: Int,
  posicao: Long,
  aoSair: () -> Unit
) {
  val ctx = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var urlAtual by remember { mutableStateOf(url) }
  var refererAtual by remember { mutableStateOf(referer) }
  var tentativa by remember { mutableStateOf(0) }
  var tentouRenovar by remember { mutableStateOf(false) }
  val refererRef = remember { AtomicReference(referer) }

  var tocando by remember { mutableStateOf(true) }
  var onde by remember { mutableStateOf(posicao) }
  var quanto by remember { mutableStateOf(0L) }
  var guardado by remember { mutableStateOf(0L) }
  var qualidade by remember { mutableStateOf("") }
  var expandirTela by remember { mutableStateOf(false) }
  var avisoPulo by remember { mutableStateOf<String?>(null) }

  var procurandoProximo by remember { mutableStateOf(false) }
  val escopo = rememberCoroutineScope()
  var carregando by remember { mutableStateOf(true) }
  var falha by remember { mutableStateOf<String?>(null) }
  var quieto by remember { mutableStateOf(false) }
  var abertura by remember { mutableStateOf(true) }
  var ultimoToque by remember { mutableStateOf(System.currentTimeMillis()) }

  val player = remember {
    val fabricaHttp = OkHttpDataSource.Factory(Http.cliente) {
      Http.cabecalhosVideo(refererRef.get())
    }
    val renderers = object : DefaultRenderersFactory(ctx) {
      override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
      ): AudioSink? {
        val base = super.buildAudioSink(
          context,
          enableFloatOutput,
          enableAudioTrackPlaybackParams
        ) ?: return null
        return TolerantAudioSink(base)
      }
    }.setEnableDecoderFallback(true)

    val audioAttrs = AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
      .build()

    ExoPlayer.Builder(ctx, renderers)
      .setMediaSourceFactory(DefaultMediaSourceFactory(fabricaHttp))
      .setAudioAttributes(audioAttrs, /* handleAudioFocus = */ true)
      .setHandleAudioBecomingNoisy(true)
      .build()
  }

  fun prepararNoPlayer(endereco: String, ehHls: Boolean, posInicial: Long) {
    val b = MediaItem.Builder().setUri(endereco)
    if (ehHls) b.setMimeType(MimeTypes.APPLICATION_M3U8)
    player.stop()
    player.setMediaItem(b.build())
    if (posInicial > 0) player.seekTo(posInicial)
    player.playWhenReady = true
    player.prepare()
  }

  // Pausa ao ir para segundo plano.
  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, evento ->
      if (evento == Lifecycle.Event.ON_PAUSE) {
        player.pause()
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(obs)
    }
  }

  LaunchedEffect(tentativa) {
    carregando = true
    falha = null
    refererRef.set(refererAtual)

    val ver = Fontes.verificar(urlAtual, refererAtual)
    if (ver is Fontes.ResultadoVerificacao.Valida) {
      urlAtual = ver.urlFinal
      prepararNoPlayer(ver.urlFinal, ver.ehHls, onde)
      return@LaunchedEffect
    }

    val motivo = (ver as Fontes.ResultadoVerificacao.Invalida).motivo
    if (!tentouRenovar && id > 0) {
      tentouRenovar = true
      Guardados.apagarFonte(ctx, tipo, id, temporada, episodio)
      val nova = try {
        Fontes.procurar(tipo, id, temporada, episodio, ignorarUrl = urlAtual)
      } catch (_: Exception) {
        null
      }
      if (nova != null) {
        Guardados.guardarFonte(ctx, nova)
        urlAtual = nova.url
        refererAtual = nova.referer
        refererRef.set(nova.referer)
        val verNova = Fontes.verificar(nova.url, nova.referer)
        if (verNova is Fontes.ResultadoVerificacao.Valida) {
          urlAtual = verNova.urlFinal
          prepararNoPlayer(verNova.urlFinal, verNova.ehHls, onde)
          return@LaunchedEffect
        }
      }
    }

    falha = "Nao deu para tocar este endereco ($motivo). " +
      "O servidor pode ter recusado o pedido ou o link expirou."
    carregando = false
    abertura = false
  }

  DisposableEffect(Unit) {
    val ouvinte = object : Player.Listener {
      override fun onIsPlayingChanged(estaTocando: Boolean) {
        tocando = estaTocando
        if (estaTocando) abertura = false
      }

      override fun onPlaybackStateChanged(estado: Int) {
        carregando = estado == Player.STATE_BUFFERING
        if (estado == Player.STATE_READY) abertura = false
      }

      override fun onVideoSizeChanged(tamanho: VideoSize) {
        qualidade = if (tamanho.height > 0) "${tamanho.height}p" else ""
      }

      override fun onPlayerError(erro: PlaybackException) {
        if (player.currentPosition > 0) {
          onde = player.currentPosition
        }
        if (!tentouRenovar && id > 0) {
          tentouRenovar = true
          carregando = true
          falha = null
          escopo.launch {
            Guardados.apagarFonte(ctx, tipo, id, temporada, episodio)
            val nova = try {
              Fontes.procurar(tipo, id, temporada, episodio, ignorarUrl = urlAtual)
            } catch (_: Exception) {
              null
            }
            if (nova != null) {
              Guardados.guardarFonte(ctx, nova)
              urlAtual = nova.url
              refererAtual = nova.referer
              refererRef.set(nova.referer)
              tentativa++
            } else {
              falha = "Nao deu para tocar este endereco. " +
                "O servidor pode ter recusado o pedido ou o link expirou."
              carregando = false
              abertura = false
            }
          }
          return
        }
        falha = "Nao deu para tocar este endereco. " +
          "O servidor pode ter recusado o pedido ou o link expirou."
        carregando = false
        abertura = false
      }
    }
    player.addListener(ouvinte)
    onDispose {
      val p = player.currentPosition
      val d = player.duration
      if (d > 0) {
        val marca =
          Marca(tipo, id, titulo, cartaz, temporada, episodio, p, d, System.currentTimeMillis())
        Guardados.marcar(ctx, marca)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
          try {
            Progresso.guardar(marca, terminou = marca.fracao >= 0.95f)
          } catch (_: Exception) {
          }
        }
      }
      player.removeListener(ouvinte)
      player.release()
    }
  }

  LaunchedEffect(Unit) {
    while (true) {
      onde = player.currentPosition
      quanto = if (player.duration > 0) player.duration else 0L
      guardado = player.bufferedPosition
      delay(400)
    }
  }

  LaunchedEffect(ultimoToque, tocando) {
    quieto = false
    if (!tocando) return@LaunchedEffect
    delay(4000)
    quieto = true
  }

  LaunchedEffect(Unit) {
    delay(2000)
    abertura = false
  }

  LaunchedEffect(avisoPulo) {
    if (avisoPulo != null) {
      delay(900)
      avisoPulo = null
    }
  }

  fun acordar() {
    ultimoToque = System.currentTimeMillis()
  }

  fun virarPlay() {
    acordar()
    if (player.isPlaying) player.pause() else player.play()
  }

  fun correr(ms: Long) {
    acordar()
    val alvo = (player.currentPosition + ms).coerceIn(0L, if (quanto > 0) quanto else Long.MAX_VALUE)
    player.seekTo(alvo)
    onde = alvo
    avisoPulo = if (ms > 0) "+${ms / 1000}s" else "${ms / 1000}s"
  }

  val tituloExibido = if (tipo.ehSerie) "$titulo · T$temporada E$episodio" else titulo

  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .background(Color.Black)
      .focusable()
      .onKeyEvent { evento ->
        if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (evento.key) {
          Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.MediaPlayPause -> {
            virarPlay(); true
          }
          Key.DirectionLeft, Key.MediaRewind -> {
            correr(-10_000); true
          }
          Key.DirectionRight, Key.MediaFastForward -> {
            correr(10_000); true
          }
          Key.DirectionUp, Key.DirectionDown -> {
            acordar(); true
          }
          else -> false
        }
      }
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
      ) {
        if (quieto) acordar() else quieto = true
      }
  ) {
    val compacto = maxHeight < 460.dp || maxWidth < 740.dp

    // Video com PlayerView para respeitar proporcao e alternar ajuste/tela cheia.
    AndroidView(
      factory = { c ->
        PlayerView(c).apply {
          useController = false
          setKeepContentOnPlayerReset(true)
          setShutterBackgroundColor(android.graphics.Color.BLACK)
          resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
          this.player = player
        }
      },
      update = { view ->
        view.resizeMode = if (expandirTela) {
          AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
          AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
      },
      modifier = Modifier.fillMaxSize()
    )

    // Abertura curta da marca antes do video iniciar.
    AnimatedVisibility(
      visible = abertura,
      enter = fadeIn(),
      exit = fadeOut(),
      label = "abertura"
    ) {
      Box(Modifier.fillMaxSize().background(Cor.bg), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("M", color = Cor.accent, fontSize = if (compacto) 46.sp else 64.sp, fontWeight = FontWeight.Black)
          Text("ovFlix", color = Cor.text, fontSize = if (compacto) 46.sp else 64.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    if (carregando && !abertura && falha == null) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Rodinha(tamanho = 50)
      }
    }

    // Feedback rapido ao avancar/voltar 10s no controle.
    if (avisoPulo != null && !abertura && falha == null) {
      Box(
        Modifier
          .align(Alignment.Center)
          .clip(RoundedCornerShape(10.dp))
          .background(Color(0xAA0B101B))
          .padding(horizontal = 18.dp, vertical = 10.dp)
      ) {
        Text(
          avisoPulo!!,
          color = Color.White,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    if (falha != null) {
      Box(
        Modifier.fillMaxSize().background(Color(0xEE0B1222)),
        contentAlignment = Alignment.Center
      ) {
        Column(
          Modifier.padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            "O filme não abriu",
            color = Cor.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
          )
          Vao(8)
          Text(falha!!, color = Cor.muted, fontSize = 13.sp)
          Vao(18)
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.filmemeu.app.ui.Botao(
              "Tentar novamente",
              jeito = com.filmemeu.app.ui.Jeito.PRIMARIO
            ) {
              tentouRenovar = false
              falha = null
              carregando = true
              tentativa++
            }
            com.filmemeu.app.ui.Botao(
              "Voltar",
              jeito = com.filmemeu.app.ui.Jeito.FANTASMA
            ) {
              aoSair()
            }
          }
        }
      }
    }

    // Botao discreto de voltar no canto superior esquerdo (some junto com os controles).
    AnimatedVisibility(
      visible = !quieto && !abertura && falha == null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier
        .align(Alignment.TopStart)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = if (compacto) 12.dp else 18.dp, vertical = if (compacto) 10.dp else 14.dp),
      label = "voltar_topo"
    ) {
      BotaoVoltarPlayer { aoSair() }
    }

    /* ----- Overlay inferior identico ao player web ----- */
    AnimatedVisibility(
      visible = !quieto && !abertura && falha == null,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier.align(Alignment.BottomCenter),
      label = "controles_web"
    ) {
      Column(
        Modifier
          .fillMaxWidth()
          .background(
            Brush.verticalGradient(
              listOf(
                Color.Transparent,
                Color(0xB8050810),
                Color(0xF2050810)
              )
            )
          )
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(
            start = if (compacto) 14.dp else 20.dp,
            end = if (compacto) 14.dp else 20.dp,
            top = if (compacto) 24.dp else 36.dp,
            bottom = if (compacto) 10.dp else 16.dp
          )
      ) {
        // Linha do titulo logo acima da barra, exatamente como no player web.
        Row(
          Modifier
            .fillMaxWidth()
            .padding(bottom = if (compacto) 8.dp else 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            tituloExibido,
            color = Color.White,
            fontSize = if (compacto) 16.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )

          if (tipo.ehSerie) {
            Spacer(Modifier.width(12.dp))
            BotaoProximoEpisodio(
              carregando = procurandoProximo,
              compacto = compacto
            ) {
              if (!procurandoProximo) {
                acordar()
                procurandoProximo = true
                escopo.launch {
                  val proximo = episodio + 1
                  val achada = try {
                    Fontes.procurar(tipo, id, temporada, proximo)
                  } catch (_: Exception) {
                    null
                  }
                  procurandoProximo = false
                  if (achada == null) {
                    android.widget.Toast.makeText(
                      ctx,
                      "Não achei o episódio $proximo.",
                      android.widget.Toast.LENGTH_SHORT
                    ).show()
                  } else {
                    Guardados.guardarFonte(ctx, achada)
                    PlayerActivity.abrir(
                      ctx = ctx,
                      url = achada.url,
                      referer = achada.referer,
                      titulo = titulo,
                      cartaz = cartaz,
                      tipo = tipo,
                      id = id,
                      temporada = temporada,
                      episodio = proximo,
                      posicao = Guardados.posicaoDe(ctx, tipo, id, temporada, proximo)
                    )
                    aoSair()
                  }
                }
              }
            }
          }
        }

        // Barra inferior minimalista: [▶/⏸]  00:01 / 1:40:15  720p  ●━━━━━━━━━━  [⛶]
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB80B101B))
            .padding(
              horizontal = if (compacto) 6.dp else 8.dp,
              vertical = if (compacto) 5.dp else 7.dp
            ),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(if (compacto) 10.dp else 14.dp)
        ) {
          BotaoQuadradoPlayer(
            icone = if (tocando) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            descricao = if (tocando) "Pausar" else "Reproduzir",
            tag = "player_play_pause_button",
            compacto = compacto
          ) { virarPlay() }

          Text(
            "${tempoWeb(onde)} / ${tempoWeb(quanto)}",
            color = Color(0xFFE2E8F0),
            fontSize = if (compacto) 11.5.sp else 12.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
          )

          if (qualidade.isNotEmpty()) {
            Text(
              qualidade,
              color = Color(0xFF94A3B8),
              fontSize = if (compacto) 10.5.sp else 11.5.sp,
              fontWeight = FontWeight.Medium,
              maxLines = 1
            )
          }

          TrilhoWeb(
            fracao = if (quanto > 0) (onde.toFloat() / quanto).coerceIn(0f, 1f) else 0f,
            guardada = if (quanto > 0) (guardado.toFloat() / quanto).coerceIn(0f, 1f) else 0f,
            modifier = Modifier.weight(1f),
            aoArrastar = { f ->
              acordar()
              if (quanto > 0) {
                val alvo = (quanto * f).toLong()
                onde = alvo
                player.seekTo(alvo)
              }
            },
            aoPularTecla = { deltaMs -> correr(deltaMs) }
          )

          BotaoQuadradoPlayer(
            icone = if (expandirTela) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
            descricao = if (expandirTela) "Ajustar vídeo" else "Preencher tela",
            tag = "player_fullscreen_toggle_button",
            compacto = compacto
          ) {
            acordar()
            expandirTela = !expandirTela
          }
        }
      }
    }
  }
}

@Composable
private fun BotaoVoltarPlayer(aoTocar: () -> Unit) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  val forma = RoundedCornerShape(8.dp)
  Row(
    Modifier
      .testTag("player_back_button")
      .clip(forma)
      .background(if (focado) Color(0xCC1E293B) else Color(0x990D1320))
      .border(1.dp, if (focado) Cor.accent else Color(0x28FFFFFF), forma)
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Icon(
      imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
      contentDescription = "Voltar",
      tint = Color.White,
      modifier = Modifier.size(16.dp)
    )
    Text(
      "Voltar",
      color = Color.White,
      fontSize = 13.sp,
      fontWeight = FontWeight.Medium
    )
  }
}

@Composable
private fun BotaoProximoEpisodio(
  carregando: Boolean,
  compacto: Boolean,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  val forma = RoundedCornerShape(8.dp)
  Box(
    Modifier
      .testTag("player_next_episode_button")
      .clip(forma)
      .background(if (focado) Cor.accentStrong else Color(0xCC141B2B))
      .border(1.dp, if (focado) Color.White else Color(0x33FFFFFF), forma)
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() }
      .padding(horizontal = if (compacto) 10.dp else 14.dp, vertical = if (compacto) 6.dp else 8.dp)
  ) {
    Text(
      if (carregando) "Procurando…" else "Próximo ▸",
      color = if (focado) Cor.sobreAccent else Color.White,
      fontSize = if (compacto) 12.sp else 13.sp,
      fontWeight = FontWeight.SemiBold
    )
  }
}

/** Botao quadrado arredondado do player web (play/pause e tela cheia). */
@Composable
private fun BotaoQuadradoPlayer(
  icone: ImageVector,
  descricao: String,
  tag: String,
  compacto: Boolean,
  aoTocar: () -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  val forma = RoundedCornerShape(8.dp)
  val tam = if (compacto) 36.dp else 40.dp

  Box(
    Modifier
      .testTag(tag)
      .size(tam)
      .clip(forma)
      .background(if (focado) Color(0xFF1F2D45) else Color(0xCC151B28))
      .border(
        width = if (focado) 1.5.dp else 1.dp,
        color = if (focado) Cor.accent else Color(0x26FFFFFF),
        shape = forma
      )
      .focusable(interactionSource = fonte)
      .clickable(interactionSource = fonte, indication = null) { aoTocar() },
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icone,
      contentDescription = descricao,
      tint = Color.White,
      modifier = Modifier.size(if (compacto) 19.dp else 22.dp)
    )
  }
}

/**
 * Trilho horizontal igual ao do player web:
 * linha discreta cinza, camada de buffer, progresso branco e bolinha branca
 * na ponta. Suporta toque direto, arrastar com o dedo e setas do controle.
 */
@Composable
private fun TrilhoWeb(
  fracao: Float,
  guardada: Float,
  modifier: Modifier = Modifier,
  aoArrastar: (Float) -> Unit,
  aoPularTecla: (Long) -> Unit
) {
  val fonte = remember { MutableInteractionSource() }
  val focado by fonte.collectIsFocusedAsState()
  var larguraPx by remember { mutableStateOf(1) }
  val f = fracao.coerceIn(0f, 1f)
  val g = guardada.coerceIn(0f, 1f)

  Box(
    modifier
      .height(30.dp)
      .onSizeChanged { if (it.width > 0) larguraPx = it.width }
      .focusable(interactionSource = fonte)
      .onKeyEvent { evento ->
        if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
        when (evento.key) {
          Key.DirectionLeft -> {
            aoPularTecla(-10_000); true
          }
          Key.DirectionRight -> {
            aoPularTecla(10_000); true
          }
          else -> false
        }
      }
      .pointerInput(Unit) {
        detectTapGestures { offset ->
          val alvo = (offset.x / larguraPx.toFloat()).coerceIn(0f, 1f)
          aoArrastar(alvo)
        }
      }
      .pointerInput(Unit) {
        detectHorizontalDragGestures { change, _ ->
          change.consume()
          val alvo = (change.position.x / larguraPx.toFloat()).coerceIn(0f, 1f)
          aoArrastar(alvo)
        }
      },
    contentAlignment = Alignment.CenterStart
  ) {
    val alturaLinha = if (focado) 6.dp else 4.dp
    // Fundo da barra
    Box(
      Modifier
        .fillMaxWidth()
        .height(alturaLinha)
        .clip(RoundedCornerShape(50))
        .background(Color(0x3DFFFFFF))
    )
    // Buffer
    Box(
      Modifier
        .fillMaxWidth(g)
        .height(alturaLinha)
        .clip(RoundedCornerShape(50))
        .background(Color(0x66FFFFFF))
    )
    // Progresso assistido
    Box(
      Modifier
        .fillMaxWidth(f)
        .height(alturaLinha)
        .clip(RoundedCornerShape(50))
        .background(if (focado) Cor.accent else Color(0xFFE2E8F0))
    )
    // Bolinha branca igual a imagem do player web
    val tamBolinha = if (focado) 14.dp else 11.dp
    Box(
      Modifier
        .offset {
          val x = (larguraPx * f).roundToInt() - (tamBolinha.roundToPx() / 2)
          IntOffset(x.coerceIn(0, (larguraPx - tamBolinha.roundToPx()).coerceAtLeast(0)), 0)
        }
        .size(tamBolinha)
        .clip(CircleShape)
        .background(Color.White)
        .border(
          width = if (focado) 2.dp else 0.dp,
          color = if (focado) Cor.accent else Color.Transparent,
          shape = CircleShape
        )
    )
  }
}

/** Formato igual ao web: 00:01 quando < 1h, e 1:40:15 quando passa de 1 hora. */
private fun tempoWeb(ms: Long): String {
  if (ms <= 0) return "00:00"
  val s = ms / 1000
  val h = s / 3600
  val m = (s % 3600) / 60
  val seg = s % 60
  return if (h > 0) {
    String.format("%d:%02d:%02d", h, m, seg)
  } else {
    String.format("%02d:%02d", m, seg)
  }
}

/**
 * Envolve o AudioSink padrao para ressincronizar automaticamente quando um
 * stream HLS/MPEG-TS apresenta um salto de PTS de audio acima de 200ms entre
 * segmentos sem a tag `#EXT-X-DISCONTINUITY`.
 */
@OptIn(UnstableApi::class)
private class TolerantAudioSink(
  private val delegate: AudioSink
) : AudioSink by delegate {

  override fun handleBuffer(
    buffer: ByteBuffer,
    presentationTimeUs: Long,
    encodedAccessUnitCount: Int
  ): Boolean {
    return try {
      delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    } catch (_: AudioSink.UnexpectedDiscontinuityException) {
      delegate.handleDiscontinuity()
      delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
    }
  }
}
